package org.intellij.plugin.mdx.lang.parse;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiFile;
import consulo.language.ast.IFileElementType;
import consulo.language.version.LanguageVersion;
import org.intellij.markdown.parser.MarkdownParserManager;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserAdapter;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition;

@ExtensionImpl
public class MdxParserDefinition extends MarkdownParserDefinition {
  @Override
  public Language getLanguage() {
    return MdxLanguage.INSTANCE;
  }

  @Override
  public IFileElementType getFileNodeType() {
    return MdxElementTypes.MDX_FILE_NODE_TYPE;
  }

  @Override
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new MarkdownToplevelLexer(MdxFlavourDescriptor.INSTANCE);
  }

  @Override
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new MarkdownParserAdapter(MdxFlavourDescriptor.INSTANCE);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    MdxFile mdxFile = new MdxFile(viewProvider);
    mdxFile.putUserData(MarkdownParserManager.FLAVOUR_DESCRIPTION, MdxFlavourDescriptor.INSTANCE);
    return mdxFile;
  }
}
