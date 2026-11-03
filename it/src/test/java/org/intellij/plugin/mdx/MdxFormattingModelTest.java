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
import consulo.document.util.TextRange;
import consulo.it.AllowLogError;
import consulo.it.CodeInsightTestFixture;
import consulo.it.HeadlessProjectExtension;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.FormattingContext;
import consulo.language.codeStyle.FormattingModel;
import consulo.language.codeStyle.FormattingModelBuilder;
import consulo.language.psi.PsiFile;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the formatting model of MDX files (the Markdown blocks with the JavaScript/JSX blocks of the embedded code):
 * the blocks nest, are ordered, and their leaves cover every non-whitespace character. The headless application can't
 * reformat, so the formatting results themselves are not checked.
 */
@ExtendWith(HeadlessProjectExtension.class)
@Disabled("consulo-it has no CodeStyleInternalHelper: FormatterUtil, used by the template formatter, needs it")
@AllowLogError({
  "consulo.language.impl.internal.psi.resolve.reference.ReferenceProvidersRegistryImpl",
  "consulo.xml.impl.internal.ExternalResource",
  "consulo.component.internal.NewExtensionPointImpl"
})
public class MdxFormattingModelTest {
  @Test
  public void testFormatting(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "Formatting");
  }

  @Test
  public void testMarkdownProse(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "MarkdownProse");
  }

  @Test
  public void testGfmTable(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "GfmTable");
  }

  @Test
  public void testReformatNestedJsxIsIdempotent(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "ReformatNestedJsxIsIdempotent");
  }

  @Test
  public void testCodeFenceInsideJsxFlowElementIsIndented(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "CodeFenceInsideJsxFlowElementIsIndented");
  }

  @Test
  public void testListInsideJsxFlowElementIsIndentedConsistently(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "ListInsideJsxFlowElementIsIndentedConsistently");
  }

  @Test
  public void testEsmBlockJsxBodyIsReformatted(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "EsmBlockJsxBodyIsReformatted");
  }

  private static void doTest(CodeInsightTestFixture fixture, String name) throws Exception {
    fixture.configureByText(name + ".mdx", MdxItTestUtil.loadText("format/" + name + ".mdx"));
    PsiFile file = fixture.getFile();
    ReadAction.run(() -> {
      FormattingModelBuilder builder = Objects.requireNonNull(FormattingModelBuilder.forLanguage(MdxLanguage.INSTANCE).get(0));
      FormattingModel model = builder.createModel(FormattingContext.create(file, CodeStyle.getSettings(file)));
      Block root = model.getRootBlock();
      assertEquals(file.getTextRange(), root.getTextRange(), "root block range");
      List<TextRange> leaves = new ArrayList<>();
      collectLeaves(root, leaves);
      String text = file.getText();
      int covered = 0;
      for (TextRange leaf : leaves) {
        assertTrue(leaf.getStartOffset() >= covered, "overlapping leaf " + leaf + " after " + covered);
        assertBlank(text, covered, leaf.getStartOffset());
        covered = leaf.getEndOffset();
      }
      assertBlank(text, covered, text.length());
    });
  }

  private static void collectLeaves(Block block, List<TextRange> leaves) {
    List<Block> subBlocks = block.getSubBlocks();
    if (subBlocks.isEmpty()) {
      leaves.add(block.getTextRange());
      return;
    }
    TextRange range = block.getTextRange();
    for (Block subBlock : subBlocks) {
      assertTrue(range.contains(subBlock.getTextRange()), subBlock.getTextRange() + " outside of its parent " + range);
      collectLeaves(subBlock, leaves);
    }
  }

  private static void assertBlank(String text, int start, int end) {
    String gap = text.substring(start, end);
    assertTrue(gap.isBlank(), "text not covered by a block: [" + start + ", " + end + ") '" + gap + "'");
  }
}
