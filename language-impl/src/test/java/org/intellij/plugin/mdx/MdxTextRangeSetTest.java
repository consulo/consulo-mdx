package org.intellij.plugin.mdx;

import consulo.document.util.TextRange;
import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.plugin.mdx.lang.parse.MdxTextRangeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(ConsuloApplicationLoader.class)
public class MdxTextRangeSetTest {
  @Test
  public void mergesUnorderedOverlappingAndAdjacentRanges() {
    List<TextRange> input = List.of(new TextRange(8, 12), new TextRange(2, 5), new TextRange(20, 20), new TextRange(5, 9));

    MdxTextRangeSet ranges = MdxTextRangeSet.of(input);
    assertEquals(List.of(new TextRange(2, 12)), ranges);
    assertNull(ranges.endOffsetContaining(1));
    assertEquals(12, ranges.endOffsetContaining(2));
    assertEquals(12, ranges.endOffsetContaining(11));
    assertNull(ranges.endOffsetContaining(12));
  }

  @Test
  public void subtractsOrderedRangesWithExclusionsSpanningGaps() {
    MdxTextRangeSet ranges = MdxTextRangeSet.of(List.of(new TextRange(0, 10), new TextRange(12, 20)));
    MdxTextRangeSet exclusions = MdxTextRangeSet.of(List.of(new TextRange(2, 4), new TextRange(6, 14), new TextRange(18, 22)));

    assertEquals(
      List.of(new TextRange(0, 2), new TextRange(4, 6), new TextRange(14, 18)),
      ranges.subtract(exclusions)
    );
  }
}
