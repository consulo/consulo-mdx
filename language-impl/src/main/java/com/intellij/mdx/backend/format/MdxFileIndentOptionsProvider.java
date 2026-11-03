package com.intellij.mdx.backend.format;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.FileIndentOptionsProvider;
import consulo.language.psi.PsiFile;
import consulo.language.template.TemplateLanguageFileViewProvider;
import org.intellij.plugin.mdx.lang.MdxFileType;
import org.jspecify.annotations.Nullable;

/**
 * MDX files are indented like their template data language (JavaScript).
 */
@ExtensionImpl
public class MdxFileIndentOptionsProvider extends FileIndentOptionsProvider {
  @Override
  public CommonCodeStyleSettings.@Nullable IndentOptions getIndentOptions(CodeStyleSettings settings, PsiFile file) {
    if (file.getFileType() instanceof MdxFileType && file.getViewProvider() instanceof TemplateLanguageFileViewProvider viewProvider) {
      return settings.getCommonSettings(viewProvider.getTemplateDataLanguage()).getIndentOptions();
    }
    return null;
  }
}
