package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import org.intellij.markdown.MarkdownElementType;

/**
 * The element types of the MDX flavour. The markdown engine shares {@link IElementType} with the platform, so these are
 * the PSI element types too ({@link MdxElementTypes}).
 */
public final class MdxMarkdownLibElementTypes {
  public static final IElementType MDX_BLOCK = new MarkdownElementType("JSX_BLOCK");
  public static final IElementType MDX_ESM_BLOCK = new MarkdownElementType("MDX_ESM_BLOCK");
  public static final IElementType MDX_JSX_FLOW_ELEMENT = new MarkdownElementType("MDX_JSX_FLOW_ELEMENT");
  public static final IElementType MDX_JSX_TEXT_ELEMENT = new MarkdownElementType("MDX_JSX_TEXT_ELEMENT");
  public static final IElementType MDX_JSX_OPENING_ELEMENT = new MarkdownElementType("MDX_JSX_OPENING_ELEMENT");
  public static final IElementType MDX_JSX_CLOSING_ELEMENT = new MarkdownElementType("MDX_JSX_CLOSING_ELEMENT");
  public static final IElementType MDX_JSX_SELF_CLOSING_ELEMENT = new MarkdownElementType("MDX_JSX_SELF_CLOSING_ELEMENT");
  public static final IElementType MDX_JSX_ATTRIBUTE = new MarkdownElementType("MDX_JSX_ATTRIBUTE");
  public static final IElementType MDX_EXPRESSION = new MarkdownElementType("MDX_JSX_EXPRESSION");

  private MdxMarkdownLibElementTypes() {
  }
}
