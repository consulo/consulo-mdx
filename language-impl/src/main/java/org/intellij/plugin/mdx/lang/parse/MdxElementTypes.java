package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import consulo.language.psi.stub.IStubFileElementType;
import org.intellij.plugin.mdx.lang.MdxLanguage;

/**
 * The PSI element types of MDX files: the markdown engine types themselves.
 */
public final class MdxElementTypes {
  /**
   * A stub file type: the stub tree of an MDX file carries the stubs of its embedded JavaScript root.
   */
  public static final IStubFileElementType MDX_FILE_NODE_TYPE = new IStubFileElementType("MDX", MdxLanguage.INSTANCE);
  public static final IElementType MDX_BLOCK = MdxMarkdownLibElementTypes.MDX_BLOCK;
  public static final IElementType MDX_ESM_BLOCK = MdxMarkdownLibElementTypes.MDX_ESM_BLOCK;
  public static final IElementType MDX_JSX_FLOW_ELEMENT = MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT;
  public static final IElementType MDX_JSX_TEXT_ELEMENT = MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT;
  public static final IElementType MDX_JSX_OPENING_ELEMENT = MdxMarkdownLibElementTypes.MDX_JSX_OPENING_ELEMENT;
  public static final IElementType MDX_JSX_CLOSING_ELEMENT = MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT;
  public static final IElementType MDX_JSX_SELF_CLOSING_ELEMENT = MdxMarkdownLibElementTypes.MDX_JSX_SELF_CLOSING_ELEMENT;
  public static final IElementType MDX_JSX_ATTRIBUTE = MdxMarkdownLibElementTypes.MDX_JSX_ATTRIBUTE;
  public static final IElementType MDX_EXPRESSION = MdxMarkdownLibElementTypes.MDX_EXPRESSION;

  private MdxElementTypes() {
  }
}
