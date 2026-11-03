package com.intellij.mdx.backend.js;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.Language;
import consulo.language.editor.refactoring.ImportOptimizer;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import org.intellij.plugin.mdx.lang.parse.MdxEsmScanner;
import org.intellij.plugin.mdx.lang.template.MdxFileViewProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimizes the imports of the JavaScript of an MDX file with the JavaScript optimizer, then puts every ESM statement
 * glued to the previous one on a line of its own (MDX needs ESM statements on separate lines).
 */
@ExtensionImpl(order = "first")
public class MdxJSImportOptimizer implements ImportOptimizer {
  @Override
  public boolean supports(PsiFile file) {
    return file.getLanguage() == JavaScriptLanguage.INSTANCE && file.getViewProvider() instanceof MdxFileViewProvider;
  }

  @RequiredReadAction
  @Override
  public Runnable processFile(PsiFile file) {
    ImportOptimizer javaScriptOptimizer = findJavaScriptOptimizer(file);
    Runnable delegate = javaScriptOptimizer == null ? null : javaScriptOptimizer.processFile(file);
    return () -> {
      if (delegate != null) {
        delegate.run();
      }
      repairAdjacentEsmStatements(file);
    };
  }

  private static @Nullable ImportOptimizer findJavaScriptOptimizer(PsiFile file) {
    for (ImportOptimizer optimizer : ImportOptimizer.forLanguage(JavaScriptLanguage.INSTANCE)) {
      if (!(optimizer instanceof MdxJSImportOptimizer) && optimizer.supports(file)) {
        return optimizer;
      }
    }
    return null;
  }

  private static void repairAdjacentEsmStatements(PsiFile file) {
    PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    Document document = manager.getDocument(file);
    if (document == null) {
      return;
    }
    List<Integer> offsets = collectMissingSeparatorOffsets(document.getCharsSequence().toString());
    for (int i = offsets.size() - 1; i >= 0; i--) {
      document.insertString(offsets.get(i), "\n");
    }
    if (!offsets.isEmpty()) {
      manager.commitDocument(document);
    }
  }

  static List<Integer> collectMissingSeparatorOffsets(CharSequence text) {
    List<Integer> offsets = new ArrayList<>();
    int offset = 0;
    while (offset < text.length()) {
      if (MdxEsmScanner.isLineStart(text, offset)) {
        MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(text, offset);
        int blockEnd = block == null ? text.length() : block.range().getEndOffset();
        offsets.addAll(MdxEsmScanner.findMissingStatementSeparators(text, offset, blockEnd));
        offset = Math.max(blockEnd, offset + 1);
      }
      else {
        offset++;
      }
    }
    return offsets;
  }

  @Override
  public Language getLanguage() {
    return JavaScriptLanguage.INSTANCE;
  }
}
