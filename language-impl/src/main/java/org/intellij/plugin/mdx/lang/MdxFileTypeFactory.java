package org.intellij.plugin.mdx.lang;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;

/**
 * Registers {@link MdxFileType} for {@code *.mdx}.
 */
@ExtensionImpl
public class MdxFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(FileTypeConsumer consumer) {
    consumer.consume(MdxFileType.INSTANCE, MdxFileType.DEFAULT_EXTENSION);
  }
}
