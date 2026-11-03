package org.intellij.plugin.mdx.highlighting;

import consulo.language.ast.IElementType;
import consulo.language.lexer.MergeFunction;
import consulo.language.lexer.MergingLexerAdapterBase;
import org.intellij.plugin.mdx.lang.parse.MdxFlavourDescriptor;
import org.intellij.plugin.mdx.lang.parse.MdxTokenTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer;

public class MdxHighlightingLexerBase extends MergingLexerAdapterBase {
  private static final MergeFunction MERGE_FUNCTION = (type, originalLexer) -> {
    if (MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES.contains(type) && originalLexer.getTokenType() == MarkdownTokenTypes.EOL) {
      originalLexer.advance();
      return type;
    }
    if (MdxTokenTypes.EMBEDDED_JS_CONTENT != type) {
      return type;
    }
    CharSequence lastTokenText = null;
    while (originalLexer.getTokenType() != null && originalLexer.getTokenType() == type) {
      CharSequence tokenText = originalLexer.getTokenSequence();
      boolean blank = tokenText.toString().trim().isEmpty();
      if (!blank || lastTokenText == null || !endsWithLineBreak(lastTokenText)) {
        lastTokenText = tokenText;
        originalLexer.advance();
      }
      else {
        return type;
      }
    }
    return type;
  };

  public MdxHighlightingLexerBase() {
    super(new MarkdownToplevelLexer(MdxFlavourDescriptor.INSTANCE));
  }

  @Override
  public MergeFunction getMergeFunction() {
    return MERGE_FUNCTION;
  }

  private static boolean endsWithLineBreak(CharSequence text) {
    return text.length() > 0 && text.charAt(text.length() - 1) == '\n';
  }
}
