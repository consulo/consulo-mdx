package org.intellij.plugin.mdx.highlighting;

import consulo.language.ast.IElementType;
import consulo.language.lexer.LexerBase;
import org.intellij.markdown.lexer.MarkdownLexerAdapter;
import org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner;
import org.intellij.plugin.mdx.lang.parse.MdxTokenTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-lexes inline content like {@link MarkdownLexerAdapter} does, but collapses every balanced {@code {expression}}
 * span into one {@link MdxTokenTypes#EMBEDDED_JS_CONTENT} token, so the editor highlighter's JS/JSX layer covers
 * it instead of flattening it into plain {@code MARKDOWN_TEXT}.
 */
class MdxInlineHighlightingLexer extends LexerBase {
  private static final IElementType JSX_BLOCK_CONTENT = MdxTokenTypes.EMBEDDED_JS_CONTENT;

  /**
   * Upper bound for a single inline {@code {expression}} scan; keeps the re-lexer linear on pathological input.
   */
  private static final int MAX_INLINE_EXPRESSION_SCAN = 2000;

  private final MarkdownLexerAdapter myDelegate = new MarkdownLexerAdapter();

  private CharSequence myBuffer = "";
  private int myBufferEnd;

  private final List<IElementType> myTokenTypes = new ArrayList<>();
  private final List<Integer> myTokenStarts = new ArrayList<>();
  private final List<Integer> myTokenEnds = new ArrayList<>();
  private int myIndex;

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferEnd = endOffset;
    myTokenTypes.clear();
    myTokenStarts.clear();
    myTokenEnds.clear();
    myIndex = 0;

    myDelegate.start(buffer, startOffset, endOffset, initialState);
    // Emitted tokens must tile [startOffset, endOffset) with no gaps/overlaps or LexerEditorHighlighter
    // rejects the lexer; cursor tracks the end of the last emitted token.
    int cursor = startOffset;
    while (myDelegate.getTokenType() != null) {
      IElementType tokenType = myDelegate.getTokenType();
      int tokenStart = myDelegate.getTokenStart();
      int tokenEnd = myDelegate.getTokenEnd();
      if (tokenStart == cursor && buffer.charAt(tokenStart) == '{') {
        int scanLimit = Math.min(endOffset, tokenStart + MAX_INLINE_EXPRESSION_SCAN);
        int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(buffer, tokenStart, scanLimit);
        int collapsedEnd = expressionEnd != -1 ? expressionEnd : findLineEnd(buffer, tokenStart, scanLimit);
        if (collapsedEnd > tokenStart) {
          addToken(JSX_BLOCK_CONTENT, tokenStart, collapsedEnd);
          cursor = collapsedEnd;
          // re-emitted below via the emitStart clip.
          while (myDelegate.getTokenType() != null && myDelegate.getTokenEnd() <= collapsedEnd) {
            myDelegate.advance();
          }
          continue;
        }
      }
      else if (tokenStart == cursor && buffer.charAt(tokenStart) == '}') {
        addToken(JSX_BLOCK_CONTENT, tokenStart, tokenStart + 1);
        cursor = tokenStart + 1;
        continue;
      }
      // Clip a token whose head was consumed by a preceding collapsed expression.
      int emitStart = Math.max(tokenStart, cursor);
      if (emitStart < tokenEnd) {
        addToken(tokenType, emitStart, tokenEnd);
        cursor = tokenEnd;
      }
      myDelegate.advance();
    }
  }

  private static int findLineEnd(CharSequence buffer, int start, int limit) {
    int end = start;
    while (end < limit && buffer.charAt(end) != '\n') {
      end++;
    }
    return end < limit ? end + 1 : end;
  }

  private void addToken(IElementType type, int start, int end) {
    myTokenTypes.add(type);
    myTokenStarts.add(start);
    myTokenEnds.add(end);
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return myIndex < myTokenTypes.size() ? myTokenTypes.get(myIndex) : null;
  }

  @Override
  public int getTokenStart() {
    return myIndex < myTokenStarts.size() ? myTokenStarts.get(myIndex) : myBufferEnd;
  }

  @Override
  public int getTokenEnd() {
    return myIndex < myTokenEnds.size() ? myTokenEnds.get(myIndex) : myBufferEnd;
  }

  @Override
  public void advance() {
    myIndex++;
  }

  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }
}
