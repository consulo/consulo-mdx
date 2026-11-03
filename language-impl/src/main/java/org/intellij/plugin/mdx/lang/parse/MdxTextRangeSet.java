package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import org.intellij.markdown.parser.sequentialparsers.IntRange;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * An immutable, normalized set of half-open source ranges.
 */
public final class MdxTextRangeSet extends AbstractList<TextRange> implements MdxOpaqueRangeLookup {
  public static final MdxTextRangeSet EMPTY = new MdxTextRangeSet(List.of());

  private final List<TextRange> ranges;

  private MdxTextRangeSet(List<TextRange> ranges) {
    this.ranges = ranges;
  }

  @Override
  public int size() {
    return ranges.size();
  }

  @Override
  public TextRange get(int index) {
    return ranges.get(index);
  }

  @Override
  public @Nullable Integer endOffsetContaining(int offset) {
    int low = 0;
    int high = ranges.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      TextRange range = ranges.get(middle);
      if (offset < range.getStartOffset()) {
        high = middle - 1;
      }
      else if (offset >= range.getEndOffset()) {
        low = middle + 1;
      }
      else {
        return range.getEndOffset();
      }
    }
    return null;
  }

  public MdxTextRangeSet subtract(MdxTextRangeSet exclusions) {
    if (isEmpty() || exclusions.isEmpty()) return this;

    List<TextRange> result = new ArrayList<>();
    int exclusionIndex = 0;
    for (TextRange range : ranges) {
      while (exclusionIndex < exclusions.size() && exclusions.get(exclusionIndex).getEndOffset() <= range.getStartOffset()) {
        exclusionIndex++;
      }

      int offset = range.getStartOffset();
      int index = exclusionIndex;
      while (index < exclusions.size() && exclusions.get(index).getStartOffset() < range.getEndOffset()) {
        TextRange exclusion = exclusions.get(index);
        int exclusionStart = Math.max(range.getStartOffset(), exclusion.getStartOffset());
        if (offset < exclusionStart) {
          result.add(new TextRange(offset, exclusionStart));
        }
        offset = Math.max(offset, exclusion.getEndOffset());
        if (exclusion.getEndOffset() <= range.getEndOffset()) {
          index++;
        }
        else {
          break;
        }
      }
      if (offset < range.getEndOffset()) {
        result.add(new TextRange(offset, range.getEndOffset()));
      }
      exclusionIndex = index;
    }
    return fromNormalized(result);
  }

  public static MdxTextRangeSet of(Collection<TextRange> ranges) {
    List<TextRange> sorted = new ArrayList<>(ranges.size());
    for (TextRange range : ranges) {
      if (!range.isEmpty()) sorted.add(range);
    }
    sorted.sort(Comparator.comparingInt(TextRange::getStartOffset).thenComparingInt(TextRange::getEndOffset));
    if (sorted.isEmpty()) return EMPTY;

    List<TextRange> normalized = new ArrayList<>();
    TextRange current = sorted.get(0);
    for (int i = 1; i < sorted.size(); i++) {
      TextRange range = sorted.get(i);
      if (range.getStartOffset() <= current.getEndOffset()) {
        current = new TextRange(current.getStartOffset(), Math.max(current.getEndOffset(), range.getEndOffset()));
      }
      else {
        normalized.add(current);
        current = range;
      }
    }
    normalized.add(current);
    return fromNormalized(normalized);
  }

  private static MdxTextRangeSet fromNormalized(List<TextRange> ranges) {
    return ranges.isEmpty() ? EMPTY : new MdxTextRangeSet(List.copyOf(ranges));
  }

  /**
   * The text range of a markdown engine range, used as {@code [first, last)}.
   */
  public static TextRange toTextRange(IntRange range) {
    return new TextRange(range.getFirst(), range.getLast());
  }

  /**
   * The markdown engine range of a text range ({@code startOffset..endOffset}).
   */
  public static IntRange toMarkdownRange(TextRange range) {
    return new IntRange(range.getStartOffset(), range.getEndOffset());
  }

  /**
   * A production node of {@code type} over {@code range}: {@link SequentialParser.Node} takes the two bounds.
   */
  public static SequentialParser.Node toMarkdownNode(TextRange range, IElementType type) {
    return new SequentialParser.Node(range.getStartOffset(), range.getEndOffset(), type);
  }
}
