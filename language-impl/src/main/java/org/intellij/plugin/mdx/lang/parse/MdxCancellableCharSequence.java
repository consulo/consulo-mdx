package org.intellij.plugin.mdx.lang.parse;

import consulo.application.progress.ProgressManager;
import consulo.util.lang.StringUtil;

/**
 * Makes every MDX source scan cancellable, including work performed inside platform lexers.

 */
public final class MdxCancellableCharSequence {
  private MdxCancellableCharSequence() {
  }

  public static CharSequence mdxCancellableText(CharSequence text) {
    ProgressManager.checkCanceled();
    return text instanceof Bombed ? text : new Bombed(text);
  }

  public static int mdxLineStart(CharSequence text, int offset) {
    CharSequence source = mdxCancellableText(text);
    int lineStart = Math.min(offset, source.length());
    while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') {
      lineStart--;
    }
    return lineStart;
  }

  public static int mdxSmallIndent(CharSequence text, int lineStart, int offset) {
    CharSequence source = mdxCancellableText(text);
    int indent = 0;
    while (lineStart + indent < offset && source.charAt(lineStart + indent) == ' ') {
      indent++;
      if (indent > 3) return -1;
    }
    return indent;
  }

  private static final class Bombed extends StringUtil.BombedCharSequence {
    Bombed(CharSequence text) {
      super(text);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
    }

    @Override
    public String toString() {
      int size = length();
      StringBuilder builder = new StringBuilder(size);
      for (int index = 0; index < size; index++) {
        builder.append(charAt(index));
      }
      return builder.toString();
    }
  }
}
