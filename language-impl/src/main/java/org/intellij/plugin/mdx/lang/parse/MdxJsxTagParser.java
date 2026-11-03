package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence.mdxCancellableText;

/**
 * Incrementally parses one JSX tag without replaying an already observed prefix.
 */
final class MdxJsxTagParser {
  private final CharSequence text;
  final int start;
  private final int scanEnd;
  private final MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget;
  private int exposedEnd;
  private int cursor;
  private Phase phase = Phase.TAG_START;
  private boolean closing = false;
  private int nameStart = -1;
  private @Nullable String name;
  private @Nullable OpeningTagTail openingTail;
  private @Nullable Result terminalResult;

  MdxJsxTagParser(CharSequence source, int start) {
    this(source, start, source.length());
  }

  MdxJsxTagParser(CharSequence source, int start, int scanEnd) {
    this(source, start, scanEnd, new MdxExpressionBoundaryScanner.RecoveryBudget());
  }

  MdxJsxTagParser(CharSequence source, int start, int scanEnd, MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget) {
    this.text = mdxCancellableText(source);
    this.start = start;
    this.scanEnd = scanEnd;
    this.recoveryBudget = recoveryBudget;
    this.exposedEnd = start;
    this.cursor = start;
  }

  boolean isOpeningTag() {
    return !closing;
  }

  Result advanceTo(int limit) {
    mdxCancellableText(text);
    if (limit < exposedEnd || limit > scanEnd) {
      throw new IllegalArgumentException("JSX tag scan limit must advance from " + exposedEnd + " to at most " + scanEnd + ": " + limit);
    }
    exposedEnd = limit;
    Result terminal = terminalResult;
    if (terminal != null) return terminal;

    while (true) {
      switch (phase) {
        case TAG_START -> {
          if (cursor >= limit) return Result.PENDING;
          if (text.charAt(cursor) != '<') return finish(Result.INVALID);
          cursor++;
          phase = Phase.AFTER_TAG_START;
        }
        case AFTER_TAG_START -> {
          if (cursor >= limit) return Result.PENDING;
          if (text.charAt(cursor) == '/') {
            closing = true;
            cursor++;
          }
          phase = Phase.NAME;
        }
        case NAME -> {
          if (cursor >= limit) return Result.PENDING;
          if (nameStart == -1) {
            if (text.charAt(cursor) == '>') {
              cursor++;
              return finish(completeFragment());
            }
            if (!isNameStart(text.charAt(cursor))) return finish(Result.INVALID);
            nameStart = cursor;
            cursor++;
          }
          while (cursor < limit && isNamePart(text.charAt(cursor))) {
            cursor++;
          }
          if (cursor >= limit) return Result.PENDING;

          String tagName = text.subSequence(nameStart, cursor).toString();
          name = tagName;
          if (closing) {
            phase = Phase.CLOSING_TAG_END;
          }
          else {
            openingTail = new OpeningTagTail(text, start, tagName, cursor, scanEnd, recoveryBudget, List.of(), List.of(), false);
            phase = Phase.OPENING_TAG_TAIL;
          }
        }
        case CLOSING_TAG_END -> {
          while (cursor < limit && MdxEsmScanner.isWhitespace(text.charAt(cursor))) {
            cursor++;
          }
          if (cursor >= limit) return Result.PENDING;
          if (text.charAt(cursor) != '>') return finish(Result.INVALID);
          cursor++;
          return finish(new Result.Complete(tag(MdxJsxScanner.TagKind.CLOSING)));
        }
        case OPENING_TAG_TAIL -> {
          OpeningTagTail tail = openingTail;
          if (tail == null) throw new IllegalStateException("No opening tag tail");
          Result result = tail.advanceTo(limit);
          return result == Result.PENDING ? result : finish(result);
        }
      }
    }
  }

  private Result.Complete completeFragment() {
    MdxJsxScanner.TagKind kind = closing ? MdxJsxScanner.TagKind.CLOSING : MdxJsxScanner.TagKind.OPENING;
    return new Result.Complete(tag(kind));
  }

  private MdxJsxScanner.Tag tag(MdxJsxScanner.TagKind kind) {
    return new MdxJsxScanner.Tag(new TextRange(start, cursor), kind, name, List.of(), List.of());
  }

  private Result finish(Result result) {
    terminalResult = result;
    return result;
  }

  private enum Phase {
    TAG_START,
    AFTER_TAG_START,
    NAME,
    CLOSING_TAG_END,
    OPENING_TAG_TAIL,
  }

  private static final class OpeningTagTail {
    private final CharSequence text;
    private final int tagStart;
    private final String name;
    private final int scanEnd;
    private final MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget;
    private boolean selfClosing;
    private int cursor;
    private TailPhase phase = TailPhase.BETWEEN_ATTRIBUTES;
    private final List<TextRange> attributes;
    private final List<TextRange> expressions;
    private int attributeStart = -1;
    private char quote = '\u0000';
    private boolean escaped = false;
    private @Nullable ExpressionState activeExpression;
    private @Nullable OpeningTagTail delegatedTail;
    private @Nullable Result terminalResult;

    OpeningTagTail(CharSequence text,
                   int tagStart,
                   String name,
                   int initialOffset,
                   int scanEnd,
                   MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget,
                   List<TextRange> initialAttributes,
                   List<TextRange> initialExpressions,
                   boolean selfClosing) {
      this.text = text;
      this.tagStart = tagStart;
      this.name = name;
      this.scanEnd = scanEnd;
      this.recoveryBudget = recoveryBudget;
      this.selfClosing = selfClosing;
      this.cursor = initialOffset;
      this.attributes = new ArrayList<>(initialAttributes);
      this.expressions = new ArrayList<>(initialExpressions);
    }

    Result advanceTo(int limit) {
      Result terminal = terminalResult;
      if (terminal != null) return terminal;
      OpeningTagTail delegated = delegatedTail;
      if (delegated != null) return forward(delegated.advanceTo(limit));
      while (true) {
        switch (phase) {
          case BETWEEN_ATTRIBUTES -> {
            skipWhitespace(limit);
            if (cursor >= limit) return Result.PENDING;
            char c = text.charAt(cursor);
            if (c == '>') {
              cursor++;
              MdxJsxScanner.TagKind kind = selfClosing ? MdxJsxScanner.TagKind.SELF_CLOSING : MdxJsxScanner.TagKind.OPENING;
              return finish(new Result.Complete(tag(kind)));
            }
            else if (c == '/') {
              selfClosing = true;
              cursor++;
            }
            else if (c == '{') {
              beginExpression(null);
            }
            else {
              if (!isAttributeNameStart(c)) return finish(Result.INVALID);
              attributeStart = cursor;
              cursor++;
              phase = TailPhase.ATTRIBUTE_NAME;
            }
          }
          case ATTRIBUTE_NAME -> {
            while (cursor < limit && isAttributeNamePart(text.charAt(cursor))) {
              cursor++;
            }
            if (cursor >= limit) return Result.PENDING;
            phase = TailPhase.AFTER_ATTRIBUTE_NAME;
          }
          case AFTER_ATTRIBUTE_NAME -> {
            skipWhitespace(limit);
            if (cursor >= limit) return Result.PENDING;
            if (text.charAt(cursor) == '=') {
              cursor++;
              phase = TailPhase.BEFORE_ATTRIBUTE_VALUE;
            }
            else {
              finishAttribute(cursor);
              phase = TailPhase.BETWEEN_ATTRIBUTES;
            }
          }
          case BEFORE_ATTRIBUTE_VALUE -> {
            skipWhitespace(limit);
            if (cursor >= limit) return Result.PENDING;
            char c = text.charAt(cursor);
            if (c == '\'' || c == '"') {
              quote = c;
              escaped = false;
              cursor++;
              phase = TailPhase.QUOTED_ATTRIBUTE_VALUE;
            }
            else if (c == '{') {
              beginExpression(attributeStart);
            }
            else {
              phase = TailPhase.UNQUOTED_ATTRIBUTE_VALUE;
            }
          }
          case QUOTED_ATTRIBUTE_VALUE -> {
            if (!scanQuotedAttribute(limit)) return Result.PENDING;
          }
          case UNQUOTED_ATTRIBUTE_VALUE -> {
            while (cursor < limit &&
                   !MdxEsmScanner.isWhitespace(text.charAt(cursor)) &&
                   text.charAt(cursor) != '>' &&
                   text.charAt(cursor) != '/') {
              cursor++;
            }
            if (cursor >= limit) return Result.PENDING;
            finishAttribute(cursor);
            phase = TailPhase.BETWEEN_ATTRIBUTES;
          }
          case EXPRESSION -> {
            return advanceExpression(limit);
          }
        }
      }
    }

    private boolean scanQuotedAttribute(int limit) {
      while (cursor < limit) {
        char c = text.charAt(cursor);
        cursor++;
        if (escaped) {
          escaped = false;
        }
        else if (c == '\\') {
          escaped = true;
        }
        else if (c == quote) {
          finishAttribute(cursor);
          phase = TailPhase.BETWEEN_ATTRIBUTES;
          return true;
        }
      }
      return false;
    }

    private void beginExpression(@Nullable Integer attributeStart) {
      activeExpression = new ExpressionState(text, cursor, scanEnd, attributeStart, recoveryBudget);
      phase = TailPhase.EXPRESSION;
    }

    private Result advanceExpression(int limit) {
      ExpressionState expression = activeExpression;
      if (expression == null) throw new IllegalStateException("No active expression");
      MdxExpressionBoundaryScanner.Boundary boundary = expression.boundary.advanceToBoundary(limit);
      if (boundary.end() == -1) {
        expression.provisional = ProvisionalTail.NONE;
        return advanceRecoveryAlternatives(expression, limit);
      }

      if (boundary.stable()) {
        OpeningTagTail continuation = null;
        ProvisionalTail provisional = expression.provisional;
        if (provisional instanceof ProvisionalTail.Candidate candidate) {
          if (candidate.expressionEnd() == boundary.end()) continuation = candidate.tail();
        }
        else if (provisional instanceof ProvisionalTail.Rejected rejected) {
          if (rejected.expressionEnd() == boundary.end()) return finish(Result.INVALID);
        }
        if (continuation == null) {
          continuation = forkAfterExpression(expression, boundary.end());
        }
        delegatedTail = continuation;
        activeExpression = null;
        return forward(continuation.advanceTo(limit));
      }

      OpeningTagTail continuation = null;
      ProvisionalTail provisional = expression.provisional;
      if (provisional instanceof ProvisionalTail.Candidate candidate) {
        if (candidate.expressionEnd() == boundary.end()) continuation = candidate.tail();
      }
      else if (provisional instanceof ProvisionalTail.Rejected rejected) {
        if (rejected.expressionEnd() == boundary.end()) return Result.PENDING;
      }
      if (continuation == null) {
        continuation = forkAfterExpression(expression, boundary.end());
        expression.provisional = new ProvisionalTail.Candidate(boundary.end(), continuation);
      }
      Result result = continuation.advanceTo(limit);
      if (result == Result.PENDING) return result;
      if (result == Result.INVALID) {
        expression.provisional = new ProvisionalTail.Rejected(boundary.end());
        return Result.PENDING;
      }
      return finish(result);
    }

    private Result advanceRecoveryAlternatives(ExpressionState expression, int limit) {
      for (int candidateEnd : expression.recovery.advanceTo(limit)) {
        expression.alternatives.add(forkAfterExpression(expression, candidateEnd));
      }
      int alternativeIndex = 0;
      while (alternativeIndex < expression.alternatives.size()) {
        Result result = expression.alternatives.get(alternativeIndex).advanceTo(limit);
        if (result == Result.PENDING) {
          alternativeIndex++;
        }
        else if (result == Result.INVALID) {
          expression.alternatives.remove(alternativeIndex);
        }
        else {
          return finish(result);
        }
      }
      return Result.PENDING;
    }

    private OpeningTagTail forkAfterExpression(ExpressionState expression, int expressionEnd) {
      List<TextRange> recoveredAttributes = new ArrayList<>(attributes);
      Integer expressionAttributeStart = expression.attributeStart;
      if (expressionAttributeStart != null) {
        recoveredAttributes.add(new TextRange(expressionAttributeStart, expressionEnd));
      }
      List<TextRange> recoveredExpressions = new ArrayList<>(expressions);
      recoveredExpressions.add(new TextRange(expression.start, expressionEnd));
      return new OpeningTagTail(
        text,
        tagStart,
        name,
        expressionEnd,
        scanEnd,
        recoveryBudget,
        recoveredAttributes,
        recoveredExpressions,
        selfClosing
      );
    }

    private void finishAttribute(int end) {
      attributes.add(new TextRange(attributeStart, end));
      attributeStart = -1;
    }

    private void skipWhitespace(int limit) {
      while (cursor < limit && MdxEsmScanner.isWhitespace(text.charAt(cursor))) {
        cursor++;
      }
    }

    private MdxJsxScanner.Tag tag(MdxJsxScanner.TagKind kind) {
      return new MdxJsxScanner.Tag(new TextRange(tagStart, cursor), kind, name, List.copyOf(attributes), List.copyOf(expressions));
    }

    private Result finish(Result result) {
      terminalResult = result;
      return result;
    }

    private Result forward(Result result) {
      return result == Result.PENDING ? result : finish(result);
    }

    private static final class ExpressionState {
      final int start;
      final @Nullable Integer attributeStart;
      final MdxExpressionBoundaryScanner.Session boundary;
      final MdxExpressionBoundaryScanner.RecoveryCandidatesSession recovery;
      final List<OpeningTagTail> alternatives = new ArrayList<>();
      ProvisionalTail provisional = ProvisionalTail.NONE;

      ExpressionState(CharSequence text,
                      int start,
                      int scanEnd,
                      @Nullable Integer attributeStart,
                      MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget) {
        this.start = start;
        this.attributeStart = attributeStart;
        this.boundary = new MdxExpressionBoundaryScanner.Session(text, start, scanEnd);
        this.recovery = new MdxExpressionBoundaryScanner.RecoveryCandidatesSession(text, start, scanEnd, recoveryBudget);
      }
    }

    private sealed interface ProvisionalTail permits ProvisionalTail.None, ProvisionalTail.Candidate, ProvisionalTail.Rejected {
      ProvisionalTail NONE = None.INSTANCE;

      enum None implements ProvisionalTail {
        INSTANCE
      }

      record Candidate(int expressionEnd, OpeningTagTail tail) implements ProvisionalTail {
      }

      record Rejected(int expressionEnd) implements ProvisionalTail {
      }
    }

    private enum TailPhase {
      BETWEEN_ATTRIBUTES,
      ATTRIBUTE_NAME,
      AFTER_ATTRIBUTE_NAME,
      BEFORE_ATTRIBUTE_VALUE,
      QUOTED_ATTRIBUTE_VALUE,
      UNQUOTED_ATTRIBUTE_VALUE,
      EXPRESSION,
    }
  }

  sealed interface Result permits Result.Simple, Result.Complete {
    Result PENDING = Simple.PENDING;
    Result INVALID = Simple.INVALID;

    enum Simple implements Result {
      PENDING,
      INVALID
    }

    record Complete(MdxJsxScanner.Tag tag) implements Result {
    }
  }

  static MdxJsxScanner.@Nullable Tag parse(CharSequence text, int start, int limit) {
    return parse(text, start, limit, new MdxExpressionBoundaryScanner.RecoveryBudget());
  }

  static MdxJsxScanner.@Nullable Tag parse(CharSequence text,
                                           int start,
                                           int limit,
                                           MdxExpressionBoundaryScanner.RecoveryBudget recoveryBudget) {
    Result result = new MdxJsxTagParser(text, start, limit, recoveryBudget).advanceTo(limit);
    return result instanceof Result.Complete complete ? complete.tag() : null;
  }

  static boolean isNameStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  static boolean isNamePart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
  }

  static int scanQuoted(CharSequence text, int start, int limit, char quote) {
    int offset = start + 1;
    while (offset < limit) {
      char c = text.charAt(offset);
      if (c == '\\') {
        offset = Math.min(offset + 2, limit);
      }
      else if (c == quote) {
        return offset + 1;
      }
      else {
        offset++;
      }
    }
    return -1;
  }

  private static boolean isAttributeNameStart(char c) {
    return Character.isLetter(c) || c == '_' || c == ':' || c == '$';
  }

  private static boolean isAttributeNamePart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':' || c == '$';
  }
}
