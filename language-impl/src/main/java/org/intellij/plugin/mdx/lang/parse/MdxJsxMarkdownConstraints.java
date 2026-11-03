package org.intellij.plugin.mdx.lang.parse;

import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

final class MdxJsxMarkdownConstraints implements MarkdownConstraints {
  private final MarkdownConstraints myParent;
  private final int myBlockStartIndent;
  private final MdxJsxScanner.@Nullable ElementIdentity myElementIdentity;
  private final Supplier<@Nullable Integer> myElementEnd;
  private final boolean myOpaqueBlock;
  private final int myCharsEaten;

  MdxJsxMarkdownConstraints(MarkdownConstraints parent,
                            int blockStartIndent,
                            MdxJsxScanner.@Nullable ElementIdentity elementIdentity,
                            Supplier<@Nullable Integer> elementEnd) {
    this(parent, blockStartIndent, elementIdentity, elementEnd, false, blockStartIndent);
  }

  private MdxJsxMarkdownConstraints(MarkdownConstraints parent,
                                    int blockStartIndent,
                                    MdxJsxScanner.@Nullable ElementIdentity elementIdentity,
                                    Supplier<@Nullable Integer> elementEnd,
                                    boolean opaqueBlock,
                                    int charsEaten) {
    myParent = parent;
    myBlockStartIndent = blockStartIndent;
    myElementIdentity = elementIdentity;
    myElementEnd = elementEnd;
    myOpaqueBlock = opaqueBlock;
    myCharsEaten = charsEaten;
  }

  @Override
  public int getIndent() {
    return Math.max(myBlockStartIndent, myParent.getIndent());
  }

  @Override
  public char[] getTypes() {
    return myParent.getTypes();
  }

  @Override
  public boolean[] getIsExplicit() {
    return myParent.getIsExplicit();
  }

  @Override
  public int getCharsEaten() {
    return myCharsEaten;
  }

  @Override
  public boolean startsWith(MarkdownConstraints other) {
    if (other == this) {
      return true;
    }
    if (other instanceof MdxJsxMarkdownConstraints otherJsx) {
      return myParent.startsWith(otherJsx.myParent) &&
             myBlockStartIndent == otherJsx.myBlockStartIndent &&
             Objects.equals(myElementIdentity, otherJsx.myElementIdentity);
    }
    return myParent.startsWith(other);
  }

  @Override
  public boolean containsListMarkers(int upToIndex) {
    return myParent.containsListMarkers(upToIndex);
  }

  @Override
  public @Nullable MarkdownConstraints addModifierIfNeeded(LookaheadText.@Nullable Position pos) {
    // Delegate modifier recognition, but retain every JSX wrapper. Returning the modified parent
    // directly loses the closing boundary of nested JSX and lets a list item consume ancestor tags.
    MarkdownConstraints modifiedParent = myParent.addModifierIfNeeded(pos);
    if (modifiedParent == null) {
      return null;
    }
    return copy(modifiedParent, myOpaqueBlock, modifiedParent.getCharsEaten());
  }

  @Override
  public MarkdownConstraints applyToNextLine(LookaheadText.@Nullable Position pos) {
    Integer elementEnd = pos == null ? null : myElementEnd.get();
    if (pos == null || (elementEnd != null && elementEnd <= pos.getOffset())) {
      return myParent.applyToNextLine(pos);
    }
    MarkdownConstraints modifiedParent = myParent instanceof MdxMarkdownConstraints mdx ? mdx.applyToNextJsxLine(pos) : myParent.applyToNextLine(pos);
    if (myOpaqueBlock) {
      int contentOffset = firstNonWhitespaceOffset(pos.getCurrentLine(), modifiedParent.getCharsEaten());
      return copy(modifiedParent, myOpaqueBlock, contentOffset == -1 ? pos.getCurrentLine().length() : contentOffset);
    }
    int nonWhitespaceOffset = firstNonWhitespaceOffset(pos.getCurrentLine(), modifiedParent.getCharsEaten());
    if (nonWhitespaceOffset == -1) {
      return copy(modifiedParent, myOpaqueBlock, pos.getCurrentLine().length());
    }
    if (nonWhitespaceOffset <= myBlockStartIndent && closingBoundary(pos) != MdxJsxClosingBoundary.NONE) {
      return modifiedParent;
    }
    if (nonWhitespaceOffset < myBlockStartIndent) {
      return modifiedParent;
    }
    return copy(modifiedParent, myOpaqueBlock, nonWhitespaceOffset);
  }

  MdxJsxMarkdownConstraints asOpaqueBlockConstraints() {
    MarkdownConstraints opaqueParent = myParent instanceof MdxJsxMarkdownConstraints jsxParent ? jsxParent.asOpaqueBlockConstraints() : myParent;
    return copy(opaqueParent, true, myCharsEaten);
  }

  MdxJsxClosingBoundary closingBoundary(LookaheadText.Position pos) {
    MarkdownConstraints markdownConstraints = markdownConstraints().applyToNextLine(pos);
    int tagOffset = firstNonWhitespaceOffset(pos.getCurrentLine(), markdownConstraints.getCharsEaten());
    if (tagOffset == -1) {
      return MdxJsxClosingBoundary.NONE;
    }
    MdxJsxScanner.ElementIdentity closingIdentity =
      MdxJsxScanner.closingElementIdentity(pos.getCurrentLine(), tagOffset, pos.getCurrentLine().length());
    if (closingIdentity == null) {
      return MdxJsxClosingBoundary.NONE;
    }

    MarkdownConstraints constraints = this;
    boolean current = true;
    while (constraints instanceof MdxJsxMarkdownConstraints jsxConstraints) {
      if (Objects.equals(jsxConstraints.myElementIdentity, closingIdentity)) {
        return current ? MdxJsxClosingBoundary.CURRENT : MdxJsxClosingBoundary.ANCESTOR;
      }
      constraints = jsxConstraints.myParent;
      current = false;
    }
    return MdxJsxClosingBoundary.MISMATCHED;
  }

  MarkdownConstraints markdownConstraints() {
    MarkdownConstraints constraints = myParent;
    while (constraints instanceof MdxJsxMarkdownConstraints jsxConstraints) {
      constraints = jsxConstraints.myParent;
    }
    return constraints;
  }

  private MdxJsxMarkdownConstraints copy(MarkdownConstraints parent, boolean opaqueBlock, int charsEaten) {
    return new MdxJsxMarkdownConstraints(parent, myBlockStartIndent, myElementIdentity, myElementEnd, opaqueBlock, charsEaten);
  }

  private static int firstNonWhitespaceOffset(CharSequence line, int start) {
    int offset = Math.max(0, Math.min(start, line.length()));
    while (offset < line.length()) {
      if (line.charAt(offset) != ' ' && line.charAt(offset) != '\t') {
        return offset;
      }
      offset++;
    }
    return -1;
  }
}
