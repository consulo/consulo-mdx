package org.intellij.plugin.mdx.highlighting;

import consulo.colorScheme.EditorColorsScheme;
import consulo.language.editor.highlight.LayerDescriptor;
import consulo.language.editor.highlight.LayeredLexerEditorHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighter;
import org.intellij.plugin.mdx.js.MdxJSLanguage;
import org.intellij.plugin.mdx.lang.parse.MdxTokenTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;

class MdxEditorHighlighter extends LayeredLexerEditorHighlighter {
  MdxEditorHighlighter(EditorColorsScheme colors) {
    super(new MdxSyntaxHighlighter(), colors);
    // the ECMAScript 6+ (JSX) highlighter of the MdxJS version
    SyntaxHighlighter outerHighlighter = MdxJSLanguage.getInstance().getSyntaxHighlighter();
    registerLayer(MdxTokenTypes.EMBEDDED_JS_CONTENT, new LayerDescriptor(outerHighlighter, "\n;"));
    registerLayer(MarkdownTokenTypes.HTML_TAG, new LayerDescriptor(outerHighlighter, ""));
  }
}
