// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugin.mdx.lang;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Commenter;
import consulo.language.Language;
import consulo.language.editor.MultipleLangCommentProvider;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;
import consulo.language.template.TemplateLanguageFileViewProvider;
import org.intellij.plugin.mdx.lang.template.MdxFileViewProvider;
import org.jspecify.annotations.Nullable;

/**
 * Comments in MDX files: {@code {/* *}/} outside JavaScript, the JavaScript syntax inside ESM and expressions.
 */
@ExtensionImpl(order = "first")
public class MdxCommentProvider implements MultipleLangCommentProvider {
  private final MdxCommenter myMdxCommenter = new MdxCommenter();
  private final MdxCommenter myJavascriptTagCommenter = new MdxCommenter(MdxCommentContext.JAVASCRIPT);

  @Override
  public boolean canProcess(PsiFile file, FileViewProvider viewProvider) {
    return viewProvider instanceof MdxFileViewProvider;
  }

  @Override
  public Commenter getLineCommenter(PsiFile file, Editor editor, Language lineStartLanguage, Language lineEndLanguage) {
    Commenter fallback = fallbackCommenter(file, editor, lineStartLanguage, lineEndLanguage);
    return fallback != null && fallback.getLineCommentPrefix() != null ? myJavascriptTagCommenter : myMdxCommenter;
  }

  private @Nullable Commenter fallbackCommenter(PsiFile file, Editor editor, Language startLanguage, Language endLanguage) {
    FileViewProvider viewProvider = file.getViewProvider();
    MultipleLangCommentProvider provider = MultipleLangCommentProvider.EP_NAME.findFirstSafe(
      it -> !(it instanceof MdxCommentProvider) && it.canProcess(file, viewProvider));
    if (provider != null) {
      return provider.getLineCommenter(file, editor, startLanguage, endLanguage);
    }

    Language language = Commenter.forLanguage(startLanguage) == null || file.getLanguage().getBaseLanguage() == startLanguage
                        ? file.getLanguage()
                        : startLanguage;
    if (viewProvider instanceof TemplateLanguageFileViewProvider templateViewProvider &&
        language == templateViewProvider.getTemplateDataLanguage()) {
      language = templateViewProvider.getBaseLanguage();
    }
    return Commenter.forLanguage(language);
  }
}
