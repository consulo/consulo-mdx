package org.intellij.plugin.mdx.lang.parse;

import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.impl.CodeFenceMarkerBlock;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.plugins.markdown.lang.parser.blocks.CodeFenceMarkerProvider;

import java.util.List;

final class MdxCodeFenceProvider extends CodeFenceMarkerProvider {
  @Override
  public List<MarkerBlock> createMarkerBlocks(LookaheadText.Position pos,
                                              ProductionHolder productionHolder,
                                              MarkerProcessor.StateInfo stateInfo) {
    OpeningInfo openingInfo = obtainFenceOpeningInfo(pos, stateInfo.getCurrentConstraints());
    if (openingInfo == null) {
      return List.of();
    }
    createOpeningNodes(pos, openingInfo.getInfo(), productionHolder);

    if (!(stateInfo.getCurrentConstraints() instanceof MdxJsxMarkdownConstraints jsxConstraints)) {
      return List.of(new CodeFenceMarkerBlock(stateInfo.getCurrentConstraints(), productionHolder, openingInfo.getDelimiter()));
    }

    MdxJsxMarkdownConstraints fenceConstraints = jsxConstraints.asOpaqueBlockConstraints();
    CodeFenceMarkerBlock delegate = new CodeFenceMarkerBlock(fenceConstraints, productionHolder, openingInfo.getDelimiter());
    boolean hasClosingFence = hasClosingFence(pos, fenceConstraints, openingInfo.getDelimiter());
    return List.of(new MdxRecoveringCodeFenceMarkerBlock(delegate, jsxConstraints, hasClosingFence));
  }

  private static void createOpeningNodes(LookaheadText.Position pos, String info, ProductionHolder productionHolder) {
    int infoStart = pos.getNextLineOrEofOffset() - info.length();
    productionHolder.addProduction(List.of(new SequentialParser.Node(pos.getOffset(), infoStart, MarkdownTokenTypes.CODE_FENCE_START)));
    if (!info.isEmpty()) {
      productionHolder.addProduction(List.of(new SequentialParser.Node(infoStart, pos.getNextLineOrEofOffset(), MarkdownTokenTypes.FENCE_LANG)));
    }
  }

  private static boolean hasClosingFence(LookaheadText.Position openingPosition, MarkdownConstraints constraints, String delimiter) {
    LookaheadText.Position position = openingPosition.nextLinePosition();
    while (position != null) {
      MarkdownConstraints nextLineConstraints = constraints.applyToNextLine(position);
      if (!MarkdownConstraints.extendsPrev(nextLineConstraints, constraints)) {
        return false;
      }
      if (isClosingFence(MarkdownConstraints.eatItselfFromString(nextLineConstraints, position.getCurrentLine()), delimiter)) {
        return true;
      }
      position = position.nextLinePosition();
    }
    return false;
  }

  private static boolean isClosingFence(CharSequence line, String delimiter) {
    int offset = 0;
    while (offset < line.length() && offset < 3 && line.charAt(offset) == ' ') {
      offset++;
    }
    int markerLength = 0;
    while (offset + markerLength < line.length() && line.charAt(offset + markerLength) == delimiter.charAt(0)) {
      markerLength++;
    }
    if (markerLength < delimiter.length()) {
      return false;
    }
    for (int i = offset + markerLength; i < line.length(); i++) {
      if (line.charAt(i) != ' ') {
        return false;
      }
    }
    return true;
  }

  /**
   * A code fence inside JSX without a closing fence ends before the closing tag of its element.
   */
  private static final class MdxRecoveringCodeFenceMarkerBlock implements MarkerBlock {
    private final MarkerBlock myDelegate;
    private final MdxJsxMarkdownConstraints myJsxConstraints;
    private final boolean myHasClosingFence;

    private MdxRecoveringCodeFenceMarkerBlock(MarkerBlock delegate, MdxJsxMarkdownConstraints jsxConstraints, boolean hasClosingFence) {
      myDelegate = delegate;
      myJsxConstraints = jsxConstraints;
      myHasClosingFence = hasClosingFence;
    }

    @Override
    public MarkerBlock.ProcessingResult processToken(LookaheadText.Position pos, MarkdownConstraints currentConstraints) {
      if (!myHasClosingFence && pos.getOffsetInCurrentLine() == -1) {
        switch (myJsxConstraints.closingBoundary(pos)) {
          case CURRENT, ANCESTOR -> {
            return MarkerBlock.ProcessingResult.DEFAULT;
          }
          case NONE, MISMATCHED -> {
          }
        }
      }
      return myDelegate.processToken(pos, currentConstraints);
    }

    @Override
    public int getNextInterestingOffset(LookaheadText.Position pos) {
      return myDelegate.getNextInterestingOffset(pos);
    }

    @Override
    public boolean isInterestingOffset(LookaheadText.Position pos) {
      return myDelegate.isInterestingOffset(pos);
    }

    @Override
    public boolean allowsSubBlocks() {
      return myDelegate.allowsSubBlocks();
    }

    @Override
    public MarkdownConstraints getBlockConstraints() {
      return myDelegate.getBlockConstraints();
    }

    @Override
    public boolean acceptAction(MarkerBlock.ClosingAction action) {
      return myDelegate.acceptAction(action);
    }
  }
}
