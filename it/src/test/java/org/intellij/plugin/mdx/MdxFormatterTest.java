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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("needs reformat (consulo-it has no CodeStyleInternalHelper) and the JavaScript formatter API (block context, JSX tag blocks) consulo-javascript does not expose")
public class MdxFormatterTest {
  @Test
  public void testFormatting() {
  }

  @Test
  public void testFormattingAfterIsIdempotent() {
  }

  @Test
  public void testFrontMatter() {
  }

  @Test
  public void testGfmTable() {
  }

  @Test
  public void testReferenceLinks() {
  }

  @Test
  public void testTrailingSpace() {
  }

  @Test
  public void testMarkdownProse() {
  }

  @Test
  public void testMarkdownReflowMatchesMarkdown() {
  }

  @Test
  public void testMarkdownReflowRespectsWrapTextSetting() {
  }

  @Test
  public void testBlockquoteWithMultipleParagraphsIsNotFolded() {
  }

  @Test
  public void testBlockquoteInsideJsxKeepsIndent() {
  }

  @Test
  public void testBlockquoteInsideJsxMovesWithBodyIndent() {
  }

  @Test
  public void testBlockquoteInsideJsxKeepsNestedMarkdown() {
  }

  @Test
  public void testBlockquoteInsideNestedJsxUsesBodyIndent() {
  }

  @Test
  public void testBlockquoteInsideFragmentUsesBodyIndent() {
  }

  @Test
  public void testBlockquoteInsideListKeepsRelativeIndent() {
  }

  @Test
  public void testReformatSelectedBlockquoteInsideJsxUsesBodyIndent() {
  }

  @Test
  public void testReformatSelectedBlockquoteWithoutLeadingWhitespaceUsesBodyIndent() {
  }

  @Test
  public void testReformatSelectedBlockquoteKeepsUnselectedListIndent() {
  }

  @Test
  public void testBlockquoteInsideJsxUsesJavaScriptIndent() {
  }

  @Test
  public void testBlockquoteInsideJsxRespectsTagsWithoutChildIndent() {
  }

  @Test
  public void testBlockquoteOutsideJsxKeepsContent() {
  }

  @Test
  public void testIdempotent() {
  }

  @Test
  public void testEnterAfterCodeFenceInJsxFlowElementIndentsToBodyLevel() {
  }

  @Test
  public void testCodeFenceInsideJsxFlowElementIsIndented() {
  }

  @Test
  public void testCodeFenceInsideJsxFlowElementTightIsIndented() {
  }

  @Test
  public void testListInsideJsxFlowElementIsIndentedConsistently() {
  }

  @Test
  public void testNestedListInsideJsxKeepsNesting() {
  }

  @Test
  public void testReformatSelectedListInsideJsxKeepsIndent() {
  }

  @Test
  public void testReformatSelectedNestedListInsideJsxKeepsIndent() {
  }

  @Test
  public void testReformatSelectedNestedListWithoutLeadingWhitespaceKeepsIndent() {
  }

  @Test
  public void testReformatSelectedListInsideNestedJsxUsesBodyIndent() {
  }

  @Test
  public void testReformatSelectedNestedListKeepsUnselectedParentIndent() {
  }

  @Test
  public void testReformatSelectedNestedItemsKeepsEachRelativeIndent() {
  }

  @Test
  public void testReformatSelectedJsxInsideListKeepsNesting() {
  }

  @Test
  public void testReformatSelectedNestedListKeepsUnselectedTabs() {
  }

  @Test
  public void testReformatSelectedNestedListFormatsInlineMdx() {
  }

  @Test
  public void testReformatSelectedNestedListKeepsContinuationIndent() {
  }

  @Test
  public void testNestedListsInsideJsxMoveWithBodyIndent() {
  }

  @Test
  public void testNestedListsInsideJsxKeepEachRelativeIndent() {
  }

  @Test
  public void testNestedListsInsideJsxRespectTabStops() {
  }

  @Test
  public void testNestedListsInsideNestedJsxKeepNesting() {
  }

  @Test
  public void testJsxInsideListStartsNewListIndentContext() {
  }

  @Test
  public void testNestedListInsideJsxUsesJavaScriptIndent() {
  }

  @Test
  public void testNestedListWithInlineMdxKeepsContent() {
  }

  @Test
  public void testEmptyCodeFenceInsideJsxFlowElementHasNoBlankLine() {
  }

  @Test
  public void testCodeFenceThenListInsideJsxFlowElementIndentsBody() {
  }

  @Test
  public void testCodeFenceAndListInsideJsxFlowElementReformatIsIdempotent() {
  }

  @Test
  public void testCodeFenceInsideJsxFlowElementReformatIsIdempotent() {
  }

  @Test
  public void testNewLineAfterCodeFenceInsideJsxIsIndented() {
  }

  @Test
  public void testCompletionInsideJsxFlowElementKeepsCodeFenceIndent() {
  }

  @Test
  public void testConsoleCompletesInsideTsxCodeFenceInsideJsxFlowElement() {
  }

  @Test
  public void testConsoleCompletionInsideIndentedTsxCodeFenceKeepsIndent() {
  }

  @Test
  public void testEnterInsideIndentedInjectedCodeFenceKeepsInjectionValid() {
  }

  @Test
  public void testFunctionKeywordCompletionInsideIndentedTsxCodeFenceKeepsIndent() {
  }

  @Test
  public void testEnterInEmptyIndentedCodeFenceIndentsToFenceBase() {
  }

  @Test
  public void testTabInEmptyIndentedCodeFenceIndentsOneStepFromFenceBase() {
  }

  @Test
  public void testEnterInFenceWithoutLanguageKeepsIndent() {
  }

  @Test
  public void testEnterInFenceWithUninjectedLanguageKeepsIndent() {
  }

  @Test
  public void testEnterOnPreIndentedBlankLineInFenceWithoutLanguageKeepsIndent() {
  }

  @Test
  public void testEnterOnFenceOpeningLineIndentsFirstBodyLine() {
  }

  @Test
  public void testEnterOnFenceOpeningLineWithoutInfoStringIndentsFirstBodyLine() {
  }

  @Test
  public void testEnterBetweenAdjacentFenceBackticksIndentsSplitLine() {
  }

  @Test
  public void testTabInsideCodeFenceInsideJsxKeepsFenceIndent() {
  }

  @Test
  public void testPasteIntoIndentedCodeFenceIsIndentedToFenceBase() {
  }

  @Test
  public void testPasteIntoTopLevelCodeFenceIsFormatted() {
  }

  @Test
  public void testPasteIntoEmptyIndentedCodeFenceIsIndentedToFenceBase() {
  }

  @Test
  public void testPasteIntoOpaqueCodeFenceIsIndentedToFenceBase() {
  }

  @Test
  public void testReformatCodeFenceWithJsxContentInsideJsx() {
  }

  @Test
  public void testReformatLessIndentedCodeFenceBodyInsideJsx() {
  }

  @Test
  public void testReformatMixedIndentCodeFenceBodyInsideJsx() {
  }

  @Test
  public void testReformatCodeFenceWithBracesInsideJsxIsIdempotent() {
  }

  @Test
  public void testReformatKeepsInlineExpressionInline() {
  }

  @Test
  public void testReformatNestedJsxIsIdempotent() {
  }

  @Test
  public void testNonInlineTagWithOnlyInlineChildrenStaysCompact() {
  }

  @Test
  public void testMixedInlineAndBlockChildrenWrapsClosingTag() {
  }

  @Test
  public void testJsxFlowElementsKeepLineBreaksWithoutKeepLineBreaksInText() {
  }

  @Test
  public void testJsxInEsmBlockKeepsLineBreaksWithoutKeepLineBreaksInText() {
  }

  @Test
  public void testEsmBlockJsxBodyIsReformatted() {
  }

  @Test
  public void testGluedEsmStatementsWithoutJsxStayOpaque() {
  }

  @Test
  public void testEnterInsideBlockquotedCodeFenceKeepsQuoteMarker() {
  }

  @Test
  public void testReformatBlockquotedCodeFenceKeepsQuoteMarker() {
  }

  @Test
  public void testEnterInsideTabIndentedCodeFenceKeepsTabs() {
  }

  @Test
  public void testPasteIntoCodeFenceInListItemIsIndentedToItemBody() {
  }

  @Test
  public void testFrontMatterInsertsBlankLineBeforeImport() {
  }

  @Test
  public void testFrontMatterCollapsesExcessBlankLinesToOne() {
  }

  @Test
  public void testFrontMatterFollowedByBlankLinesAndJsxReformatIsIdempotent() {
  }
}
