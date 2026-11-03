package org.intellij.plugin.mdx.lang.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.file.FileViewProvider;
import consulo.language.file.LanguageFileViewProviderFactory;
import consulo.language.psi.PsiManager;
import consulo.virtualFileSystem.VirtualFile;
import org.intellij.plugin.mdx.lang.MdxLanguage;

@ExtensionImpl
public class MdxFileViewProviderFactory implements LanguageFileViewProviderFactory {
  @Override
  public FileViewProvider createFileViewProvider(VirtualFile file, Language language, PsiManager manager, boolean eventSystemEnabled) {
    return new MdxFileViewProvider(manager, file, eventSystemEnabled);
  }

  @Override
  public Language getLanguage() {
    return MdxLanguage.INSTANCE;
  }
}
