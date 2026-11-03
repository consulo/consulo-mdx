package org.intellij.plugin.mdx.highlighting;

import consulo.language.ast.IElementType;
import consulo.language.lexer.LayeredLexer;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;

public class MdxHighlightingLexer extends LayeredLexer {
  public MdxHighlightingLexer() {
    super(new MdxHighlightingLexerBase());
    registerSelfStoppingLayer(new MdxInlineHighlightingLexer(), MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES.getTypes(), IElementType.EMPTY_ARRAY);
  }
}
