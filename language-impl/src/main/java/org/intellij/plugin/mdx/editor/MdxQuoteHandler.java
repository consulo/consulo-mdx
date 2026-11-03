package org.intellij.plugin.mdx.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.HighlighterIterator;
import consulo.language.editor.action.FileQuoteHandler;
import consulo.virtualFileSystem.fileType.FileType;
import org.intellij.plugin.mdx.lang.MdxFileType;
import org.intellij.plugins.markdown.braces.MarkdownQuoteHandler;

@ExtensionImpl
public class MdxQuoteHandler implements FileQuoteHandler {
  private final MarkdownQuoteHandler myDelegate = new MarkdownQuoteHandler();

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    return myDelegate.isClosingQuote(iterator, offset);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    return myDelegate.isOpeningQuote(iterator, offset);
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return myDelegate.hasNonClosedLiteral(editor, iterator, offset);
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return myDelegate.isInsideLiteral(iterator);
  }

  @Override
  public FileType getFileType() {
    return MdxFileType.INSTANCE;
  }
}
