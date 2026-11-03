package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.jspecify.annotations.Nullable;

import java.util.List;

final class MdxOpaqueBlockMarkerBlock extends MdxBlockMarkerBlock {
  private final Scanner myScanner;
  private int myScannedLimit;
  private @Nullable ScanResult myScannedResult;
  private ScanResult myRetainedResult;

  private MdxOpaqueBlockMarkerBlock(MarkdownConstraints constraints,
                                    ProductionHolder productionHolder,
                                    int blockStartOffset,
                                    CharSequence source,
                                    String initialText,
                                    Scanner scanner) {
    super(constraints, productionHolder, blockStartOffset, source, initialText);
    myScanner = scanner;
    myScannedLimit = blockStartOffset;
    scanTo(getCurrentEndOffset());
    myRetainedResult = scannedResult();
  }

  static MdxOpaqueBlockMarkerBlock esm(MarkdownConstraints constraints,
                                       ProductionHolder productionHolder,
                                       int blockStartOffset,
                                       CharSequence source,
                                       String initialText) {
    return new MdxOpaqueBlockMarkerBlock(constraints, productionHolder, blockStartOffset, source, initialText,
                                         new EsmScanner(source, blockStartOffset));
  }

  static MdxOpaqueBlockMarkerBlock expression(MarkdownConstraints constraints,
                                              ProductionHolder productionHolder,
                                              int blockStartOffset,
                                              CharSequence source,
                                              String initialText) {
    return new MdxOpaqueBlockMarkerBlock(constraints, productionHolder, blockStartOffset, source, initialText,
                                         new ExpressionScanner(blockStartOffset, source));
  }

  @Override
  protected MarkerBlock.ProcessingResult activeProcessingResult() {
    return MarkerBlock.ProcessingResult.CANCEL;
  }

  @Override
  protected boolean shouldAppendLine(LookaheadText.Position pos, int candidateEndOffset) {
    boolean terminated = isTerminated(candidateEndOffset);
    ScanResult scannedResult = scannedResult();
    Integer boundaryEndOffset = scannedResult.boundaryEndOffset();
    if (boundaryEndOffset != null && boundaryEndOffset <= pos.getOffset()) {
      // Recovery boundaries may need the candidate as lookahead. Retain the recovered block without consuming that line.
      myRetainedResult = scannedResult;
      return false;
    }
    return terminated ||
           MarkdownConstraints.extendsPrev(myConstraints.applyToNextLine(pos), myConstraints) ||
           boundaryEndOffset != null;
  }

  @Override
  protected void appendLine(int candidateEndOffset) {
    super.appendLine(candidateEndOffset);
    retainCurrentResult();
  }

  @Override
  protected boolean isTerminated(int candidateEndOffset) {
    scanTo(candidateEndOffset);
    return scannedResult().terminated();
  }

  @Override
  protected List<SequentialParser.Node> createNodes() {
    return myRetainedResult.createNodes();
  }

  private ScanResult scannedResult() {
    ScanResult result = myScannedResult;
    if (result == null) {
      throw new IllegalStateException("MDX block not scanned");
    }
    return result;
  }

  private void scanTo(int limit) {
    if (limit == myScannedLimit && myScannedResult != null) {
      return;
    }
    if (limit <= myScannedLimit && myScannedResult != null || limit < myScannedLimit) {
      throw new IllegalStateException("MDX block scan cannot move from " + myScannedLimit + " back to " + limit);
    }
    myScannedResult = myScanner.advanceTo(limit);
    myScannedLimit = limit;
  }

  private void retainCurrentResult() {
    if (myScannedLimit != getCurrentEndOffset()) {
      throw new IllegalStateException();
    }
    myRetainedResult = scannedResult();
  }

  @FunctionalInterface
  private interface Scanner {
    ScanResult advanceTo(int limit);
  }

  private sealed interface ScanResult permits EsmScanResult, ExpressionScanResult {
    boolean terminated();

    @Nullable
    Integer boundaryEndOffset();

    List<SequentialParser.Node> createNodes();
  }

  private static final class EsmScanner implements Scanner {
    private final CharSequence mySource;
    private final int myStart;
    private final MdxEsmScanner.Session mySession;

    EsmScanner(CharSequence source, int start) {
      mySource = source;
      myStart = start;
      mySession = new MdxEsmScanner.Session(source, start);
    }

    @Override
    public ScanResult advanceTo(int limit) {
      MdxEsmScanner.Block block = mySession.advanceTo(limit);
      boolean terminated = block != null && (block.terminated() || block.recoveryBoundary() || limit == mySource.length());
      return new EsmScanResult(new TextRange(myStart, limit), block, terminated);
    }
  }

  private record EsmScanResult(TextRange range, MdxEsmScanner.@Nullable Block block, boolean terminated) implements ScanResult {
    @Override
    public @Nullable Integer boundaryEndOffset() {
      return block == null ? null : block.range().getEndOffset();
    }

    @Override
    public List<SequentialParser.Node> createNodes() {
      if (block == null || !terminated) {
        return List.of(MdxTextRangeSet.toMarkdownNode(range, MdxMarkdownLibTokenTypes.EMBEDDED_JS_CONTENT));
      }
      return MdxBlockNodeFactory.createEsmNodes(block);
    }
  }

  private static final class ExpressionScanner implements Scanner {
    private final int myStart;
    private final MdxExpressionBoundaryScanner.Session mySession;

    ExpressionScanner(int start, CharSequence source) {
      myStart = start;
      mySession = new MdxExpressionBoundaryScanner.Session(source, start);
    }

    @Override
    public ScanResult advanceTo(int limit) {
      int expressionEnd = mySession.advanceTo(limit);
      return new ExpressionScanResult(new TextRange(myStart, limit), expressionEnd);
    }
  }

  private record ExpressionScanResult(TextRange range, int expressionEnd) implements ScanResult {
    @Override
    public boolean terminated() {
      Integer boundary = boundaryEndOffset();
      return boundary != null && boundary == range.getEndOffset();
    }

    @Override
    public @Nullable Integer boundaryEndOffset() {
      return expressionEnd != -1 ? expressionEnd : null;
    }

    @Override
    public List<SequentialParser.Node> createNodes() {
      Integer boundary = boundaryEndOffset();
      TextRange nodeRange = boundary != null ? new TextRange(range.getStartOffset(), boundary) : range;
      return List.of(
        MdxTextRangeSet.toMarkdownNode(nodeRange, MdxMarkdownLibTokenTypes.EMBEDDED_JS_CONTENT),
        MdxTextRangeSet.toMarkdownNode(nodeRange, MdxMarkdownLibElementTypes.MDX_EXPRESSION)
      );
    }
  }
}
