package org.intellij.plugin.mdx;

import java.util.ArrayList;
import java.util.List;

final class MdxScannerTestSupport {
  private MdxScannerTestSupport() {
  }

  static final class CountingCharSequence implements CharSequence {
    private final String text;
    private final int start;
    private final int end;
    private final long[] counter;

    CountingCharSequence(String text) {
      this(text, 0, text.length(), new long[1]);
    }

    private CountingCharSequence(String text, int start, int end, long[] counter) {
      this.text = text;
      this.start = start;
      this.end = end;
      this.counter = counter;
    }

    long getAccesses() {
      return counter[0];
    }

    @Override
    public int length() {
      return end - start;
    }

    @Override
    public char charAt(int index) {
      counter[0]++;
      return text.charAt(start + index);
    }

    @Override
    public CharSequence subSequence(int startIndex, int endIndex) {
      return new CountingCharSequence(text, start + startIndex, start + endIndex, counter);
    }

    @Override
    public String toString() {
      counter[0] += length();
      return text.substring(start, end);
    }
  }

  static final class CancellingCharSequence implements CharSequence {
    private final String text;
    private final int start;
    private final int end;
    private final CancellationCounter counter;

    CancellingCharSequence(String text, long cancellationThreshold, Runnable cancel) {
      this(text, 0, text.length(), new CancellationCounter(cancellationThreshold, cancel));
    }

    private CancellingCharSequence(String text, int start, int end, CancellationCounter counter) {
      this.text = text;
      this.start = start;
      this.end = end;
      this.counter = counter;
    }

    long getAccesses() {
      return counter.accesses;
    }

    @Override
    public int length() {
      return end - start;
    }

    @Override
    public char charAt(int index) {
      counter.recordAccesses(1);
      return text.charAt(start + index);
    }

    @Override
    public CharSequence subSequence(int startIndex, int endIndex) {
      return new CancellingCharSequence(text, start + startIndex, start + endIndex, counter);
    }

    @Override
    public String toString() {
      counter.recordAccesses(length());
      return text.substring(start, end);
    }

    private static final class CancellationCounter {
      private final long cancellationThreshold;
      private final Runnable cancel;
      private long accesses = 0;
      private boolean cancellationRequested = false;

      CancellationCounter(long cancellationThreshold, Runnable cancel) {
        this.cancellationThreshold = cancellationThreshold;
        this.cancel = cancel;
      }

      void recordAccesses(long count) {
        accesses += count;
        if (!cancellationRequested && accesses >= cancellationThreshold) {
          cancellationRequested = true;
          cancel.run();
        }
      }
    }
  }

  static final class PrefixGuardCharSequence implements CharSequence {
    private final String text;
    private final int start;
    private final int end;
    private final int[] visibleEnd;

    PrefixGuardCharSequence(String text) {
      this(text, 0, text.length(), new int[1]);
    }

    private PrefixGuardCharSequence(String text, int start, int end, int[] visibleEnd) {
      this.text = text;
      this.start = start;
      this.end = end;
      this.visibleEnd = visibleEnd;
    }

    @Override
    public int length() {
      return end - start;
    }

    void expose(int endOffset) {
      if (endOffset < visibleEnd[0] || endOffset > text.length()) {
        throw new IllegalArgumentException("Can't expose " + endOffset);
      }
      visibleEnd[0] = endOffset;
    }

    @Override
    public char charAt(int index) {
      int absoluteIndex = start + index;
      if (absoluteIndex >= visibleEnd[0]) {
        throw new IllegalStateException("Read source offset " + absoluteIndex + " beyond exposed prefix " + visibleEnd[0]);
      }
      return text.charAt(absoluteIndex);
    }

    @Override
    public CharSequence subSequence(int startIndex, int endIndex) {
      return new PrefixGuardCharSequence(text, start + startIndex, start + endIndex, visibleEnd);
    }

    @Override
    public String toString() {
      if (end > visibleEnd[0]) {
        throw new IllegalStateException("Read source through " + end + " beyond exposed prefix " + visibleEnd[0]);
      }
      return text.substring(start, end);
    }
  }

  static List<Integer> lineEnds(String text) {
    List<Integer> result = new ArrayList<>();
    int lineEnd = text.indexOf('\n');
    while (lineEnd != -1) {
      result.add(lineEnd + 1);
      lineEnd = text.indexOf('\n', lineEnd + 1);
    }
    if (result.isEmpty() || result.get(result.size() - 1) != text.length()) result.add(text.length());
    return result;
  }

  static String buildEsm(int lines) {
    StringBuilder builder = new StringBuilder();
    builder.append("export const values = [\n");
    for (int i = 0; i < lines; i++) {
      builder.append("  'value-").append(i).append("',\n");
    }
    builder.append(']');
    return builder.toString();
  }

  static String buildJsx(int elements) {
    StringBuilder builder = new StringBuilder();
    builder.append("<Box>\n");
    for (int i = 0; i < elements; i++) {
      builder.append("  <Item value={").append(i).append("} />\n");
    }
    builder.append("</Box>");
    return builder.toString();
  }
}
