package org.intellij.plugin.mdx.lang.parse;

import com.intellij.lang.javascript.JSTokenTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxCancellableText;

/**
 * Locates JavaScript expression boundaries without duplicating JavaScript lexical rules.
 * <p>
 * This is the same token-based boundary-scanning pattern used by Svelte. Strings, template literals,
 * comments, regular expressions, and nested JSX are consumed by the platform JavaScript lexer; this
 * class only counts ordinary JavaScript braces.
 */
public final class MdxExpressionBoundaryScanner {
  /**
   * Caps speculative reparses after the normal JavaScript lexer failed to find a boundary.
   * Valid JavaScript never consumes this budget; the cutoff only makes malformed editor input
   * degrade to an unterminated expression instead of repeatedly reparsing the rest of the file.
   */
  public static final int MALFORMED_RECOVERY_ATTEMPT_LIMIT = 64;

  private MdxExpressionBoundaryScanner() {
  }

  public static final class RecoveryBudget {
    private int remainingAttempts;

    public RecoveryBudget() {
      this(MALFORMED_RECOVERY_ATTEMPT_LIMIT);
    }

    public RecoveryBudget(int remainingAttempts) {
      this.remainingAttempts = remainingAttempts;
    }

    public boolean isExhausted() {
      return remainingAttempts == 0;
    }

    public boolean tryAcquire() {
      if (isExhausted()) return false;
      remainingAttempts--;
      return true;
    }

    public RecoveryBudget copy() {
      return new RecoveryBudget(remainingAttempts);
    }
  }

  /**
   * Incrementally validates host-brace candidates after normal JavaScript lexing failed.
   */
  public static final class RecoveryCandidatesSession {
    private final CharSequence buffer;
    private final int start;
    private final int scanEnd;
    private final RecoveryBudget recoveryBudget;
    private int exposedEnd;
    private int cursor;

    public RecoveryCandidatesSession(CharSequence source, int start) {
      this(source, start, source.length());
    }

    public RecoveryCandidatesSession(CharSequence source, int start, int scanEnd) {
      this(source, start, scanEnd, new RecoveryBudget());
    }

    public RecoveryCandidatesSession(CharSequence source, int start, int scanEnd, RecoveryBudget recoveryBudget) {
      this.buffer = mdxCancellableText(source);
      this.start = start;
      this.scanEnd = scanEnd;
      this.recoveryBudget = recoveryBudget;
      this.exposedEnd = start;
      this.cursor = start + 1;
    }

    public List<Integer> advanceTo(int limit) {
      mdxCancellableText(buffer);
      if (limit < exposedEnd || limit > scanEnd) {
        throw new IllegalArgumentException(
          "Expression recovery limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
      }
      exposedEnd = limit;
      List<Integer> result = new ArrayList<>();
      while (cursor < limit) {
        int candidateEnd = cursor + 1;
        if (buffer.charAt(cursor) == '}' &&
            recoveryBudget.tryAcquire() &&
            findExpressionEnd(buffer, start, candidateEnd) == candidateEnd) {
          result.add(candidateEnd);
        }
        cursor++;
      }
      return result;
    }
  }

  public static final class Session {
    private final CharSequence buffer;
    private final int scanEnd;
    private final @Nullable MdxPrefixJavaScriptLexer lexer;
    private int exposedEnd;
    private int depth = 0;
    private int expressionEnd = -1;

    public Session(CharSequence source, int start) {
      this(source, start, source.length());
    }

    public Session(CharSequence source, int start, int scanEnd) {
      this.buffer = mdxCancellableText(source);
      this.scanEnd = scanEnd;
      this.lexer = charAtOrNull(buffer, start) == '{' ? new MdxPrefixJavaScriptLexer(buffer, start + 1, scanEnd) : null;
      this.exposedEnd = start;
    }

    public int advanceTo(int limit) {
      return advanceToBoundary(limit).end();
    }

    public Boundary advanceToBoundary(int limit) {
      mdxCancellableText(buffer);
      if (limit < exposedEnd || limit > scanEnd) {
        throw new IllegalArgumentException(
          "Expression scan limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
      }
      exposedEnd = limit;
      MdxPrefixJavaScriptLexer lexer = this.lexer;
      if (lexer == null) return Boundary.NOT_FOUND;
      if (expressionEnd != -1) return new Boundary(expressionEnd, true);

      List<MdxJavaScriptToken> provisional = lexer.advanceTo(limit, this::acceptStableToken);
      if (expressionEnd != -1) return new Boundary(expressionEnd, true);
      return new Boundary(scanTokens(provisional, depth).expressionEnd(), false);
    }

    private boolean acceptStableToken(MdxJavaScriptToken token) {
      TokenScanResult result = scanToken(token, depth);
      depth = result.depth();
      expressionEnd = result.expressionEnd();
      return expressionEnd == -1;
    }

    private TokenScanResult scanToken(MdxJavaScriptToken token, int initialDepth) {
      if (token.type() == JSTokenTypes.REGEXP_LITERAL &&
          !MdxJavaScriptLexicalUtils.isMdxRegularExpressionTerminated(buffer, token.start(), token.end())) {
        Integer recoveredEnd = new UnterminatedRegularExpressionRecovery(buffer, token.start(), initialDepth).advanceTo(token.end());
        if (recoveredEnd != null) return new TokenScanResult(initialDepth, recoveredEnd);
      }

      // JSX expression containers are balanced by the JSX lexer (they are XML_JS_SCRIPT tokens)
      if (token.type() == JSTokenTypes.LBRACE) {
        return new TokenScanResult(initialDepth + 1);
      }
      if (token.type() == JSTokenTypes.RBRACE) {
        if (initialDepth == 0) return new TokenScanResult(initialDepth, token.end());
        return new TokenScanResult(initialDepth - 1);
      }
      return new TokenScanResult(initialDepth);
    }

    private TokenScanResult scanTokens(List<MdxJavaScriptToken> tokens, int initialDepth) {
      TokenScanResult result = new TokenScanResult(initialDepth);
      for (MdxJavaScriptToken token : tokens) {
        result = scanToken(token, result.depth());
        if (result.expressionEnd() != -1) return result;
      }
      return result;
    }

    private record TokenScanResult(int depth, int expressionEnd) {
      TokenScanResult(int depth) {
        this(depth, -1);
      }
    }
  }

  public record Boundary(int end, boolean stable) {
    public static final Boundary NOT_FOUND = new Boundary(-1, false);
  }

  /**
   * Returns the end offset after the top-level <code>}</code>, or {@code -1} if the expression reaches {@code end}.
   */
  public static int findExpressionEnd(CharSequence buffer, int start, int end) {
    return new Session(buffer, start, end).advanceTo(end);
  }

  public static List<Integer> findExpressionEndCandidates(CharSequence buffer, int start, int end) {
    return findExpressionEndCandidates(buffer, start, end, new RecoveryBudget());
  }

  /**
   * Returns the normal JavaScript-lexer boundary, or recovery boundaries that become lexically valid
   * when the input ends at that host <code>}</code>. Recovery is intentionally offered only when the lexer found
   * no boundary in the full range: the caller must validate each candidate against its host grammar.
   * This resolves ambiguous malformed input without overriding valid JavaScript tokens such as <code>/}/</code>.
   */
  public static List<Integer> findExpressionEndCandidates(CharSequence buffer, int start, int end, RecoveryBudget recoveryBudget) {
    CharSequence text = mdxCancellableText(buffer);
    int expressionEnd = findExpressionEnd(text, start, end);
    if (expressionEnd != -1) return List.of(expressionEnd);

    return new RecoveryCandidatesSession(text, start, end, recoveryBudget).advanceTo(end);
  }

  /**
   * The JavaScript lexer deliberately accepts a regular-expression token without its final {@code /}.
   * In embedded code that recovery can swallow the host <code>}</code> after malformed JavaScript. Once the
   * platform lexer has identified precisely that failure, prefer an unescaped brace outside a
   * character class as the host boundary. Valid regular expressions remain entirely lexer-owned.
   */
  private static final class UnterminatedRegularExpressionRecovery {
    private final CharSequence buffer;
    private int depth;
    private int offset;
    private boolean escaped = false;
    private boolean inCharacterClass = false;
    private int expressionEnd = -1;

    UnterminatedRegularExpressionRecovery(CharSequence buffer, int start, int depth) {
      this.buffer = buffer;
      this.depth = depth;
      this.offset = start + 1;
    }

    @Nullable
    Integer advanceTo(int limit) {
      if (expressionEnd != -1) return expressionEnd;
      while (offset < limit) {
        char c = buffer.charAt(offset);
        if (escaped) {
          escaped = false;
        }
        else if (c == '\\') {
          escaped = true;
        }
        else if (c == '[') {
          inCharacterClass = true;
        }
        else if (c == ']') {
          inCharacterClass = false;
        }
        else if (!inCharacterClass && c == '{') {
          depth++;
        }
        else if (!inCharacterClass && c == '}') {
          if (depth == 0) {
            expressionEnd = offset + 1;
            return expressionEnd;
          }
          depth--;
        }
        offset++;
      }
      return null;
    }
  }

  /**
   * The char at {@code index}, {@code '\0'} outside the text (the callers only compare
   * with real characters).
   */
  static char charAtOrNull(CharSequence text, int index) {
    return index >= 0 && index < text.length() ? text.charAt(index) : '\0';
  }
}
