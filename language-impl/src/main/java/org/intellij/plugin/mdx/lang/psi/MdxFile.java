package org.intellij.plugin.mdx.lang.psi;

import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiFileBase;
import consulo.virtualFileSystem.fileType.FileType;
import org.intellij.plugin.mdx.lang.MdxFileType;
import org.intellij.plugin.mdx.lang.MdxLanguage;

public class MdxFile extends PsiFileBase {
  public MdxFile(FileViewProvider viewProvider) {
    super(viewProvider, MdxLanguage.INSTANCE);
  }

  @Override
  public FileType getFileType() {
    return MdxFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "MDX File";
  }
}
