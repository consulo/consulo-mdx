package org.intellij.plugin.mdx.markdown;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.lang.MarkdownCompatibilityChecker;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class MdxMarkdownCompatibilityChecker implements MarkdownCompatibilityChecker {
  @Override
  public boolean isSupportedLanguage(Language language) {
    return language == MdxLanguage.INSTANCE;
  }

  @Override
  public boolean isSupportedContext(Language language, @Nullable DataContext dataContext) {
    if (!isSupportedLanguage(language)) {
      return false;
    }
    return MdxMarkdown.isMarkdownContext(dataContext);
  }

  @Override
  public boolean isSupportedElement(PsiElement element) {
    return MdxMarkdown.isMarkdownElement(element);
  }

  @Override
  public boolean isSupportedRange(PsiFile file, TextRange range) {
    if (!(file instanceof MdxFile)) {
      return false;
    }
    return MdxMarkdown.isMarkdownRange(file, range.getStartOffset(), range.getEndOffset());
  }
}
