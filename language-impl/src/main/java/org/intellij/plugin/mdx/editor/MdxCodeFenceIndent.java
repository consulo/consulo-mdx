package org.intellij.plugin.mdx.editor;

import consulo.document.Document;

/**
 * The indentation a code fence carries.
 */
public final class MdxCodeFenceIndent {
  private MdxCodeFenceIndent() {
  }

  /**
   * Whatever the fence's opening {@code line} carries in front of its backticks. Every line of the fence carries the same,
   * so this is what gets stripped for the sandbox and put back afterwards.
   * <p>
   * Indentation — spaces or tabs — and the {@code >} markers of a blockquote around the fence carry over as they are. A
   * list bullet does not: repeating it would start a second item, so it stands in as blank filler of its own width,
   * which is exactly the item's content indent. {@code MarkdownCodeFenceUtils.getIndent} does it the same way.
   */
  public static String fenceIndent(Document document, int line) {
    CharSequence text = document.getImmutableCharSequence();
    int lineStart = document.getLineStartOffset(line);
    int lineEnd = document.getLineEndOffset(line);
    int end = lineStart;
    while (end < lineEnd && text.charAt(end) != '`' && text.charAt(end) != '~') {
      end++;
    }
    StringBuilder builder = new StringBuilder();
    for (int offset = lineStart; offset < end; offset++) {
      char c = text.charAt(offset);
      builder.append(c == ' ' || c == '\t' || c == '>' ? c : ' ');
    }
    return builder.toString();
  }
}
