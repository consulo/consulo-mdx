package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxCancellableText;

/**
 * Finds code spans within paragraph segments supplied by the Markdown owner.
 */
public final class MdxMarkdownCodeSpanScanner {
  private MdxMarkdownCodeSpanScanner() {
  }

  public static final class Session {
    private final CharSequence text;
    private final int scanEnd;
    private final IntUnaryOperator paragraphEnd;
    private int cursor;
    private int lineStart;
    private int precedingBackslashes = 0;
    private int exposedEnd;
    private @Nullable Segment activeSegment;
    private int nextSpanIndex = 0;

    public Session(CharSequence source, int start) {
      this(source, start, source.length());
    }

    public Session(CharSequence source, int start, int scanEnd) {
      this(source, start, scanEnd, lineStart -> scanEnd);
    }

    /**
     * @param paragraphEnd maps the start of the line that opens a code span to the end of its Markdown paragraph segment
     */
    public Session(CharSequence source, int start, int scanEnd, IntUnaryOperator paragraphEnd) {
      this.text = mdxCancellableText(source);
      this.scanEnd = scanEnd;
      this.paragraphEnd = paragraphEnd;
      this.cursor = start;
      this.lineStart = start;
      this.exposedEnd = start;
    }

    public List<TextRange> advanceTo(int limit) {
      return advanceTo(limit, MdxTextRangeSet.EMPTY);
    }

    public List<TextRange> advanceTo(int limit, MdxOpaqueRangeLookup opaqueRanges) {
      mdxCancellableText(text);
      if (limit < exposedEnd || limit > scanEnd) {
        throw new IllegalArgumentException("Code-span scan limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
      }
      exposedEnd = limit;
      List<TextRange> result = new ArrayList<>();

      while (true) {
        Segment segment = activeSegment;
        if (segment != null) {
          while (nextSpanIndex < segment.spans().size() && segment.spans().get(nextSpanIndex).getStartOffset() < limit) {
            result.add(segment.spans().get(nextSpanIndex));
            nextSpanIndex++;
          }
          if (limit < segment.endOffset()) return result;

          cursor = segment.endOffset();
          lineStart = cursor;
          precedingBackslashes = 0;
          activeSegment = null;
          nextSpanIndex = 0;
          continue;
        }
        if (cursor >= limit) return result;

        Integer opaqueEnd = opaqueRanges.endOffsetContaining(cursor);
        if (opaqueEnd != null) {
          consumeThrough(Math.min(opaqueEnd, limit));
          continue;
        }

        char c = text.charAt(cursor);
        if (c != '`') {
          consume(c);
          continue;
        }

        if (precedingBackslashes % 2 != 0) {
          consumeBacktickRun();
          continue;
        }
        activeSegment = indexParagraphSegment(cursor, lineStart, opaqueRanges);
      }
    }

    private Segment indexParagraphSegment(int startOffset, int initialLineStart, MdxOpaqueRangeLookup opaqueRanges) {
      List<BacktickRun> runs = new ArrayList<>();
      int end = Math.min(paragraphEnd.applyAsInt(initialLineStart), scanEnd);
      if (end <= startOffset) throw new IllegalStateException("A Markdown segment must include its code-span opener");
      int offset = startOffset;
      int backslashes = 0;
      while (offset < end) {
        Integer opaqueEnd = opaqueRanges.endOffsetContaining(offset);
        if (opaqueEnd != null) {
          offset = Math.min(opaqueEnd, end);
          backslashes = 0;
          continue;
        }

        char c = text.charAt(offset);
        if (c == '\\') {
          backslashes++;
          offset++;
          continue;
        }
        if (c != '`') {
          backslashes = 0;
          offset++;
          continue;
        }

        int runStart = offset;
        while (offset < end && text.charAt(offset) == '`') {
          offset++;
        }
        runs.add(new BacktickRun(runStart, offset - runStart, backslashes % 2 == 0));
        backslashes = 0;
      }
      return new Segment(end, pairRuns(runs));
    }

    private static List<TextRange> pairRuns(List<BacktickRun> runs) {
      if (runs.isEmpty()) return List.of();
      int[] nextEqualLength = new int[runs.size()];
      Arrays.fill(nextEqualLength, -1);
      Map<Integer, Integer> latestByLength = new HashMap<>();
      for (int index = runs.size() - 1; index >= 0; index--) {
        Integer previous = latestByLength.put(runs.get(index).length(), index);
        nextEqualLength[index] = previous == null ? -1 : previous;
      }

      List<TextRange> result = new ArrayList<>();
      int index = 0;
      while (index < runs.size()) {
        int closingIndex = nextEqualLength[index];
        if (!runs.get(index).canOpen() || closingIndex == -1) {
          index++;
          continue;
        }
        BacktickRun closing = runs.get(closingIndex);
        result.add(new TextRange(runs.get(index).start(), closing.start() + closing.length()));
        index = closingIndex + 1;
      }
      return result;
    }

    private void consumeBacktickRun() {
      while (cursor < scanEnd && text.charAt(cursor) == '`') {
        cursor++;
      }
      precedingBackslashes = 0;
    }

    private void consumeThrough(int endOffset) {
      while (cursor < endOffset) {
        consume(text.charAt(cursor));
      }
    }

    private void consume(char c) {
      cursor++;
      if (c == '\n') {
        lineStart = cursor;
        precedingBackslashes = 0;
      }
      else if (c == '\\') {
        precedingBackslashes++;
      }
      else {
        precedingBackslashes = 0;
      }
    }

    private record Segment(int endOffset, List<TextRange> spans) {
    }

    private record BacktickRun(int start, int length, boolean canOpen) {
    }
  }
}
