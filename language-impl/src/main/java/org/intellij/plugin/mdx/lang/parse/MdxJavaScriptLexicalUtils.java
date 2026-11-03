package org.intellij.plugin.mdx.lang.parse;

final class MdxJavaScriptLexicalUtils {
  private MdxJavaScriptLexicalUtils() {
  }

  static boolean isMdxRegularExpressionTerminated(CharSequence text, int start, int end) {
    boolean escaped = false;
    boolean inCharacterClass = false;
    int offset = start + 1;
    while (offset < end) {
      char c = text.charAt(offset);
      if (escaped) {
        escaped = false;
      }
      else if (c == '\\') {
        escaped = true;
      }
      else if (c == '[') {
        inCharacterClass = true;
      }
      else if (c == ']') {
        inCharacterClass = false;
      }
      else if (c == '/' && !inCharacterClass) {
        return true;
      }
      offset++;
    }
    return false;
  }
}
