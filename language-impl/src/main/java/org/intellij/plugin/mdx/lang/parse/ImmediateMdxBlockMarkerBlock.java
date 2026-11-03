package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;

import java.util.List;

/**
 * A single-line ESM block or flow expression, complete on its first line.
 */
final class ImmediateMdxBlockMarkerBlock extends MarkerBlockImpl {
  private final IElementType myNodeType;

  ImmediateMdxBlockMarkerBlock(MarkdownConstraints constraints,
                               ProductionHolder productionHolder,
                               IElementType nodeType,
                               List<SequentialParser.Node> nodes) {
    super(constraints, productionHolder.mark());
    myNodeType = nodeType;
    productionHolder.addProduction(nodes);
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
    // DONE, not DROP: a single-line block closed via flushMarkers at EOF must still emit its wrapping
    // node, or its children leak as detached siblings.
    return MarkerBlock.ClosingAction.DONE;
  }

  @Override
  protected MarkerBlock.ProcessingResult doProcessToken(LookaheadText.Position pos, MarkdownConstraints currentConstraints) {
    return MarkerBlock.ProcessingResult.DEFAULT;
  }

  @Override
  protected int calcNextInterestingOffset(LookaheadText.Position pos) {
    return pos.getNextLineOrEofOffset();
  }

  @Override
  public IElementType getDefaultNodeType() {
    return myNodeType;
  }
}
