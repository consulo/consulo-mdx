package com.intellij.mdx.backend.highlighting;

import consulo.annotation.component.ExtensionImpl;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.editor.DefaultHighlightingSettingProvider;
import consulo.language.editor.FileHighlightingSetting;
import consulo.language.file.LanguageFileType;
import consulo.language.file.inject.VirtualFileWindow;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.intellij.plugin.mdx.lang.MdxFileType;
import org.jspecify.annotations.Nullable;

/**
 * JavaScript (or a dialect of it, TypeScript included) injected into an MDX code fence is a documentation snippet, not a
 * real module, so incomplete code is not inspected.
 */
@ExtensionImpl
public class MdxInjectedHighlightingSettingProvider extends DefaultHighlightingSettingProvider {
  @Override
  public @Nullable FileHighlightingSetting getDefaultSetting(Project project, VirtualFile file) {
    if (!(file instanceof VirtualFileWindow window)) {
      return null;
    }
    FileType fileType = file.getFileType();
    if (!(fileType instanceof LanguageFileType languageFileType) || !languageFileType.getLanguage().isKindOf(JavaScriptLanguage.INSTANCE)) {
      return null;
    }
    FileType hostFileType = FileTypeRegistry.getInstance().getFileTypeByFileName(window.getDelegate().getName());
    return hostFileType == MdxFileType.INSTANCE ? FileHighlightingSetting.SKIP_INSPECTION : null;
  }
}
