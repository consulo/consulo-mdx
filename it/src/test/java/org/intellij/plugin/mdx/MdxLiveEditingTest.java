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

@Disabled("needs an editor and the typed action handlers, consulo-it has none")
public class MdxLiveEditingTest {
  @Test
  public void testEnablerApprovesLessThan() {
  }

  @Test
  public void testEnablerApprovesTagNamePrefix() {
  }

  @Test
  public void testEnablerRejectsInsideFencedCodeBlock() {
  }

  @Test
  public void testEnablerRejectsInsideFrontMatter() {
  }

  @Test
  public void testEnablerRejectsNonTagContext() {
  }

  @Test
  public void testTagNameAutoPopupFiresInJsxContext() {
  }

  @Test
  public void testAutoCloseHtmlTag() {
  }

  @Test
  public void testAutoCloseCustomComponentTag() {
  }

  @Test
  public void testAutoCloseFragment() {
  }

  @Test
  public void testAutoCloseFragmentNestedInTag() {
  }

  @Test
  public void testAutoCloseFragmentNestedInBraces() {
  }

  @Test
  public void testAutoCloseDoesNotAbsorbAdjacentTrailingText() {
  }

  @Test
  public void testAutoCloseInDeeplyNestedSameNameTags() {
  }

  @Test
  public void testAutoCloseInDeepMixedNameTagsConvergesToFreshParse() {
  }

  @Test
  public void testRenamingMultilineOpeningTagKeepsMdxJsPsi() {
  }

  @Test
  public void testWrappedParagraphEditsConvergeToFreshParse() {
  }

  @Test
  public void testNestedJsxStructureAndHighlightingDoNotDependOnEditOrder() {
  }

  @Test
  public void testLargeDocumentCommitsAfterAnEarlyEdit() {
  }

  @Test
  public void testTypingUnclosedFenceInsideJsxKeepsOuterHighlighting() {
  }

  @Test
  public void testNoAutoCloseInsideArrowFunctionExpression() {
  }

  @Test
  public void testNoAutoCloseBuildingArrowInUnterminatedExpression() {
  }

  @Test
  public void testNoAutoCloseForComparisonInsideExpression() {
  }

  @Test
  public void testNoAutoCloseInsideQuotedAttributeValue() {
  }

  @Test
  public void testAutoCloseAfterCompletedAttributeExpression() {
  }

  @Test
  public void testAutoCloseAfterMultilineAttributeExpression() {
  }

  @Test
  public void testAutoCloseAfterMultilineAttributeExpressionWithTrailingSpace() {
  }

  @Test
  public void testNoAutoCloseVoidBrTag() {
  }

  @Test
  public void testNoAutoCloseVoidInputTag() {
  }

  @Test
  public void testAutoCloseCapitalizedComponentNamedLikeVoidTag() {
  }

  @Test
  public void testAutoWrapLongMarkdownLine() {
  }

  @Test
  public void testAutoWrapKeepsMultiParagraphBlockQuote() {
  }

  @Test
  public void testAutoWrapDoesNotBreakInlineLink() {
  }

  @Test
  public void testTypeExpressionBraceInsideJsxBody() {
  }

  @Test
  public void testTypeInsideIndentedCodeFenceInJsxBody() {
  }

  @Test
  public void testAutoCloseBraceBetweenMarkdownWords() {
  }

  @Test
  public void testBackspaceRemovesEmptyBracePairBetweenMarkdownWords() {
  }

  @Test
  public void testAutoCloseBraceInMidDocumentFlowExpression() {
  }

  @Test
  public void testAutoCloseBraceInJsxAttributeValue() {
  }

  @Test
  public void testAutoCloseBraceInEmptyFile() {
  }

  @Test
  public void testAutoCloseBraceAfterMarkdownText() {
  }

  @Test
  public void testAutoCloseBraceInMiddleOfMarkdownText() {
  }

  @Test
  public void testNoDuplicateCloseBraceBeforeExistingCloser() {
  }

  @Test
  public void testBackspaceRemovesEmptyBracePair() {
  }

  @Test
  public void testAutoCloseNestsInsideExistingEmptyPair() {
  }

  @Test
  public void testNoAutoCloseBraceWhenPairInsertionDisabled() {
  }

  @Test
  public void testNoAutoCloseBraceWhenPairInsertionDisabledInJsxBody() {
  }

  @Test
  public void testNoPairedBackspaceWhenPairInsertionDisabled() {
  }

  @Test
  public void testBackspaceInIncompleteArrowFunctionDoesNotCrashFormatter() {
  }

  @Test
  public void testLineIndentAfterUnclosedFenceFollowingEmptyFenceDoesNotCrashFormatter() {
  }

  @Test
  public void testDoKeywordCompletionInsideFenceDoesNotCrashFormatter() {
  }

  @Test
  public void testAutoCloseBacktick() {
  }

  @Test
  public void testNoAutoCloseBacktickAfterWord() {
  }

  @Test
  public void testAutoCloseBacktickInsideJsxText() {
  }

  @Test
  public void testFenceLanguageAutoPopupFiresOnThirdBacktick() {
  }

  @Test
  public void testEnterBetweenBracesInCodeFenceInsideJsxDoesNotCorruptLexer() {
  }

  @Test
  public void testEnterInsideJavaScriptFenceNestedUnderTwoJsxElementsKeepsInjectionValid() {
  }

  @Test
  public void testEnterBetweenJsxTagsInCodeFenceInsideJsxIsIndented() {
  }

  @Test
  public void testEnterBetweenComponentTagsInCodeFenceInsideJsxIsIndented() {
  }

  @Test
  public void testEnterAfterOpeningJsxTagInCodeFenceInsideJsxIsIndented() {
  }

  @Test
  public void testEnterBetweenJsxTagsInEsmBlockIsIndented() {
  }

  @Test
  public void testEnterBetweenJsxTagsAfterLeadingBlankLineIsIndented() {
  }

  @Test
  public void testEnterBelowIndentedBlankLineInInjectedFenceKeepsInjectionValid() {
  }

  @Test
  public void testEnterOnUnderIndentedBlankLineInInjectedFenceKeepsInjectionValid() {
  }

  @Test
  public void testEnterSplittingTextInJsxFlowElementBodyKeepsSiblingIndent() {
  }

  @Test
  public void testEnterAfterSpaceInJsxFlowElementBodyKeepsSiblingIndent() {
  }
}
