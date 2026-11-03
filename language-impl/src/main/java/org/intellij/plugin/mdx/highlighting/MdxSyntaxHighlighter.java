package org.intellij.plugin.mdx.highlighting;

import consulo.language.lexer.Lexer;
import org.intellij.plugins.markdown.highlighting.MarkdownSyntaxHighlighter;

public class MdxSyntaxHighlighter extends MarkdownSyntaxHighlighter {
  private final Lexer myHighlightingLexer = new MdxHighlightingLexer();

  @Override
  public Lexer getHighlightingLexer() {
    return myHighlightingLexer;
  }
}
