package org.intellij.plugin.mdx.highlighting;

import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.EditorColorsScheme;
import consulo.codeEditor.EditorHighlighter;
import consulo.language.editor.highlight.EditorHighlighterProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.intellij.plugin.mdx.lang.MdxFileType;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class MdxEditorHighlighterProvider implements EditorHighlighterProvider {
  @Override
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                FileType fileType,
                                                @Nullable VirtualFile virtualFile,
                                                EditorColorsScheme colors) {
    return new MdxEditorHighlighter(colors);
  }

  @Override
  public FileType getFileType() {
    return MdxFileType.INSTANCE;
  }
}
