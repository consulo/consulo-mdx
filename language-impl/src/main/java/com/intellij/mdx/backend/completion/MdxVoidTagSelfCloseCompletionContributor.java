package com.intellij.mdx.backend.completion;

import com.intellij.xml.util.HtmlUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import org.intellij.plugin.mdx.lang.template.MdxFileViewProvider;

/**
 * When a void HTML element ({@code input}, {@code br}, {@code img}, {@code hr}, …) is chosen from JSX tag-name completion,
 * inserts a self-closing {@code <input/>} instead of leaving {@code <input} open, since JSX requires void elements to be
 * self-closed. Runs first and delegates to the real JSX completions, decorating void-tag results with the insert handler.
 */
@ExtensionImpl(id = "MdxVoidTagSelfCloseCompletionContributor", order = "first")
public class MdxVoidTagSelfCloseCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (!(parameters.getOriginalFile().getViewProvider() instanceof MdxFileViewProvider)) {
      return;
    }
    result.runRemainingContributors(parameters, completionResult -> {
      LookupElement element = completionResult.getLookupElement();
      if (HtmlUtil.isSingleHtmlTagL(element.getLookupString())) {
        result.passResult(completionResult.withLookupElement(withSelfClose(element)));
      }
      else {
        result.passResult(completionResult);
      }
    });
  }

  private static LookupElement withSelfClose(LookupElement element) {
    return LookupElementDecorator.withInsertHandler(element, (InsertionContext context, LookupElementDecorator<LookupElement> item) -> {
      item.getDelegate().handleInsert(context);

      Document document = context.getDocument();
      int nameStart = context.getStartOffset();
      // Self-close only an opening JSX tag name (`<input`) — not a closing tag (`</input`), a member
      // expression, or a plain JS identifier that happens to match a void-element name.
      if (nameStart <= 0 || document.getCharsSequence().charAt(nameStart - 1) != '<') {
        return;
      }

      Editor editor = context.getEditor();
      int caret = editor.getCaretModel().getOffset();
      CharSequence chars = document.getCharsSequence();
      char next = caret < chars.length() ? chars.charAt(caret) : ' ';
      if (next == '/' || next == '>') {
        return;
      }

      document.insertString(caret, "/>");
      editor.getCaretModel().moveToOffset(caret + 2);
      context.commitDocument();
    });
  }

  @Override
  public Language getLanguage() {
    return JavaScriptLanguage.INSTANCE;
  }
}
