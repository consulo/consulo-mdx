package org.intellij.plugin.mdx.lang.template;

import consulo.language.ast.IElementType;
import consulo.language.impl.psi.template.OuterLanguageElementType;
import org.intellij.plugin.mdx.lang.MdxLanguage;

public final class MdxTemplateElementTypes {
  public static final IElementType OUTER_MARKDOWN_CONTENT = new OuterLanguageElementType("OUTER_MARKDOWN_CONTENT", MdxLanguage.INSTANCE);

  private MdxTemplateElementTypes() {
  }
}
