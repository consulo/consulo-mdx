package com.intellij.mdx.backend.codeInspection;

import com.intellij.lang.javascript.psi.JSBlockStatement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.InspectionSuppressor;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Suppresses the JSX namespace and "bad expression statement" JavaScript inspections on MDX files, and the statement
 * inspections on the {@code {expression}} of MDX: the JavaScript root makes a top-level block statement of it.
 */
@ExtensionImpl
public class MdxInspectionSuppressor implements InspectionSuppressor {
  private static final Set<String> EXPRESSION_SUPPRESSED = Set.of("BlockStatementJS", "BadExpressionStatementJS");

  @Override
  public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, String toolId) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @Override
  @RequiredReadAction
  public boolean isSuppressedFor(PsiElement element, String name) {
    if (element instanceof MdxFile) {
      return name.equals("JSXNamespaceValidation") || name.equals("BadExpressionStatementJS");
    }
    return EXPRESSION_SUPPRESSED.contains(name) && isInMdxExpression(element);
  }

  @RequiredReadAction
  private static boolean isInMdxExpression(PsiElement element) {
    // the nearest block: a statement block of code inside the expression is still checked
    JSBlockStatement block = PsiTreeUtil.getParentOfType(element, JSBlockStatement.class, false);
    return block != null
      && block.getParent() instanceof PsiFile file
      && file.getViewProvider().getBaseLanguage() == MdxLanguage.INSTANCE;
  }

  @Override
  public Language getLanguage() {
    return MdxLanguage.INSTANCE;
  }
}
