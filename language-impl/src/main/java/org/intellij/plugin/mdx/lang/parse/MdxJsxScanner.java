package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxCancellableText;
import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxLineStart;
import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxSmallIndent;
import static org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner.charAtOrNull;

public final class MdxJsxScanner {
  private MdxJsxScanner() {
  }

  public enum TagKind {
    OPENING,
    CLOSING,
    SELF_CLOSING
  }

  public enum Termination {
    MATCHED,
    RECOVERED,
    UNTERMINATED,
  }

  public record Tag(
    TextRange range,
    TagKind kind,
    @Nullable String name,
    List<TextRange> attributes,
    List<TextRange> expressions
  ) {
  }

  public record Element(
    TextRange range,
    List<Tag> tags,
    List<TextRange> expressions,
    List<ElementRecord> elements,
    boolean incomplete,
    Termination termination
  ) {
  }

  /**
   * Indexes follow opening order. Records follow closing order.
   */
  public record ElementRecord(
    int index,
    @Nullable Integer parentIndex,
    TextRange range,
    @Nullable TextRange bodyRange
  ) {
  }

  public record ElementIdentity(@Nullable String name) {
  }

  public static final class Session {
    private final CharSequence text;
    private final int start;
    private final int scanEnd;
    private final MdxOpaqueRangeLookup baseOpaqueRanges;
    private final MdxMutableTextRangeSet addedOpaqueRanges = new MdxMutableTextRangeSet();
    private final MdxOpaqueRangeLookup allOpaqueRanges = this::endOffsetContaining;
    private MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget = new MdxExpressionBoundaryScanner.RecoveryBudget();
    private final RetainedAccumulator<Tag> tags = new RetainedAccumulator<>();
    private final RetainedAccumulator<TextRange> expressions = new RetainedAccumulator<>();
    private final RetainedAccumulator<ElementRecord> elements = new RetainedAccumulator<>();
    private final NestingState nesting = new NestingState();
    private int cursor;
    private int exposedEnd;
    private @Nullable MdxJsxTagParser activeTag;
    private @Nullable ExpressionSession activeExpression;
    private boolean invalidRoot;
    private @Nullable Termination termination;
    private int previousAdvanceStart;
    private @Nullable Snapshot previousAdvanceSnapshot;
    private @Nullable Element lastResult;
    private boolean resultIsCurrent = false;

    public Session(CharSequence source, int start) {
      this(source, start, source.length());
    }

    public Session(CharSequence source, int start, int scanEnd) {
      this(source, start, scanEnd, MdxTextRangeSet.EMPTY);
    }

    public Session(CharSequence source, int start, int scanEnd, MdxOpaqueRangeLookup baseOpaqueRanges) {
      this.text = mdxCancellableText(source);
      this.start = start;
      this.scanEnd = scanEnd;
      this.baseOpaqueRanges = baseOpaqueRanges;
      this.cursor = start;
      this.exposedEnd = start;
      this.invalidRoot = charAtOrNull(text, start) != '<';
      this.previousAdvanceStart = start;
      this.previousAdvanceSnapshot = snapshot();
    }

    public boolean isInJavaScript() {
      MdxJsxTagParser tag = activeTag;
      if (tag != null && tag.isOpeningTag()) return true;
      ExpressionSession expression = activeExpression;
      return expression != null && expression.session().advanceTo(exposedEnd) == -1;
    }

    public @Nullable Integer getEndOffset() {
      return termination == null ? null : cursor;
    }

    public @Nullable Element advanceTo(int limit) {
      mdxCancellableText(text);
      if (limit < exposedEnd || limit > scanEnd) {
        throw new IllegalArgumentException("JSX scan limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
      }
      if (limit == exposedEnd && resultIsCurrent) return lastResult;
      if (limit > exposedEnd) {
        commitPreviousAdvance();
        previousAdvanceStart = cursor;
        previousAdvanceSnapshot = snapshot();
        exposedEnd = limit;
      }
      Element result = scanTo(limit);
      lastResult = result;
      resultIsCurrent = true;
      return result;
    }

    private @Nullable Element scanTo(int limit) {
      while (cursor < limit && termination == null && !invalidRoot) {
        ExpressionSession expression = activeExpression;
        if (expression != null) {
          MdxExpressionBoundaryScanner.Boundary boundary = expression.session().advanceToBoundary(limit);
          if (boundary.end() == -1) return elementAt(limit);
          if (!boundary.stable()) return scanProvisionalExpression(expression, boundary.end(), limit);
          expressions.add(new TextRange(expression.start(), boundary.end()));
          cursor = boundary.end();
          activeExpression = null;
          continue;
        }

        MdxJsxTagParser tagSession = activeTag;
        if (tagSession != null) {
          MdxJsxTagParser.Result result = tagSession.advanceTo(limit);
          if (result == MdxJsxTagParser.Result.PENDING) {
            return elementAt(limit);
          }
          if (result instanceof MdxJsxTagParser.Result.Complete complete) {
            activeTag = null;
            cursor = complete.tag().range().getEndOffset();
            accept(complete.tag());
            continue;
          }
          // invalid
          activeTag = null;
          if (hasNoTags()) {
            invalidRoot = true;
            return null;
          }
          cursor = tagSession.start + 1;
          continue;
        }

        Integer opaqueEnd = allOpaqueRanges.endOffsetContaining(cursor);
        if (opaqueEnd != null) {
          cursor = Math.min(opaqueEnd, limit);
          continue;
        }
        char c = text.charAt(cursor);
        if (c == '{') {
          activeExpression = new ExpressionSession(cursor, new MdxExpressionBoundaryScanner.Session(text, cursor, scanEnd));
        }
        else if (c == '<') {
          activeTag = new MdxJsxTagParser(text, cursor, scanEnd, recoveryBudget);
        }
        else {
          cursor++;
        }
      }
      return elementAt(limit);
    }

    private @Nullable Element scanProvisionalExpression(ExpressionSession expression, int expressionEnd, int limit) {
      ProvisionalScanState state = new ProvisionalScanState(
        cursor,
        nesting.snapshot(),
        invalidRoot,
        termination,
        tags.checkpoint(),
        expressions.checkpoint(),
        elements.checkpoint()
      );
      MdxExpressionBoundaryScanner.RecoveryBudget durableRecoveryBudget = recoveryBudget;
      recoveryBudget = durableRecoveryBudget.copy();
      expressions.add(new TextRange(expression.start(), expressionEnd));
      cursor = expressionEnd;
      activeExpression = null;
      try {
        return scanTo(limit);
      }
      finally {
        recoveryBudget = durableRecoveryBudget;
        cursor = state.cursor();
        nesting.restore(state.nesting());
        invalidRoot = state.invalidRoot();
        termination = state.termination();
        tags.restore(state.tagCheckpoint());
        expressions.restore(state.expressionCheckpoint());
        elements.restore(state.elementCheckpoint());
        activeTag = null;
        activeExpression = expression;
      }
    }

    public void addOpaqueRanges(Collection<TextRange> ranges) {
      boolean hasNonEmpty = false;
      for (TextRange range : ranges) {
        if (!range.isEmpty()) {
          hasNonEmpty = true;
          break;
        }
      }
      if (!hasNonEmpty) return;
      addedOpaqueRanges.addAll(ranges);

      Snapshot snapshot = previousAdvanceSnapshot;
      if (snapshot != null) {
        for (TextRange range : ranges) {
          if (range.intersects(previousAdvanceStart, cursor)) {
            restore(snapshot);
            resultIsCurrent = false;
            break;
          }
        }
      }
    }

    public MdxOpaqueRangeLookup opaqueRangeLookup() {
      return allOpaqueRanges;
    }

    private void accept(Tag tag) {
      if (hasNoTags() && tag.kind() == TagKind.CLOSING) {
        invalidRoot = true;
        return;
      }
      tags.add(tag);
      expressions.addAll(tag.expressions());
      termination = nesting.accept(tag, elements::add);
    }

    private @Nullable Element elementAt(int limit) {
      if (invalidRoot || hasNoTags()) return null;
      Termination actualTermination = termination == null ? Termination.UNTERMINATED : termination;
      return new Element(
        new TextRange(start, termination == null ? limit : cursor),
        tags.snapshot(),
        expressions.snapshot(),
        elements.snapshot(),
        termination == null || nesting.isIncomplete(),
        actualTermination
      );
    }

    private boolean hasNoTags() {
      return tags.isEmpty();
    }

    private void commitPreviousAdvance() {
      tags.commit();
      expressions.commit();
      elements.commit();
    }

    private @Nullable Integer endOffsetContaining(int offset) {
      Integer baseEnd = baseOpaqueRanges.endOffsetContaining(offset);
      Integer addedEnd = addedOpaqueRanges.endOffsetContaining(offset);
      if (baseEnd == null) return addedEnd;
      if (addedEnd == null) return baseEnd;
      return Math.max(baseEnd, addedEnd);
    }

    private @Nullable Snapshot snapshot() {
      if (activeTag != null || activeExpression != null) return null;
      return new Snapshot(cursor, nesting.snapshot(), invalidRoot, termination);
    }

    private void restore(Snapshot snapshot) {
      cursor = snapshot.cursor();
      nesting.restore(snapshot.nesting());
      invalidRoot = snapshot.invalidRoot();
      termination = snapshot.termination();
      tags.restore(0);
      expressions.restore(0);
      elements.restore(0);
      activeTag = null;
      activeExpression = null;
    }

    private record Snapshot(
      int cursor,
      NestingSnapshot nesting,
      boolean invalidRoot,
      @Nullable Termination termination
    ) {
    }

    private record ProvisionalScanState(
      int cursor,
      NestingSnapshot nesting,
      boolean invalidRoot,
      @Nullable Termination termination,
      int tagCheckpoint,
      int expressionCheckpoint,
      int elementCheckpoint
    ) {
    }

    private record ExpressionSession(int start, MdxExpressionBoundaryScanner.Session session) {
    }
  }

  public static boolean isLineStartJsx(CharSequence text, int start) {
    CharSequence source = mdxCancellableText(text);
    int lineStart = mdxLineStart(source, start);
    int indent = mdxSmallIndent(source, lineStart, start);
    return indent != -1 &&
           lineStart + indent == start &&
           (MdxJsxTagParser.parse(source, start, source.length()) != null ||
            isIncompleteOpeningTagStart(source, start, source.length()));
  }

  public static @Nullable TextRange incompleteOpeningTagRange(CharSequence text, int start) {
    return incompleteOpeningTagRange(text, start, text.length());
  }

  /**
   * If a {@code <Name…} opening tag that has not yet been closed with {@code >} begins at {@code start} (see
   * {@link #isIncompleteOpeningTagStart}), returns its range up to the logical end of a "clean" prefix — a
   * {@code <Name} with optional, fully-formed attributes, ending at a line break or end of input. A dangling
   * <code>{…</code> expression or unterminated quote (e.g. the static {@code <Broken attr={"unterminated}}) yields
   * {@code null} so it keeps its existing outer-language parse rather than being reinterpreted as JSX.
   * Used to project a freshly-typed {@code <My} into the MdxJS layer so the platform JSX tag-name completion
   * runs while the tag is still unbalanced. WEB-78468.
   */
  public static @Nullable TextRange incompleteOpeningTagRange(CharSequence text, int start, int limit) {
    CharSequence source = mdxCancellableText(text);
    if (charAtOrNull(source, start) != '<') return null;
    if (start + 1 >= limit || source.charAt(start + 1) == '\n' || source.charAt(start + 1) == '\r') {
      return new TextRange(start, start + 1);
    }
    if (!isIncompleteOpeningTagStart(source, start, limit)) return null;
    int offset = start + 1;
    while (offset < limit && MdxJsxTagParser.isNamePart(source.charAt(offset))) {
      offset++;
    }
    while (offset < limit) {
      char c = source.charAt(offset);
      if (c == '\n') {
        return new TextRange(start, offset);
      }
      else if (c == '{') {
        int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(source, offset, limit);
        if (expressionEnd == -1) return null;
        offset = expressionEnd;
      }
      else if (c == '\'' || c == '"') {
        int quoteEnd = MdxJsxTagParser.scanQuoted(source, offset, limit, c);
        if (quoteEnd == -1) return null;
        offset = quoteEnd;
      }
      else {
        offset++;
      }
    }
    return new TextRange(start, limit);
  }

  public static @Nullable Element scanJsxElement(CharSequence text, int start) {
    return scanJsxElement(text, start, text.length());
  }

  public static @Nullable Element scanJsxElement(CharSequence text, int start, int limit) {
    return scanJsxElement(text, start, limit, MdxTextRangeSet.EMPTY);
  }

  public static @Nullable Element scanJsxElement(CharSequence text, int start, int limit, MdxTextRangeSet opaqueRanges) {
    return new Session(text, start, limit, opaqueRanges).advanceTo(limit);
  }

  public static @Nullable ElementIdentity openingElementIdentity(CharSequence text, int start) {
    return openingElementIdentity(text, start, text.length());
  }

  public static @Nullable ElementIdentity openingElementIdentity(CharSequence text, int start, int limit) {
    Tag tag = MdxJsxTagParser.parse(text, start, limit);
    if (tag == null) return null;
    return tag.kind() == TagKind.OPENING ? new ElementIdentity(tag.name()) : null;
  }

  public static @Nullable ElementIdentity closingElementIdentity(CharSequence text, int start) {
    return closingElementIdentity(text, start, text.length());
  }

  public static @Nullable ElementIdentity closingElementIdentity(CharSequence text, int start, int limit) {
    Tag tag = MdxJsxTagParser.parse(text, start, limit);
    if (tag == null) return null;
    return tag.kind() == TagKind.CLOSING ? new ElementIdentity(tag.name()) : null;
  }

  private static boolean isIncompleteOpeningTagStart(CharSequence text, int start, int limit) {
    if (charAtOrNull(text, start) != '<' || start + 1 >= limit || charAtOrNull(text, start + 1) == '/') return false;
    int offset = start + 1;
    if (charAtOrNull(text, offset) == '>') return false;
    if (!MdxJsxTagParser.isNameStart(charAtOrNull(text, offset))) return false;
    offset++;
    while (offset < limit && MdxJsxTagParser.isNamePart(text.charAt(offset))) {
      offset++;
    }
    // A `>`/`<` outside an expression breaks the tag; one inside an unterminated `{...}` attribute
    // (e.g. an `=>` arrow) does not, so expressions are skipped as a unit rather than scanned char-by-char.
    while (offset < limit) {
      char c = text.charAt(offset);
      if (c == '>' || c == '<') {
        return false;
      }
      else if (c == '\n') {
        return true;
      }
      else if (c == '{') {
        int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(text, offset, limit);
        if (expressionEnd == -1) return true;
        offset = expressionEnd;
      }
      else if (c == '\'' || c == '"') {
        int quoteEnd = MdxJsxTagParser.scanQuoted(text, offset, limit, c);
        if (quoteEnd == -1) return true;
        offset = quoteEnd;
      }
      else {
        offset++;
      }
    }
    return true;
  }

  private static final class NestingState {
    private @Nullable Frame topFrame;
    private int nextIndex = 0;
    private boolean incomplete = false;

    boolean isIncomplete() {
      return incomplete;
    }

    @Nullable
    Termination accept(Tag tag, Consumer<ElementRecord> addElement) {
      switch (tag.kind()) {
        case OPENING -> topFrame = new Frame(tag, nextIndex++, topFrame);
        case SELF_CLOSING -> {
          Frame parent = topFrame;
          addElement.accept(new ElementRecord(nextIndex++, parent == null ? null : parent.index, tag.range(), null));
        }
        case CLOSING -> {
          Frame matching = topFrame;
          while (matching != null && !Objects.equals(matching.opening.name(), tag.name())) {
            matching = matching.parent;
          }
          Frame skipped = topFrame;
          while (skipped != null && skipped != matching) {
            int end = skipped.parent == null ? tag.range().getEndOffset() : tag.range().getStartOffset();
            addElement.accept(skipped.closeAt(tag.range().getStartOffset(), end));
            topFrame = skipped.parent;
            incomplete = true;
            skipped = topFrame;
          }
          if (matching == null) return Termination.RECOVERED;
          addElement.accept(matching.closeAt(tag.range().getStartOffset(), tag.range().getEndOffset()));
          topFrame = matching.parent;
        }
      }
      return topFrame == null ? Termination.MATCHED : null;
    }

    NestingSnapshot snapshot() {
      return new NestingSnapshot(topFrame, nextIndex, incomplete);
    }

    void restore(NestingSnapshot snapshot) {
      topFrame = snapshot.topFrame();
      nextIndex = snapshot.nextIndex();
      incomplete = snapshot.incomplete();
    }
  }

  private static final class Frame {
    final Tag opening;
    final int index;
    final @Nullable Frame parent;

    Frame(Tag opening, int index, @Nullable Frame parent) {
      this.opening = opening;
      this.index = index;
      this.parent = parent;
    }

    ElementRecord closeAt(int bodyEnd, int end) {
      Frame parent = this.parent;
      return new ElementRecord(
        index,
        parent == null ? null : parent.index,
        new TextRange(opening.range().getStartOffset(), end),
        new TextRange(opening.range().getEndOffset(), bodyEnd)
      );
    }
  }

  private record NestingSnapshot(@Nullable Frame topFrame, int nextIndex, boolean incomplete) {
  }

  /**
   * Shares an append-only committed prefix and owns the current advance's immutable suffix. Later
   * commits and rollbacks therefore cannot change a retained result.
   */
  private static final class RetainedAccumulator<E> {
    private final List<E> committed = new ArrayList<>();
    private final List<E> provisional = new ArrayList<>();

    boolean isEmpty() {
      return committed.isEmpty() && provisional.isEmpty();
    }

    void add(E value) {
      provisional.add(value);
    }

    void addAll(Collection<E> values) {
      provisional.addAll(values);
    }

    int checkpoint() {
      return provisional.size();
    }

    void restore(int checkpoint) {
      if (checkpoint < provisional.size()) provisional.subList(checkpoint, provisional.size()).clear();
    }

    void commit() {
      committed.addAll(provisional);
      provisional.clear();
    }

    List<E> snapshot() {
      return new RetainedList<>(committed, committed.size(), new ArrayList<>(provisional));
    }
  }

  private static final class RetainedList<E> extends AbstractList<E> {
    private final List<E> committed;
    private final int committedSize;
    private final List<E> provisional;

    RetainedList(List<E> committed, int committedSize, List<E> provisional) {
      this.committed = committed;
      this.committedSize = committedSize;
      this.provisional = provisional;
    }

    @Override
    public int size() {
      return committedSize + provisional.size();
    }

    @Override
    public E get(int index) {
      if (index < 0 || index >= size()) throw new IndexOutOfBoundsException("index: " + index + ", size: " + size());
      return index < committedSize ? committed.get(index) : provisional.get(index - committedSize);
    }
  }
}
