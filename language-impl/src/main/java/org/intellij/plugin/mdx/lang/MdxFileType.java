package org.intellij.plugin.mdx.lang;

import consulo.annotation.access.RequiredReadAction;
import consulo.javascript.language.JavaScriptFileTypeWithVersion;
import consulo.language.file.LanguageFileType;
import consulo.language.version.LanguageVersion;
import consulo.localize.LocalizeValue;
import consulo.mdx.icon.MdxIconGroup;
import consulo.mdx.localize.MdxLocalize;
import consulo.module.Module;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import org.intellij.plugin.mdx.js.MdxJSLanguage;
import org.jspecify.annotations.Nullable;

/**
 * Also a {@link JavaScriptFileTypeWithVersion}: the JavaScript of an MDX file (its template data root) is of the
 * {@link MdxJSLanguage} version.
 */
public final class MdxFileType extends LanguageFileType implements JavaScriptFileTypeWithVersion {
  public static final MdxFileType INSTANCE = new MdxFileType();
  public static final String DEFAULT_EXTENSION = "mdx";

  private MdxFileType() {
    super(MdxLanguage.INSTANCE);
  }

  @Override
  public String getId() {
    return "MDX";
  }

  @Override
  public LocalizeValue getDescription() {
    return MdxLocalize.mdxFileTypeDescription();
  }

  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Image getIcon() {
    return MdxIconGroup.mdx();
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @RequiredReadAction
  @Override
  public LanguageVersion getLanguageVersion(@Nullable Module module, @Nullable VirtualFile virtualFile) {
    return MdxJSLanguage.getInstance();
  }
}
