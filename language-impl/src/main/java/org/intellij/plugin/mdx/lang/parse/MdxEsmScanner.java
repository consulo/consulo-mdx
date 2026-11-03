package org.intellij.plugin.mdx.lang.parse;

import com.intellij.lang.javascript.JSTokenTypes;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxCancellableText;
import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxLineStart;
import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxSmallIndent;
import static org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner.charAtOrNull;

public final class MdxEsmScanner {
  private static final Set<Character> LINE_END_CONTINUATION_CHARS = Set.of(
    '=', '+', '-', '*', '/', '%', '&', '|', '^', '!', '~', '?', '.', ',', ':', '(', '[', '{', '<'
  );
  private static final Set<Character> NEXT_LINE_CONTINUATION_CHARS = Set.of(
    '.', '?', ':', ',', '+', '-', '*', '/', '%', '&', '|', ')', ']', '}', '('
  );

  private MdxEsmScanner() {
  }

  public record Block(TextRange range, boolean terminated, boolean recoveryBoundary) {
    public Block(TextRange range, boolean terminated) {
      this(range, terminated, false);
    }
  }

  public static final class Session {
    private final CharSequence text;
    private final int start;
    private final int scanEnd;
    private final MdxPrefixJavaScriptLexer lexer;
    private NestingState nesting = new NestingState();
    private ModuleStatementState statement = new ModuleStatementState();
    private LexicalState lexicalState = new LexicalState();
    private int exposedEnd;
    private int lastSignificantEnd = -1;
    private int pendingSemicolonEnd = -1;
    private @Nullable Block terminalBlock;

    public Session(CharSequence text, int start) {
      this(text, start, text.length());
    }

    public Session(CharSequence text, int start, int scanEnd) {
      this.text = mdxCancellableText(text);
      this.start = start;
      this.scanEnd = scanEnd;
      this.lexer = new MdxPrefixJavaScriptLexer(this.text, start, scanEnd);
      this.exposedEnd = start;
    }

    public @Nullable Block advanceTo(int limit) {
      mdxCancellableText(text);
      if (limit < exposedEnd || limit > scanEnd) {
        throw new IllegalArgumentException("ESM scan limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
      }
      exposedEnd = limit;
      if (esmStartStatus(text, start, limit) != EsmStartStatus.VALID) return null;
      Block terminal = terminalBlock;
      if (terminal != null) return terminal;

      List<MdxJavaScriptToken> provisional = lexer.advanceTo(limit, token -> {
        processToken(token, limit);
        return terminalBlock == null;
      });
      terminal = terminalBlock;
      if (terminal != null) return terminal;

      State committedState = snapshot();
      try {
        for (MdxJavaScriptToken token : provisional) {
          processToken(token, limit);
          if (terminalBlock != null) break;
        }
        return currentBlock(limit);
      }
      finally {
        restore(committedState);
      }
    }

    private void processWhitespace(int startOffset, int endOffset, int visibleEnd) {
      int offset = startOffset;
      while (offset < endOffset) {
        int lineBreak = offset;
        boolean isLineBreak = text.charAt(offset) == '\n';
        offset++;
        if (!isLineBreak) continue;
        if (pendingSemicolonEnd != -1) {
          terminalBlock = new Block(new TextRange(start, pendingSemicolonEnd), true);
          return;
        }
        if (nesting.atTopLevel()) {
          if (hasBlankLineAfter(text, lineBreak + 1, visibleEnd)) {
            terminalBlock = new Block(new TextRange(start, lastSignificantEnd), false, true);
            return;
          }
          if (lastSignificantEnd != -1 &&
              lexicalState.isTerminated() &&
              statement.isTerminated() &&
              isCompleteBeforeLineBreak(text, lastSignificantEnd - 1) &&
              !nextLineContinuesEsm(text, lineBreak + 1, visibleEnd, lastSignificantEnd - 1)) {
            terminalBlock = new Block(new TextRange(start, lastSignificantEnd), true);
            return;
          }
        }
      }
    }

    private void processToken(MdxJavaScriptToken token, int visibleEnd) {
      IElementType type = esmTokenType(text, token);
      if (type == JSTokenTypes.WHITE_SPACE) {
        processWhitespace(token.start(), token.end(), visibleEnd);
        return;
      }
      if (!isTrivia(type) && pendingSemicolonEnd != -1) {
        if (hasLineBreakBefore(text, pendingSemicolonEnd, token.start()) || !isEsmKeyword(type)) {
          terminalBlock = new Block(new TextRange(start, pendingSemicolonEnd), true);
          return;
        }
        statement = new ModuleStatementState();
        lexicalState = new LexicalState();
        lastSignificantEnd = -1;
        pendingSemicolonEnd = -1;
      }

      boolean atTopLevel = nesting.atTopLevel();
      lexicalState.accept(text, type, token.start(), token.end());
      if (isTrivia(type)) return;

      statement.accept(type, atTopLevel);
      nesting.accept(type);
      lastSignificantEnd = token.end();
      if (type == JSTokenTypes.SEMICOLON && atTopLevel) {
        pendingSemicolonEnd = token.end();
      }
    }

    private Block currentBlock(int limit) {
      Block terminal = terminalBlock;
      if (terminal != null) return terminal;
      if (pendingSemicolonEnd != -1) {
        return new Block(new TextRange(start, pendingSemicolonEnd), true);
      }
      return new Block(new TextRange(start, limit), nesting.atTopLevel() && lexicalState.isTerminated() && statement.isTerminated());
    }

    private State snapshot() {
      return new State(nesting.copy(), statement.copy(), lexicalState.copy(), lastSignificantEnd, pendingSemicolonEnd, terminalBlock);
    }

    private void restore(State state) {
      nesting = state.nesting();
      statement = state.statement();
      lexicalState = state.lexicalState();
      lastSignificantEnd = state.lastSignificantEnd();
      pendingSemicolonEnd = state.pendingSemicolonEnd();
      terminalBlock = state.terminalBlock();
    }

    private record State(
      NestingState nesting,
      ModuleStatementState statement,
      LexicalState lexicalState,
      int lastSignificantEnd,
      int pendingSemicolonEnd,
      @Nullable Block terminalBlock
    ) {
    }
  }

  public static boolean isLineStart(CharSequence text, int start) {
    CharSequence source = mdxCancellableText(text);
    int lineStart = mdxLineStart(source, start);
    int indent = mdxSmallIndent(source, lineStart, start);
    return indent != -1 && lineStart + indent == start && isEsmKeywordAt(source, start);
  }

  public static @Nullable Block scanBlock(CharSequence text, int start) {
    return scanBlock(text, start, text.length());
  }

  public static @Nullable Block scanBlock(CharSequence text, int start, int limit) {
    return new Session(text, start, limit).advanceTo(limit);
  }

  /**
   * Returns adjacent top-level ESM keyword offsets that have no whitespace or semicolon before them.
   */
  public static List<Integer> findMissingStatementSeparators(CharSequence text, int start, int end) {
    CharSequence source = mdxCancellableText(text);
    NestingState nesting = new NestingState();
    List<Integer> result = new ArrayList<>();
    for (MdxJavaScriptToken token : tokenize(source, start, end)) {
      IElementType tokenType = esmTokenType(source, token);
      int tokenStart = token.start();
      if (tokenStart > start &&
          nesting.atTopLevel() &&
          isEsmKeyword(tokenType) &&
          !hasStatementSeparatorBefore(source, tokenStart)) {
        result.add(tokenStart);
      }
      nesting.accept(tokenType);
    }
    return result;
  }

  private static List<MdxJavaScriptToken> tokenize(CharSequence text, int start, int limit) {
    MdxJavaScriptLexer lexer = new MdxJavaScriptLexer();
    lexer.start(text, start, limit);
    List<MdxJavaScriptToken> result = new ArrayList<>();
    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      result.add(new MdxJavaScriptToken(tokenType, lexer.getTokenStart(), lexer.getTokenEnd()));
      lexer.advance();
    }
    return result;
  }

  /**
   * The ECMAScript 6 lexer lexes the contextual {@code from} as an {@code IDENTIFIER}, and a {@code *} after a keyword or
   * a <code>{</code> as {@code ANY_IDENTIFIER}: map them to {@code FROM_KEYWORD} and {@code MULT}, the token types the
   * statement state expects.
   */
  private static IElementType esmTokenType(CharSequence text, MdxJavaScriptToken token) {
    IElementType type = token.type();
    if (type == JSTokenTypes.IDENTIFIER && isTokenText(text, token, "from")) {
      return JSTokenTypes.FROM_KEYWORD;
    }
    if (type == JSTokenTypes.ANY_IDENTIFIER) {
      return JSTokenTypes.MULT;
    }
    return type;
  }

  private static boolean isTokenText(CharSequence text, MdxJavaScriptToken token, String expected) {
    if (token.end() - token.start() != expected.length()) return false;
    for (int index = 0; index < expected.length(); index++) {
      if (text.charAt(token.start() + index) != expected.charAt(index)) return false;
    }
    return true;
  }

  private static boolean isCompleteBeforeLineBreak(CharSequence text, int lastSignificantOffset) {
    if (lastSignificantOffset == -1) return false;
    char c = text.charAt(lastSignificantOffset);
    return !LINE_END_CONTINUATION_CHARS.contains(c) &&
           (c != '>' || charAtOrNull(text, lastSignificantOffset - 1) != '=');
  }

  private static boolean nextLineContinuesEsm(CharSequence text, int start, int limit, int previousSignificantOffset) {
    int next = firstNonWhitespaceOffset(text, start, limit);
    if (next == -1) return false;
    if (hasLineBreakBefore(text, start, next)) return false;
    int lineStart = mdxLineStart(text, next);
    if (mdxSmallIndent(text, lineStart, next) == -1) return true;
    if (keywordAt(text, next, "from")) return true;
    if (keywordEndsAt(text, previousSignificantOffset, "from")) {
      return text.charAt(next) == '\'' || text.charAt(next) == '"' || text.charAt(next) == '`';
    }
    if (keywordEndsAt(text, previousSignificantOffset, "import")) {
      return isImportContinuationStart(text.charAt(next));
    }
    if (keywordEndsAt(text, previousSignificantOffset, "export")) {
      return isExportContinuationStart(text.charAt(next));
    }
    return NEXT_LINE_CONTINUATION_CHARS.contains(text.charAt(next));
  }

  private static boolean keywordEndsAt(CharSequence text, int endOffset, String keyword) {
    int start = endOffset - keyword.length() + 1;
    return start >= 0 && keywordAt(text, start, keyword);
  }

  private static boolean isImportContinuationStart(char c) {
    return c == '{' || c == '*' || c == '\'' || c == '"' || c == '`' || isNameStart(c);
  }

  private static boolean isExportContinuationStart(char c) {
    return c == '{' || c == '*' || isNameStart(c);
  }

  private static boolean hasLineBreakBefore(CharSequence text, int start, int end) {
    int offset = start;
    while (offset < end) {
      boolean isLineBreak = text.charAt(offset) == '\n';
      offset++;
      if (isLineBreak) return true;
    }
    return false;
  }

  private static boolean hasBlankLineAfter(CharSequence text, int start, int limit) {
    int offset = start;
    while (offset < limit && text.charAt(offset) != '\n') {
      if (!isWhitespace(text.charAt(offset))) return false;
      offset++;
    }
    return start < limit;
  }

  private static int firstNonWhitespaceOffset(CharSequence text, int start, int limit) {
    int offset = start;
    while (offset < limit) {
      boolean whitespace = isWhitespace(text.charAt(offset));
      offset++;
      if (!whitespace) return offset - 1;
    }
    return -1;
  }

  private static boolean isEsmKeywordAt(CharSequence text, int start) {
    return keywordAt(text, start, "import") || keywordAt(text, start, "export");
  }

  private static EsmStartStatus esmStartStatus(CharSequence text, int start, int limit) {
    if (start < 0 || start >= limit) return EsmStartStatus.PENDING;
    String keyword;
    char first = text.charAt(start);
    if (first == 'i') {
      keyword = "import";
    }
    else if (first == 'e') {
      keyword = "export";
    }
    else {
      return EsmStartStatus.INVALID;
    }
    for (int index = 0; index < keyword.length(); index++) {
      if (start + index >= limit) return EsmStartStatus.PENDING;
      if (text.charAt(start + index) != keyword.charAt(index)) return EsmStartStatus.INVALID;
    }
    if (start - 1 >= 0 && isNamePart(text.charAt(start - 1))) return EsmStartStatus.INVALID;
    int after = start + keyword.length();
    return after < limit && isNamePart(text.charAt(after)) ? EsmStartStatus.INVALID : EsmStartStatus.VALID;
  }

  private static boolean keywordAt(CharSequence text, int start, String keyword) {
    if (start + keyword.length() > text.length()) return false;
    for (int index = 0; index < keyword.length(); index++) {
      if (text.charAt(start + index) != keyword.charAt(index)) return false;
    }
    int before = start - 1;
    int after = start + keyword.length();
    return (before < 0 || !isNamePart(text.charAt(before))) &&
           (after >= text.length() || !isNamePart(text.charAt(after)));
  }

  private static boolean isNameStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  private static boolean isNamePart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
  }

  /**
   * Whitespace as {@link Character#isWhitespace(char)} or {@link Character#isSpaceChar(char)} define it.
   */
  static boolean isWhitespace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  private static boolean isTrivia(IElementType type) {
    return type == JSTokenTypes.WHITE_SPACE ||
           type == JSTokenTypes.END_OF_LINE_COMMENT ||
           type == JSTokenTypes.C_STYLE_COMMENT ||
           type == JSTokenTypes.XML_STYLE_COMMENT;
  }

  private static boolean isEsmKeyword(IElementType type) {
    return type == JSTokenTypes.IMPORT_KEYWORD || type == JSTokenTypes.EXPORT_KEYWORD;
  }

  /**
   * The string literal tokens of the ECMAScript 6 lexer.
   */
  private static boolean isStringLiteral(IElementType type) {
    return type == JSTokenTypes.STRING_LITERAL || type == JSTokenTypes.SINGLE_QUOTE_STRING_LITERAL;
  }

  private static boolean hasStatementSeparatorBefore(CharSequence text, int offset) {
    if (offset - 1 < 0 || offset - 1 >= text.length()) return true;
    char previous = text.charAt(offset - 1);
    return isWhitespace(previous) || previous == ';';
  }

  private enum EsmStartStatus {
    PENDING,
    VALID,
    INVALID,
  }

  private static final class ModuleStatementState {
    private @Nullable IElementType first;
    private @Nullable IElementType second;
    private int significantCount = 0;
    private boolean sawFrom = false;
    private boolean sawModuleSpecifierAfterFrom = false;
    private boolean requiresTopLevelBody = false;
    private boolean sawTopLevelBody = false;

    void accept(IElementType tokenType, boolean atTopLevel) {
      significantCount++;
      if (first == null) {
        first = tokenType;
      }
      else if (second == null) {
        second = tokenType;
      }
      if (atTopLevel && (tokenType == JSTokenTypes.FUNCTION_KEYWORD || tokenType == JSTokenTypes.CLASS_KEYWORD)) {
        requiresTopLevelBody = true;
      }
      else if (atTopLevel && requiresTopLevelBody && tokenType == JSTokenTypes.LBRACE) {
        sawTopLevelBody = true;
      }
      if (tokenType == JSTokenTypes.FROM_KEYWORD) {
        sawFrom = true;
      }
      else if (sawFrom && isStringLiteral(tokenType)) {
        sawModuleSpecifierAfterFrom = true;
      }
    }

    boolean isTerminated() {
      if (requiresTopLevelBody && !sawTopLevelBody) return false;
      IElementType second = this.second;
      if (first == JSTokenTypes.IMPORT_KEYWORD) {
        if (second == null) return false;
        if (second == JSTokenTypes.STRING_LITERAL ||
            second == JSTokenTypes.SINGLE_QUOTE_STRING_LITERAL ||
            second == JSTokenTypes.LPAR ||
            second == JSTokenTypes.DOT) {
          return true;
        }
        return sawModuleSpecifierAfterFrom;
      }
      if (first == JSTokenTypes.EXPORT_KEYWORD) {
        if (second == null) return false;
        if (sawFrom) return sawModuleSpecifierAfterFrom;
        if (second == JSTokenTypes.MULT) return false;
        if (second == JSTokenTypes.DEFAULT_KEYWORD && significantCount == 2) return false;
        return true;
      }
      return true;
    }

    ModuleStatementState copy() {
      ModuleStatementState it = new ModuleStatementState();
      it.first = first;
      it.second = second;
      it.significantCount = significantCount;
      it.sawFrom = sawFrom;
      it.sawModuleSpecifierAfterFrom = sawModuleSpecifierAfterFrom;
      it.requiresTopLevelBody = requiresTopLevelBody;
      it.sawTopLevelBody = sawTopLevelBody;
      return it;
    }
  }

  private static final class LexicalState {
    private int templateDelimiters = 0;
    private boolean hasUnterminatedToken = false;

    void accept(CharSequence text, IElementType type, int start, int end) {
      if (type == JSTokenTypes.BACKQUOTE) {
        templateDelimiters++;
      }
      else if (type == JSTokenTypes.STRING_LITERAL || type == JSTokenTypes.SINGLE_QUOTE_STRING_LITERAL) {
        if (!isQuotedLiteralTerminated(text, start, end)) hasUnterminatedToken = true;
      }
      else if (type == JSTokenTypes.C_STYLE_COMMENT) {
        if (end - start < 4 || text.charAt(end - 2) != '*' || text.charAt(end - 1) != '/') {
          hasUnterminatedToken = true;
        }
      }
      else if (type == JSTokenTypes.REGEXP_LITERAL) {
        if (!MdxJavaScriptLexicalUtils.isMdxRegularExpressionTerminated(text, start, end)) hasUnterminatedToken = true;
      }
    }

    boolean isTerminated() {
      return !hasUnterminatedToken && templateDelimiters % 2 == 0;
    }

    LexicalState copy() {
      LexicalState it = new LexicalState();
      it.templateDelimiters = templateDelimiters;
      it.hasUnterminatedToken = hasUnterminatedToken;
      return it;
    }
  }

  private static final class NestingState {
    private int parenDepth = 0;
    private int braceDepth = 0;
    private int bracketDepth = 0;
    private JsxState jsxState = new JsxState();

    void accept(IElementType tokenType) {
      jsxState.accept(tokenType);
      if (tokenType == JSTokenTypes.LPAR) {
        parenDepth++;
      }
      else if (tokenType == JSTokenTypes.RPAR) {
        if (parenDepth > 0) parenDepth--;
      }
      else if (tokenType == JSTokenTypes.LBRACE) {
        braceDepth++;
      }
      else if (tokenType == JSTokenTypes.RBRACE) {
        if (braceDepth > 0) braceDepth--;
      }
      else if (tokenType == JSTokenTypes.LBRACKET) {
        bracketDepth++;
      }
      else if (tokenType == JSTokenTypes.RBRACKET) {
        if (bracketDepth > 0) bracketDepth--;
      }
    }

    boolean atTopLevel() {
      return parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && jsxState.isBalanced();
    }

    NestingState copy() {
      NestingState it = new NestingState();
      it.parenDepth = parenDepth;
      it.braceDepth = braceDepth;
      it.bracketDepth = bracketDepth;
      it.jsxState = jsxState.copy();
      return it;
    }
  }

  private static final class JsxState {
    private int depth = 0;
    private boolean inClosingTag = false;

    void accept(IElementType tokenType) {
      if (tokenType == JSTokenTypes.XML_START_TAG_START) {
        depth++;
      }
      else if (tokenType == JSTokenTypes.XML_END_TAG_START) {
        inClosingTag = true;
      }
      else if (tokenType == JSTokenTypes.XML_EMPTY_TAG_END) {
        if (depth > 0) depth--;
        inClosingTag = false;
      }
      else if (tokenType == JSTokenTypes.XML_TAG_END) {
        if (inClosingTag && depth > 0) depth--;
        inClosingTag = false;
      }
      // the fragment tags <> and </> are single tokens
      else if (tokenType == JSTokenTypes.XML_START_TAG_LIST) {
        depth++;
      }
      else if (tokenType == JSTokenTypes.XML_END_TAG_LIST) {
        if (depth > 0) depth--;
        inClosingTag = false;
      }
    }

    boolean isBalanced() {
      return depth == 0 && !inClosingTag;
    }

    JsxState copy() {
      JsxState it = new JsxState();
      it.depth = depth;
      it.inClosingTag = inClosingTag;
      return it;
    }
  }

  private static boolean isQuotedLiteralTerminated(CharSequence text, int start, int end) {
    if (end - start < 2) return false;
    char quote = text.charAt(start);
    if ((quote != '\'' && quote != '"') || text.charAt(end - 1) != quote) return false;

    int precedingBackslashes = 0;
    int offset = end - 2;
    while (offset > start && text.charAt(offset) == '\\') {
      precedingBackslashes++;
      offset--;
    }
    return precedingBackslashes % 2 == 0;
  }
}
