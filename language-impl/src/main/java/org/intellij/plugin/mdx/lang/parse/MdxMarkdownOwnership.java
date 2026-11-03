package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.markerblocks.impl.ParagraphMarkerBlock;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Shares the block parser's constraints and providers with provisional Markdown scans.
 */
final class MdxMarkdownOwnership {
  private final Supplier<List<MarkerBlockProvider<MarkerProcessor.StateInfo>>> myProviders;
  private final TreeMap<Integer, Line> myLines = new TreeMap<>();

  MdxMarkdownOwnership(Supplier<List<MarkerBlockProvider<MarkerProcessor.StateInfo>>> providers) {
    myProviders = providers;
  }

  @Nullable
  TextRange observeLine(LookaheadText.Position pos, MarkdownConstraints constraints) {
    MarkdownConstraints current = constraints;
    while (current.getCharsEaten() < pos.getCurrentLine().length()) {
      LookaheadText.Position content = pos.nextPosition(current.getCharsEaten() - pos.getOffsetInCurrentLine());
      if (content == null) {
        break;
      }
      MarkdownConstraints modified = current.addModifierIfNeeded(content);
      if (modified == null) {
        break;
      }
      current = modified;
    }
    int lineStart = pos.getOffset() - pos.getOffsetInCurrentLine();
    myLines.put(lineStart, new Line(pos, current));
    LookaheadText.Position content = contentPosition(pos, current);
    if (content == null) {
      return null;
    }
    for (MarkerBlockProvider<MarkerProcessor.StateInfo> provider : myProviders.get()) {
      if (provider instanceof MdxCodeFenceProvider && provider.interruptsParagraph(content, current)) {
        return new TextRange(lineStart, pos.getNextLineOrEofOffset());
      }
    }
    return null;
  }

  int paragraphEnd(int lineStart) {
    Map.Entry<Integer, Line> entry = Objects.requireNonNull(myLines.floorEntry(lineStart));
    Line line = entry.getValue();
    LookaheadText.Position content = contentPosition(line.position(), line.constraints());
    if (content == null || interruptsParagraph(content, line.constraints())) {
      return line.position().getNextLineOrEofOffset();
    }
    return paragraphEnd(line.position(), line.constraints(), line.position().getOriginalText().length());
  }

  private int paragraphEnd(LookaheadText.Position pos, MarkdownConstraints constraints, int limit) {
    ParagraphMarkerBlock paragraph = new ParagraphMarkerBlock(constraints, new ProductionHolder().mark(), this::interruptsParagraph);
    LookaheadText.Position next = pos.nextLinePosition();
    while (next != null && next.getOffset() < limit) {
      if (paragraph.processToken(next, constraints) != MarkerBlock.ProcessingResult.CANCEL) {
        return Math.min(next.getOffset() + 1, limit);
      }
      next = next.nextLinePosition();
    }
    return limit;
  }

  private boolean interruptsParagraph(LookaheadText.Position pos, MarkdownConstraints constraints) {
    for (MarkerBlockProvider<MarkerProcessor.StateInfo> provider : myProviders.get()) {
      if (provider.interruptsParagraph(pos, constraints)) {
        return true;
      }
    }
    return false;
  }

  private static LookaheadText.@Nullable Position contentPosition(LookaheadText.Position pos, MarkdownConstraints constraints) {
    if (constraints.getCharsEaten() >= pos.getCurrentLine().length()) {
      return null;
    }
    return pos.nextPosition(constraints.getCharsEaten() - pos.getOffsetInCurrentLine());
  }

  private record Line(LookaheadText.Position position, MarkdownConstraints constraints) {
  }
}
