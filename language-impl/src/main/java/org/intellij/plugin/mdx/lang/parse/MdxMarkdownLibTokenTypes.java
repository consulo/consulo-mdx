package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import org.intellij.markdown.MarkdownElementType;

/**
 * The token types of the MDX flavour. The markdown engine shares {@link IElementType} with the platform, so these are the
 * PSI token types too ({@link MdxTokenTypes}).
 */
public final class MdxMarkdownLibTokenTypes {
  public static final IElementType EMBEDDED_JS_CONTENT = new MarkdownElementType("JSX_BLOCK_CONTENT", true);

  private MdxMarkdownLibTokenTypes() {
  }
}
