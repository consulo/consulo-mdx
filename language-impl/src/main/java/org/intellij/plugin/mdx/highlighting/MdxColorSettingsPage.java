package org.intellij.plugin.mdx.highlighting;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.localize.LocalizeValue;
import consulo.mdx.localize.MdxLocalize;
import org.intellij.plugins.markdown.highlighting.impl.MarkdownColorSettingsPage;

@ExtensionImpl
public class MdxColorSettingsPage extends MarkdownColorSettingsPage {
  @Override
  public SyntaxHighlighter getHighlighter() {
    return new MdxSyntaxHighlighter();
  }

  @Override
  public LocalizeValue getDisplayName() {
    return MdxLocalize.mdxName();
  }

  @Override
  public String getDemoText() {
    return "import React from 'react';\n\n" +
           "#hello\n\n" +
           "<Button>Press the button</Button>";
  }
}
