/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugin.mdx;

import consulo.application.ReadAction;
import consulo.document.Document;
import consulo.it.CodeInsightTestFixture;
import consulo.language.editor.folding.FoldingBuilder;
import consulo.language.editor.folding.FoldingDescriptor;
import consulo.language.editor.folding.LanguageFolding;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Helpers for the MDX integration tests: test data, folding markup of all PSI roots, error checks.
 */
public final class MdxItTestUtil {
  private MdxItTestUtil() {
  }

  public static String loadText(String resourcePath) throws IOException {
    try (InputStream stream = MdxItTestUtil.class.getResourceAsStream("/" + resourcePath)) {
      Objects.requireNonNull(stream, resourcePath);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  /**
   * The text with {@code <fold text='placeholder'>} markup of the folding regions of every PSI root of the file (without
   * the collapsed state: the headless application has no code folding settings).
   */
  public static String getFoldingDescription(CodeInsightTestFixture fixture) {
    PsiFile file = fixture.getFile();
    Document document = fixture.getDocument();
    return ReadAction.compute(() -> {
      record Border(int offset, boolean open, int otherEnd, String text) {
      }
      List<Border> borders = new ArrayList<>();
      for (PsiFile root : file.getViewProvider().getAllFiles()) {
        FoldingBuilder builder = FoldingBuilder.forLanguageComposite(root.getLanguage());
        if (builder == null) {
          continue;
        }
        for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptors(builder, root, document, false)) {
          String placeholder = descriptor.getPlaceholderText();
          if (placeholder == null) {
            placeholder = builder.getPlaceholderText(descriptor.getElement());
          }
          if (placeholder == null) {
            placeholder = "...";
          }
          int start = descriptor.getRange().getStartOffset();
          int end = descriptor.getRange().getEndOffset();
          borders.add(new Border(start, true, end, "<fold text='" + placeholder + "'>"));
          borders.add(new Border(end, false, start, "</fold>"));
        }
      }
      borders.sort(Comparator.comparingInt(Border::offset)
                     .thenComparing(Border::open)
                     .thenComparing((a, b) -> Integer.compare(b.otherEnd(), a.otherEnd())));
      String text = document.getText();
      StringBuilder result = new StringBuilder();
      int position = 0;
      for (Border border : borders) {
        result.append(text, position, border.offset());
        result.append(border.text());
        position = border.offset();
      }
      result.append(text.substring(position));
      return result.toString();
    });
  }

  /**
   * The PSI errors of all roots of the file.
   */
  public static List<String> psiErrors(CodeInsightTestFixture fixture) {
    PsiFile file = fixture.getFile();
    return ReadAction.compute(() -> {
      List<String> errors = new ArrayList<>();
      for (PsiFile root : file.getViewProvider().getAllFiles()) {
        for (PsiErrorElement error : PsiTreeUtil.findChildrenOfType(root, PsiErrorElement.class)) {
          errors.add(error.getErrorDescription() + " @ " + error.getTextRange());
        }
      }
      return errors;
    });
  }

  /**
   * The highlights of ERROR severity.
   */
  public static List<String> errorHighlights(CodeInsightTestFixture fixture) throws Exception {
    List<String> errors = new ArrayList<>();
    for (HighlightInfo info : fixture.doHighlighting()) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        errors.add(info.getDescription() + " @ [" + info.getStartOffset() + ", " + info.getEndOffset() + ")");
      }
    }
    return errors;
  }
}
