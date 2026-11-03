package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class MdxJsxBlockMarkerBlock extends MdxBlockMarkerBlock {
  private final MdxJsxScanner.Session myJsxSession;
  private final boolean myHasJsxParent;
  private final MarkdownConstraints myMarkdownConstraints;
  private final MdxMarkdownCodeSpanScanner.Session myCodeSpanSession;
  private boolean mySuppressSubBlocks;
  private int myProcessedProductionCount;
  private int myScannedLimit;
  private MdxJsxScanner.@Nullable Element myScannedElement;
  private MdxJsxScanner.@Nullable Element myCurrentElement;
  private final List<MdxFlowParagraph> myParagraphs = new ArrayList<>();
  private @Nullable Integer myOpeningParagraphStart;

  MdxJsxBlockMarkerBlock(MarkdownConstraints constraints,
                         MdxMarkdownOwnership ownership,
                         ProductionHolder productionHolder,
                         int blockStartOffset,
                         int blockStartIndent,
                         MdxJsxScanner.@Nullable ElementIdentity elementIdentity,
                         CharSequence source,
                         String initialText) {
    this(constraints, ownership, productionHolder, blockStartOffset, blockStartIndent, elementIdentity, source, initialText,
         new MdxJsxScanner.Session(source, blockStartOffset));
  }

  private MdxJsxBlockMarkerBlock(MarkdownConstraints constraints,
                                 MdxMarkdownOwnership ownership,
                                 ProductionHolder productionHolder,
                                 int blockStartOffset,
                                 int blockStartIndent,
                                 MdxJsxScanner.@Nullable ElementIdentity elementIdentity,
                                 CharSequence source,
                                 String initialText,
                                 MdxJsxScanner.Session jsxSession) {
    super(new MdxJsxMarkdownConstraints(constraints, blockStartIndent, elementIdentity, jsxSession::getEndOffset),
          productionHolder, blockStartOffset, source, initialText);
    myJsxSession = jsxSession;
    myHasJsxParent = constraints instanceof MdxJsxMarkdownConstraints;
    myMarkdownConstraints = constraints instanceof MdxJsxMarkdownConstraints jsxConstraints ? jsxConstraints.markdownConstraints() : constraints;
    myCodeSpanSession = new MdxMarkdownCodeSpanScanner.Session(source, blockStartOffset, source.length(), ownership::paragraphEnd);
    myProcessedProductionCount = initialProductionCount;
    myScannedLimit = blockStartOffset;

    scanElement(getCurrentEndOffset());
    retainCurrentElement();
    myOpeningParagraphStart = findOpeningParagraphStart();
  }

  @Nullable
  Integer getBodyEnd() {
    MdxJsxScanner.Element element = myCurrentElement;
    if (element == null || element.elements().isEmpty()) {
      return null;
    }
    MdxJsxScanner.ElementRecord last = element.elements().get(element.elements().size() - 1);
    if (last.index() != 0 || last.bodyRange() == null) {
      return null;
    }
    return last.bodyRange().getEndOffset();
  }

  @Override
  public boolean allowsSubBlocks() {
    if (myOpeningParagraphStart != null) {
      return true;
    }
    return (!isCloseScheduled() || hasClosingLineContent()) && !mySuppressSubBlocks && hasCompleteOpeningTag();
  }

  @Nullable
  Integer takeOpeningParagraphStart() {
    Integer start = myOpeningParagraphStart;
    if (start == null) {
      return null;
    }
    myOpeningParagraphStart = null;
    return start;
  }

  MarkerBlock createParagraph(int start,
                              int end,
                              MarkdownConstraints paragraphConstraints,
                              BiFunction<LookaheadText.Position, MarkdownConstraints, Boolean> interruptsParagraph,
                              Consumer<LookaheadText.Position> onContinuation) {
    MdxFlowParagraph paragraph = new MdxFlowParagraph(start, end, paragraphConstraints, this, source, interruptsParagraph, onContinuation);
    myParagraphs.add(paragraph);
    return paragraph.marker;
  }

  void observeParagraphLine(LookaheadText.Position pos) {
    if (pos.getNextLineOrEofOffset() <= getCurrentEndOffset() || isCloseScheduled()) {
      return;
    }
    LookaheadText.Position lineStart = pos.nextPosition(-pos.getOffsetInCurrentLine() - 1);
    if (lineStart == null) {
      return;
    }
    processToken(lineStart, myConstraints);
  }

  private @Nullable Integer findOpeningParagraphStart() {
    MdxJsxScanner.Element element = myCurrentElement;
    if (element == null || element.tags().isEmpty()) {
      return null;
    }
    MdxJsxScanner.Tag opening = element.tags().get(0);
    if (opening.kind() != MdxJsxScanner.TagKind.OPENING) {
      return null;
    }
    int start = opening.range().getEndOffset();
    Integer bodyEnd = getBodyEnd();
    int end = bodyEnd != null ? Math.min(bodyEnd, getCurrentEndOffset()) : getCurrentEndOffset();
    if (start >= end || MdxBlockProvider.isBlank(source.subSequence(start, end))) {
      return null;
    }
    if (source.subSequence(start, end).toString().indexOf('\n') >= 0) {
      return null;
    }
    return start;
  }

  private boolean hasClosingLineContent() {
    Integer end = getBodyEnd();
    if (end == null) {
      return false;
    }
    int start = lastIndexOf(source, '\n', getCurrentEndOffset() - 1) + 1;
    return start < end && !MdxBlockProvider.isBlank(source.subSequence(start, end));
  }

  void addMarkdownOpacity(TextRange range) {
    myJsxSession.addOpaqueRanges(List.of(range));
  }

  @Override
  protected void prepareForLine() {
    mySuppressSubBlocks = myJsxSession.isInJavaScript();
  }

  @Override
  protected void appendLine(int candidateEndOffset) {
    boolean hadCompleteOpeningTag = hasCompleteOpeningTag();
    super.appendLine(candidateEndOffset);
    retainCurrentElement();
    if (!hadCompleteOpeningTag && hasCompleteOpeningTag()) {
      myOpeningParagraphStart = findOpeningParagraphStart();
      if (!isCurrentBlockTerminated()) {
        mySuppressSubBlocks = true;
      }
    }
  }

  @Override
  protected MarkerBlock.ProcessingResult activeProcessingResult() {
    return myHasJsxParent && isCloseScheduled() ? MarkerBlock.ProcessingResult.PASS : MarkerBlock.ProcessingResult.CANCEL;
  }

  @Override
  protected boolean shouldAppendLine(LookaheadText.Position pos, int candidateEndOffset) {
    MarkdownConstraints nextConstraints = myConstraints.applyToNextLine(pos);
    // A JSX block cannot continue after its Markdown container ends.
    if (!MarkdownConstraints.extendsPrev(nextConstraints, myMarkdownConstraints)) {
      return false;
    }
    MdxJsxScanner.Element element = scanElement(candidateEndOffset);
    return (element != null && element.termination() != MdxJsxScanner.Termination.UNTERMINATED) ||
           MarkdownConstraints.extendsPrev(nextConstraints, myConstraints) ||
           element != null;
  }

  @Override
  protected boolean isTerminated(int candidateEndOffset) {
    MdxJsxScanner.Element element = scanElement(candidateEndOffset);
    return element != null && element.termination() != MdxJsxScanner.Termination.UNTERMINATED;
  }

  @Override
  protected List<SequentialParser.Node> createNodes() {
    List<SequentialParser.Node> paragraphNodes = new ArrayList<>();
    for (MdxFlowParagraph paragraph : myParagraphs) {
      paragraphNodes.addAll(paragraph.getProduction());
    }
    MdxJsxScanner.Element element = scanElement(getCurrentEndOffset());
    // A marker may be finalized merely because a nested Markdown block takes over. Its current
    // range is then only a scanner snapshot and can cross the nested block's eventual JSX root.
    // Publish recovered roots only at boundaries that cannot later move: a consumed closing tag
    // or the actual end of input.
    if (element == null) {
      TextRange openingPrefix = MdxJsxScanner.incompleteOpeningTagRange(source, blockStartOffset, getCurrentEndOffset());
      if (openingPrefix == null) {
        return paragraphNodes;
      }
      List<SequentialParser.Node> result = new ArrayList<>();
      result.add(MdxTextRangeSet.toMarkdownNode(openingPrefix, MdxMarkdownLibElementTypes.MDX_JSX_OPENING_ELEMENT));
      result.add(MdxTextRangeSet.toMarkdownNode(openingPrefix, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT));
      result.addAll(paragraphNodes);
      return result;
    }
    if (element.termination() == MdxJsxScanner.Termination.UNTERMINATED && getCurrentEndOffset() < source.length()) {
      return paragraphNodes;
    }
    List<SequentialParser.Node> result = MdxBlockNodeFactory.createFlowElementNodes(element);
    result.addAll(paragraphNodes);
    return result;
  }

  private boolean hasCompleteOpeningTag() {
    return myCurrentElement != null;
  }

  private MdxJsxScanner.@Nullable Element scanElement(int limit) {
    if (limit < myScannedLimit) {
      return myCurrentElement;
    }
    ingestProductionOpacity();
    if (limit > myScannedLimit) {
      List<TextRange> codeSpanRanges = myCodeSpanSession.advanceTo(limit, myJsxSession.opaqueRangeLookup());
      myJsxSession.addOpaqueRanges(codeSpanRanges);
    }
    myScannedElement = myJsxSession.advanceTo(limit);
    myScannedLimit = limit;
    if (limit == getCurrentEndOffset()) {
      myCurrentElement = myScannedElement;
    }
    return myScannedElement;
  }

  private void ingestProductionOpacity() {
    List<SequentialParser.Node> productions = productions();
    if (myProcessedProductionCount >= productions.size()) {
      return;
    }
    List<TextRange> ranges = new ArrayList<>();
    for (int index = myProcessedProductionCount; index < productions.size(); index++) {
      SequentialParser.Node production = productions.get(index);
      if ((production.getType() == MarkdownElementTypes.CODE_FENCE || production.getType() == MarkdownElementTypes.HTML_BLOCK) &&
          production.getStartOffset() >= blockStartOffset) {
        ranges.add(new TextRange(production.getStartOffset(), production.getEndOffset()));
      }
    }
    myProcessedProductionCount = productions.size();
    myJsxSession.addOpaqueRanges(ranges);
  }

  private void retainCurrentElement() {
    if (myScannedLimit != getCurrentEndOffset()) {
      throw new IllegalStateException();
    }
    myCurrentElement = myScannedElement;
  }

  private static int lastIndexOf(CharSequence text, char c, int from) {
    for (int i = Math.min(from, text.length() - 1); i >= 0; i--) {
      if (text.charAt(i) == c) {
        return i;
      }
    }
    return -1;
  }
}
