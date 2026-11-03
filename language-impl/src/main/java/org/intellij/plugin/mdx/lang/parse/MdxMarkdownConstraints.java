package org.intellij.plugin.mdx.lang.parse;

import org.intellij.markdown.flavours.gfm.GFMConstraints;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Keeps the indentation skipped by JSX when GFM recognizes a new Markdown modifier.

 */
final class MdxMarkdownConstraints extends CommonMarkdownConstraints {
  static final MdxMarkdownConstraints BASE = new MdxMarkdownConstraints(new int[0], new char[0], new boolean[0], 0, false);

  private final int[] myIndents;
  private final boolean myHasCheckbox;

  private MdxMarkdownConstraints(int[] indents, char[] types, boolean[] isExplicit, int charsEaten, boolean hasCheckbox) {
    super(indents, types, isExplicit, charsEaten);
    myIndents = indents;
    myHasCheckbox = hasCheckbox;
  }

  @Override
  protected CommonMarkdownConstraints getBase() {
    return BASE;
  }

  @Override
  protected CommonMarkdownConstraints createNewConstraints(int[] indents, char[] types, boolean[] isExplicit, int charsEaten) {
    return new MdxMarkdownConstraints(indents, types, isExplicit, charsEaten, false);
  }

  @Override
  public @Nullable CommonMarkdownConstraints addModifierIfNeeded(LookaheadText.@Nullable Position pos) {
    if (!(asGfmConstraints().addModifierIfNeeded(pos) instanceof GFMConstraints modified)) {
      return null;
    }
    int skippedIndent = pos == null ? 0 : skippedIndent(pos);
    int[] indents = Arrays.copyOf(myIndents, myIndents.length + 1);
    indents[myIndents.length] = modified.getIndent() + skippedIndent;
    return new MdxMarkdownConstraints(indents, modified.getTypes(), modified.getIsExplicit(), modified.getCharsEaten(), modified.hasCheckbox());
  }

  GFMConstraints asGfmConstraints() {
    return new GFMConstraints(myIndents, getTypes(), getIsExplicit(), getCharsEaten(), myHasCheckbox);
  }

  MdxMarkdownConstraints applyToNextJsxLine(LookaheadText.@Nullable Position pos) {
    if (pos == null) {
      return BASE;
    }
    char[] types = getTypes();
    String line = pos.getCurrentLine();
    int[] nextIndents = new int[myIndents.length];
    boolean[] explicit = new boolean[myIndents.length];
    int offset = 0;
    int column = 0;
    int count = 0;
    int consumed = 0;
    for (int index = 0; index < types.length; index++) {
      if (types[index] == '>') {
        // JSX owns the whitespace before a quote, including indentation beyond three columns.
        while (offset < line.length() && (line.charAt(offset) == ' ' || line.charAt(offset) == '\t')) {
          column += line.charAt(offset++) == '\t' ? 4 - column % 4 : 1;
        }
        if (offset == line.length() || line.charAt(offset) != '>') {
          break;
        }
        offset++;
        column++;
        boolean hasSpace = offset == line.length() || line.charAt(offset) == ' ' || line.charAt(offset) == '\t';
        nextIndents[index] = column + (hasSpace ? 1 : 0);
        if (offset < line.length() && hasSpace) {
          column += line.charAt(offset++) == '\t' ? 4 - column % 4 : 1;
        }
        explicit[index] = true;
      }
      else {
        int previousIndent = index == 0 ? 0 : myIndents[index - 1];
        int nextPreviousIndent = index == 0 ? 0 : nextIndents[index - 1];
        int requiredIndent = nextPreviousIndent + myIndents[index] - previousIndent;
        while (column < requiredIndent && offset < line.length() && (line.charAt(offset) == ' ' || line.charAt(offset) == '\t')) {
          column += line.charAt(offset++) == '\t' ? 4 - column % 4 : 1;
        }
        if (column < requiredIndent && offset < line.length()) {
          break;
        }
        nextIndents[index] = requiredIndent;
      }
      consumed = offset;
      count++;
    }
    return new MdxMarkdownConstraints(Arrays.copyOf(nextIndents, count), Arrays.copyOf(types, count), Arrays.copyOf(explicit, count), consumed, false);
  }

  private int skippedIndent(LookaheadText.Position pos) {
    int end = pos.getOffsetInCurrentLine();
    int charsEaten = getCharsEaten();
    if (end <= charsEaten) {
      return 0;
    }
    String line = pos.getCurrentLine();
    int indent = getIndent();
    int column = indent;
    if (charsEaten > 0 && line.charAt(charsEaten - 1) == '\t') {
      column += (4 - column % 4) % 4;
    }
    for (int offset = charsEaten; offset < end; offset++) {
      char c = line.charAt(offset);
      if (c == ' ') {
        column += 1;
      }
      else if (c == '\t') {
        column += 4 - column % 4;
      }
      else {
        return 0;
      }
    }
    // GFM already accounts for the unused columns of the tab before its starting position.
    int tabRemainder = line.charAt(end - 1) == '\t' ? (4 - indent % 4) % 4 : 0;
    return column - indent - tabRemainder;
  }
}
