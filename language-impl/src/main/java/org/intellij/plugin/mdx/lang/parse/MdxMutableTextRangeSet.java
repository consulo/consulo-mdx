package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

final class MdxMutableTextRangeSet implements MdxOpaqueRangeLookup {
  private final TreeMap<Integer, Integer> ranges = new TreeMap<>();

  @Override
  public @Nullable Integer endOffsetContaining(int offset) {
    Map.Entry<Integer, Integer> range = ranges.floorEntry(offset);
    if (range == null) return null;
    int end = range.getValue();
    return offset < end ? end : null;
  }

  public void addAll(Collection<TextRange> newRanges) {
    for (TextRange range : newRanges) {
      add(range);
    }
  }

  private void add(TextRange range) {
    if (range.isEmpty()) return;
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    Map.Entry<Integer, Integer> floor = ranges.floorEntry(start);
    if (floor != null && floor.getValue() >= start) {
      start = floor.getKey();
      end = Math.max(end, floor.getValue());
      ranges.remove(floor.getKey());
    }
    Map.Entry<Integer, Integer> next = ranges.ceilingEntry(start);
    while (next != null && next.getKey() <= end) {
      end = Math.max(end, next.getValue());
      ranges.remove(next.getKey());
      next = ranges.ceilingEntry(start);
    }
    ranges.put(start, end);
  }
}
