package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;

import java.util.List;

abstract class MdxBlockMarkerBlock extends MarkerBlockImpl {
  private final ProductionHolder myProductionHolder;
  protected final int blockStartOffset;
  protected final CharSequence source;
  protected final int initialProductionCount;
  private int myCurrentEndOffset;
  private boolean myCloseScheduled;
  private boolean myFinalized;

  MdxBlockMarkerBlock(MarkdownConstraints constraints,
                      ProductionHolder productionHolder,
                      int blockStartOffset,
                      CharSequence source,
                      String initialText) {
    super(constraints, productionHolder.mark());
    myProductionHolder = productionHolder;
    this.blockStartOffset = blockStartOffset;
    this.source = source;
    initialProductionCount = productionHolder.getProduction().size();
    myCurrentEndOffset = blockStartOffset + initialText.length();
  }

  protected final int getCurrentEndOffset() {
    return myCurrentEndOffset;
  }

  protected final boolean isCloseScheduled() {
    return myCloseScheduled;
  }

  @Override
  public boolean allowsSubBlocks() {
    return false;
  }

  @Override
  public boolean isInterestingOffset(LookaheadText.Position pos) {
    return pos.getOffsetInCurrentLine() == -1;
  }

  @Override
  protected MarkerBlock.ClosingAction getDefaultAction() {
    return MarkerBlock.ClosingAction.DROP;
  }

  @Override
  protected final MarkerBlock.ProcessingResult doProcessToken(LookaheadText.Position pos, MarkdownConstraints currentConstraints) {
    if (myFinalized) {
      return MarkerBlock.ProcessingResult.DEFAULT;
    }
    if (pos.getOffsetInCurrentLine() != -1) {
      return activeProcessingResult();
    }
    prepareForLine();
    if (isCurrentBlockTerminated()) {
      return MarkerBlock.ProcessingResult.DEFAULT;
    }
    int candidateEndOffset = pos.getNextLineOrEofOffset();
    if (!shouldAppendLine(pos, candidateEndOffset)) {
      return MarkerBlock.ProcessingResult.DEFAULT;
    }

    appendLine(candidateEndOffset);
    if (isCurrentBlockTerminated()) {
      scheduleClose(pos.getNextLineOrEofOffset());
    }
    return activeProcessingResult();
  }

  @Override
  public boolean acceptAction(MarkerBlock.ClosingAction action) {
    if (action == MarkerBlock.ClosingAction.DONE || action == MarkerBlock.ClosingAction.DEFAULT) {
      finalizeProductions();
      return true;
    }
    return super.acceptAction(action);
  }

  @Override
  protected int calcNextInterestingOffset(LookaheadText.Position pos) {
    return pos.getNextLineOrEofOffset();
  }

  @Override
  public IElementType getDefaultNodeType() {
    return MdxMarkdownLibElementTypes.MDX_BLOCK;
  }

  protected void prepareForLine() {
  }

  protected void appendLine(int candidateEndOffset) {
    myCurrentEndOffset = candidateEndOffset;
  }

  protected final boolean isCurrentBlockTerminated() {
    return isTerminated(myCurrentEndOffset);
  }

  protected abstract MarkerBlock.ProcessingResult activeProcessingResult();

  protected abstract boolean shouldAppendLine(LookaheadText.Position pos, int candidateEndOffset);

  protected abstract boolean isTerminated(int candidateEndOffset);

  protected abstract List<SequentialParser.Node> createNodes();

  protected final List<SequentialParser.Node> productions() {
    return myProductionHolder.getProduction();
  }

  private void scheduleClose(int offset) {
    myCloseScheduled = true;
    scheduleProcessingResult(offset, MarkerBlock.ProcessingResult.DEFAULT);
  }

  private void finalizeProductions() {
    if (myFinalized) {
      return;
    }
    myFinalized = true;
    List<SequentialParser.Node> nodes = createNodes();
    if (!nodes.isEmpty()) {
      myProductionHolder.addProduction(nodes);
    }
  }
}
