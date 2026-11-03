package org.intellij.plugin.mdx.lang.parse;

import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.impl.ParagraphMarkerBlock;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Keeps the standard paragraph marker while its JSX owner controls the output range.
 */
final class MdxFlowParagraph {
  private final int myStart;
  private final int myFirstLineEnd;
  private final MdxJsxBlockMarkerBlock myOwner;
  private final CharSequence mySource;
  private final Consumer<LookaheadText.Position> myOnContinuation;
  private final ProductionHolder myProductionHolder = new ProductionHolder();
  final ParagraphMarkerBlock marker;

  MdxFlowParagraph(int start,
                   int end,
                   MarkdownConstraints constraints,
                   MdxJsxBlockMarkerBlock owner,
                   CharSequence source,
                   BiFunction<LookaheadText.Position, MarkdownConstraints, Boolean> interruptsParagraph,
                   Consumer<LookaheadText.Position> onContinuation) {
    myStart = start;
    myFirstLineEnd = end;
    myOwner = owner;
    mySource = source;
    myOnContinuation = onContinuation;
    myProductionHolder.updatePosition(start);
    marker = new ParagraphMarkerBlock(constraints, myProductionHolder.mark(), (pos, nextConstraints) -> {
      if (myOwner.getBodyEnd() != null || interruptsParagraph.apply(pos, nextConstraints)) {
        return true;
      }
      continueParagraph(pos);
      return false;
    });
    advanceTo(end);
  }

  List<SequentialParser.Node> getProduction() {
    return myProductionHolder.getProduction();
  }

  private void continueParagraph(LookaheadText.Position pos) {
    myOnContinuation.accept(pos);
    advanceTo(pos.getNextLineOrEofOffset());
  }

  private void advanceTo(int end) {
    Integer bodyEnd = myOwner.getBodyEnd();
    int contentEnd = bodyEnd != null ? Math.min(bodyEnd, end) : end;
    boolean multiline = contentEnd > myFirstLineEnd;
    while (contentEnd > myStart && (multiline
                                    ? mySource.charAt(contentEnd - 1) == '\n' || mySource.charAt(contentEnd - 1) == '\r'
                                    : Character.isWhitespace(mySource.charAt(contentEnd - 1)))) {
      contentEnd--;
    }
    myProductionHolder.updatePosition(contentEnd);
  }
}
