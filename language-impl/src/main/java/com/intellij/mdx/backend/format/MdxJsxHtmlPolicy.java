package com.intellij.mdx.backend.format;

import com.intellij.lang.javascript.psi.JSReturnStatement;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.FormattingDocumentModel;
import consulo.language.codeStyle.WrapType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.util.lang.StringUtil;
import consulo.xml.language.psi.XmlTag;
import consulo.xml.language.psi.XmlText;
import consulo.xml.psi.formatter.xml.HtmlPolicy;
import org.jspecify.annotations.Nullable;

/**
 * The JSX wrap/indent policy of the MDX host blocks: it decides whether a tag forces its own line, to place the host-side
 * {@code MDX_JSX_CLOSING_ELEMENT} block correctly.
 */
class MdxJsxHtmlPolicy extends HtmlPolicy {
  MdxJsxHtmlPolicy(CodeStyleSettings settings, FormattingDocumentModel model) {
    super(settings, model);
  }

  @Override
  public WrapType getWrappingTypeForTagBegin(XmlTag tag) {
    PsiElement parent = tag.getParent();
    if (isRestrictedProduction(parent)) {
      return WrapType.NONE;
    }
    if (!(parent instanceof XmlTag)) {
      return WrapType.NORMAL;
    }
    return newlineProhibitedBefore(tag) ? WrapType.NONE : super.getWrappingTypeForTagBegin(tag);
  }

  @Override
  public boolean indentChildrenOf(XmlTag parentTag) {
    return parentTag.getName().isEmpty() || super.indentChildrenOf(parentTag);
  }

  @Override
  public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
    return false;
  }

  @Override
  protected boolean isInlineTag(XmlTag tag) {
    return StringUtil.isCapitalized(tag.getName()) || super.isInlineTag(tag);
  }

  private static boolean newlineProhibitedBefore(PsiElement tag) {
    PsiElement prevSibling = tag.getPrevSibling();
    return prevSibling instanceof PsiWhiteSpace && prevSibling.getPrevSibling() instanceof XmlText;
  }

  /**
   * A tag right after {@code return}: a line break there would end the statement.
   */
  private static boolean isRestrictedProduction(@Nullable PsiElement parent) {
    return parent instanceof JSReturnStatement;
  }
}
