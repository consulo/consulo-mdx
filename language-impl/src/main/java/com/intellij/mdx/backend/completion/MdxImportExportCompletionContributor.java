package com.intellij.mdx.backend.completion;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.util.ProcessingContext;
import org.intellij.markdown.MarkdownLanguage;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;

/**
 * Offers {@code import} and {@code export} at the start of a paragraph. Registered for Markdown: the prose of an MDX
 * file is Markdown PSI.
 */
@ExtensionImpl(id = "MdxImportExportCompletionContributor")
public class MdxImportExportCompletionContributor extends CompletionContributor {
  public MdxImportExportCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(MarkdownTokenTypes.TEXT), new CompletionProvider() {
      @RequiredReadAction
      @Override
      public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
        ASTNode node = parameters.getPosition().getNode();
        ASTNode nodeParent = node.getTreeParent();
        if (nodeParent != null && nodeParent.getPsi() != null && nodeParent.getPsi().getContainingFile() instanceof MdxFile) {
          if (nodeParent.getElementType() == MarkdownElementTypes.PARAGRAPH &&
              (nodeParent.getFirstChildNode() == node || nodeParent.getText().trim().startsWith(node.getText()))) {
            result.addElement(LookupElementBuilder.create("export ").bold());
            result.addElement(LookupElementBuilder.create("import ").bold());
          }
        }
      }
    });
  }

  @Override
  public Language getLanguage() {
    return MarkdownLanguage.INSTANCE;
  }
}
