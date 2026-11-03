package org.intellij.plugin.mdx.lang.parse;

import com.intellij.lang.javascript.JSTokenTypes;
import consulo.javascript.ecmascript.lang.BaseEcmaScriptJavaScriptVersion;
import consulo.language.ast.IElementType;
import consulo.language.lexer.Lexer;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * The JavaScript lexer of the MDX scanners.
 * <p>
 * It is the ECMAScript 6 lexer with JSX. Its state 0 is {@code YYINITIAL} without template or JSX nesting (template
 * depth and tag count are encoded in the state), so it is a safe restart point.
 * <p>
 * The ECMAScript 6 lexer balances a JSX expression container (<code>{...}</code> in tag content or as an attribute
 * value, an {@code XML_JS_SCRIPT} token) by counting raw braces, so a brace inside a string, template, comment or
 * regular expression (<code>{'{'}</code>) breaks the container and swallows the rest of the input. This wrapper ends each
 * container where
 * {@link MdxExpressionBoundaryScanner#findExpressionEnd} does and restarts the lexer after it in the state the container
 * started in (the lexer returns there after a container).
 */
final class MdxJavaScriptLexer {
  private CharSequence text = "";
  private int endOffset;
  private Lexer lexer = createLexer();
  private @Nullable IElementType tokenType;
  private int tokenStart;
  private int tokenEnd;
  private int state;
  private boolean isExpressionContainer;

  private static Lexer createLexer() {
    return BaseEcmaScriptJavaScriptVersion.createEcmaScriptLexer(false);
  }

  void start(CharSequence text, int startOffset, int endOffset) {
    this.text = text;
    this.endOffset = endOffset;
    restart(startOffset, 0);
  }

  @Nullable
  IElementType getTokenType() {
    return tokenType;
  }

  int getTokenStart() {
    return tokenStart;
  }

  int getTokenEnd() {
    return tokenEnd;
  }

  /**
   * The lexer state before the current token.
   */
  int getState() {
    return state;
  }

  void advance() {
    if (tokenType == null) return;
    if (isExpressionContainer) {
      // the platform lexer may have ended the container elsewhere: resume after it in the state it started in
      restart(tokenEnd, state);
      return;
    }
    lexer.advance();
    readToken();
  }

  private void restart(int offset, int initialState) {
    lexer = createLexer();
    lexer.start(text, offset, endOffset, initialState);
    readToken();
  }

  /**
   * {@link MdxExpressionBoundaryScanner#findExpressionEnd} of the container at {@code start}, memoized per text: the
   * scanner lexes the container content with this lexer again, so nested containers would be rescanned once per
   * enclosing container and per prefix advance (exponential in the nesting depth) without it.
   */
  private int containerEnd(int start) {
    ContainerEnds ends = CONTAINER_ENDS.get();
    if (ends.text.get() != text) {
      ends.text = new WeakReference<>(text);
      ends.terminated.clear();
      ends.unterminatedUpTo.clear();
    }
    Integer end = ends.terminated.get(start);
    if (end != null && end <= endOffset) {
      return end;
    }
    Integer upTo = ends.unterminatedUpTo.get(start);
    if (upTo != null && endOffset <= upTo) {
      return -1;
    }
    int found = MdxExpressionBoundaryScanner.findExpressionEnd(text, start, endOffset);
    if (found != -1) {
      ends.terminated.put(start, found);
    }
    else {
      ends.unterminatedUpTo.merge(start, endOffset, Math::max);
    }
    return found;
  }

  private static final ThreadLocal<ContainerEnds> CONTAINER_ENDS = ThreadLocal.withInitial(ContainerEnds::new);

  private static final class ContainerEnds {
    private WeakReference<@Nullable CharSequence> text = new WeakReference<>(null);
    private final Map<Integer, Integer> terminated = new HashMap<>();
    private final Map<Integer, Integer> unterminatedUpTo = new HashMap<>();
  }

  private void readToken() {
    tokenType = lexer.getTokenType();
    tokenStart = lexer.getTokenStart();
    tokenEnd = lexer.getTokenEnd();
    state = lexer.getState();
    isExpressionContainer = tokenType == JSTokenTypes.XML_JS_SCRIPT && tokenStart < endOffset && text.charAt(tokenStart) == '{';
    if (isExpressionContainer) {
      int containerEnd = containerEnd(tokenStart);
      // an unterminated container reaches the end of the input
      tokenEnd = containerEnd == -1 ? endOffset : containerEnd;
    }
  }
}
