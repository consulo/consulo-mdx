package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Incrementally lexes only the source prefix exposed by the caller.
 * <p>
 * Tokens are committed through {@code onStableToken} only after the JavaScript lexer reaches its base
 * state again. The remaining suffix is re-lexed on the next advance because an unterminated string,
 * template, comment, regular expression, or JSX token can change earlier token boundaries.
 */
final class MdxPrefixJavaScriptLexer {
  private final CharSequence text;
  private final int scanEnd;
  private int exposedEnd;
  private int checkpointOffset;

  MdxPrefixJavaScriptLexer(CharSequence text, int start, int scanEnd) {
    this.text = text;
    this.scanEnd = scanEnd;
    this.exposedEnd = start;
    this.checkpointOffset = start;
  }

  List<MdxJavaScriptToken> advanceTo(int limit, Predicate<MdxJavaScriptToken> onStableToken) {
    if (limit < exposedEnd || limit > scanEnd) {
      throw new IllegalArgumentException(
        "JavaScript lexer limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
    }
    exposedEnd = limit;
    if (checkpointOffset >= limit) return List.of();

    MdxJavaScriptLexer lexer = new MdxJavaScriptLexer();
    lexer.start(text, checkpointOffset, limit);
    List<MdxJavaScriptToken> provisional = new ArrayList<>();
    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      int tokenState = lexer.getState();
      MdxJavaScriptToken token = new MdxJavaScriptToken(tokenType, lexer.getTokenStart(), lexer.getTokenEnd());
      if (token.end() > limit) {
        throw new IllegalStateException("JavaScript lexer crossed the exposed prefix: " + token.end() + " > " + limit);
      }
      // the whole scan range is exposed: no later advance can change a token, each one is stable at once
      // (lets the consumer stop early; the base state may be rare in JSX-heavy text)
      if (limit == scanEnd) {
        if (!onStableToken.test(token)) return List.of();
        checkpointOffset = token.end();
        lexer.advance();
        continue;
      }
      if (token.start() > checkpointOffset && tokenState == 0) {
        for (MdxJavaScriptToken stableToken : provisional) {
          if (!onStableToken.test(stableToken)) return List.of();
        }
        checkpointOffset = token.start();
        provisional.clear();
      }
      provisional.add(token);
      lexer.advance();
    }
    return provisional;
  }
}
