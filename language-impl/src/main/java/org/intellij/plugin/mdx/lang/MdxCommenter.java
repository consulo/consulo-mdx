// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugin.mdx.lang;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.CommenterDataHolder;
import consulo.language.IndentedCommenter;
import consulo.language.Language;
import consulo.language.SelfManagingCommenter;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.CharArrayUtil;
import org.jspecify.annotations.Nullable;

/**
 * The commenter of MDX: {@code {/* *}/} in Markdown, JSX bodies and around JSX tags, {@code //} and {@code /* *}/} in
 * JavaScript.
 * <p>
 * The registered commenter of the MDX language uses the MDX context for tags; {@link MdxCommentProvider} creates
 * instances with the JavaScript one.
 */
@ExtensionImpl
public class MdxCommenter implements IndentedCommenter, SelfManagingCommenter<MdxCommenter.State> {
  private final MdxCommentContext myTagContext;

  public MdxCommenter() {
    this(MdxCommentContext.MDX);
  }

  MdxCommenter(MdxCommentContext tagContext) {
    myTagContext = tagContext;
  }

  @Override
  public Language getLanguage() {
    return MdxLanguage.INSTANCE;
  }

  @Override
  public @Nullable String getLineCommentPrefix() {
    return null;
  }

  @Override
  public String getBlockCommentPrefix() {
    return "{/*";
  }

  @Override
  public String getBlockCommentSuffix() {
    return "*/}";
  }

  @Override
  public @Nullable String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  public Boolean forceIndentedLineComment() {
    return true;
  }

  @RequiredReadAction
  @Override
  public State createLineCommentingState(int startLine, int endLine, Document document, PsiFile file) {
    State state = createState(document, file, new TextRange(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine)), false);
    return new State(state.context, state.lineSpace, state.blockSpace, state.blockComment, startLine != endLine);
  }

  @RequiredReadAction
  @Override
  public State createBlockCommentingState(int selectionStart, int selectionEnd, Document document, PsiFile file) {
    return createState(document, file, new TextRange(selectionStart, selectionEnd), true);
  }

  @RequiredReadAction
  private State createState(Document document, PsiFile file, TextRange range, boolean block) {
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    ContextAndRange existing = null;
    if (block) {
      existing = selectedBlockComment(document, range);
      if (existing == null) {
        existing = commentAtCaret(file, document, range);
      }
    }
    MdxCommentContext context = existing != null ? existing.context() : MdxCommentContext.mdxCommentContext(file, trimmedRange(document, range), myTagContext);
    Language language = context == MdxCommentContext.JAVASCRIPT ? JavaScriptLanguage.INSTANCE : MdxLanguage.INSTANCE;
    CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(file, language);
    // no space is added inside block comments
    return new State(context, settings.LINE_COMMENT_ADD_SPACE, false, existing == null ? null : existing.range(), false);
  }

  @Override
  public String getCommentPrefix(int line, Document document, State data) {
    return data.context.linePrefix != null ? data.context.linePrefix : data.context.blockPrefix;
  }

  @Override
  public boolean isLineCommented(int line, int offset, Document document, State data) {
    TextRange range = trimmedRange(document, new TextRange(offset, document.getLineEndOffset(line)));
    return data.skipEmptyLines && range.isEmpty() || lineCommentContext(line, offset, document, data) != null;
  }

  @Override
  public void commentLine(int line, int offset, Document document, State data) {
    int end = trimmedRange(document, new TextRange(offset, document.getLineEndOffset(line))).getEndOffset();
    if (data.skipEmptyLines && offset == end) {
      return;
    }
    String prefix = data.context.linePrefix;
    if (prefix != null) {
      document.insertString(offset, prefix + (data.lineSpace ? " " : ""));
    }
    else {
      if (lineCommentContext(line, offset, document, data) == MdxCommentContext.MDX) {
        return;
      }
      insertBlockComment(offset, end, document,
                         data.context.blockPrefix + (data.blockSpace ? " " : ""),
                         (data.blockSpace ? " " : "") + data.context.blockSuffix);
    }
  }

  @Override
  public void uncommentLine(int line, int offset, Document document, State data) {
    MdxCommentContext context = lineCommentContext(line, offset, document, data);
    if (context == null) {
      return;
    }
    String prefix = context.linePrefix;
    if (prefix != null) {
      int end = offset + prefix.length();
      CharSequence chars = document.getCharsSequence();
      if (data.lineSpace && end < chars.length() && chars.charAt(end) == ' ') {
        end++;
      }
      document.deleteString(offset, end);
    }
    else {
      int end = trimmedRange(document, new TextRange(offset, document.getLineEndOffset(line))).getEndOffset();
      removeBlockComment(document, new TextRange(offset, end), context, data.blockSpace);
    }
  }

  private static @Nullable MdxCommentContext lineCommentContext(int line, int offset, Document document, State data) {
    int end = trimmedRange(document, new TextRange(offset, document.getLineEndOffset(line))).getEndOffset();
    if (isWrapped(document, new TextRange(offset, end), MdxCommentContext.MDX)) {
      return MdxCommentContext.MDX;
    }
    if (data.context == MdxCommentContext.JAVASCRIPT && CharArrayUtil.regionMatches(document.getCharsSequence(), offset, "//")) {
      return MdxCommentContext.JAVASCRIPT;
    }
    return null;
  }

  @Override
  public String getBlockCommentPrefix(int selectionStart, Document document, State data) {
    return data.context.blockPrefix;
  }

  @Override
  public String getBlockCommentSuffix(int selectionEnd, Document document, State data) {
    return data.context.blockSuffix;
  }

  @Override
  public @Nullable TextRange getBlockCommentRange(int selectionStart, int selectionEnd, Document document, State data) {
    return data.blockComment;
  }

  @Override
  public TextRange insertBlockComment(int startOffset, int endOffset, Document document, State data) {
    return insertBlockComment(startOffset, endOffset, document,
                              data.context.blockPrefix + (data.blockSpace ? " " : ""),
                              (data.blockSpace ? " " : "") + data.context.blockSuffix);
  }

  @Override
  public void uncommentBlockComment(int startOffset, int endOffset, Document document, State data) {
    removeBlockComment(document, new TextRange(startOffset, endOffset), data.context, data.blockSpace);
  }

  /**
   * Wraps {@code [startOffset, endOffset)} into {@code prefix} and {@code suffix}.
   */
  private static TextRange insertBlockComment(int startOffset, int endOffset, Document document, String prefix, String suffix) {
    document.insertString(startOffset, prefix);
    document.insertString(endOffset + prefix.length(), suffix);
    return new TextRange(startOffset, endOffset + prefix.length() + suffix.length());
  }

  private static void removeBlockComment(Document document, TextRange range, MdxCommentContext context, boolean removeSpace) {
    CharSequence chars = document.getCharsSequence();
    int prefixEnd = range.getStartOffset() + context.blockPrefix.length();
    int suffixStart = range.getEndOffset() - context.blockSuffix.length();
    int contentStart = prefixEnd + (removeSpace && prefixEnd < suffixStart && chars.charAt(prefixEnd) == ' ' ? 1 : 0);
    int contentEnd = suffixStart - (removeSpace && contentStart < suffixStart && chars.charAt(suffixStart - 1) == ' ' ? 1 : 0);
    document.deleteString(contentEnd, range.getEndOffset());
    document.deleteString(range.getStartOffset(), contentStart);
  }

  private static @Nullable ContextAndRange selectedBlockComment(Document document, TextRange range) {
    TextRange trimmed = trimmedRange(document, range);
    for (MdxCommentContext context : MdxCommentContext.values()) {
      if (isWrapped(document, trimmed, context)) {
        return new ContextAndRange(context, trimmed);
      }
    }
    return null;
  }

  @RequiredReadAction
  private static @Nullable ContextAndRange commentAtCaret(PsiFile file, Document document, TextRange range) {
    PsiFile javascript = file.getViewProvider().getPsi(JavaScriptLanguage.INSTANCE);
    if (javascript == null) {
      return null;
    }
    PsiElement element = MdxCommentContext.commonElement(javascript, range);
    if (element == null) {
      return null;
    }
    PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
    if (comment == null) {
      return null;
    }
    TextRange commentRange = comment.getTextRange();
    if (!isWrapped(document, commentRange, MdxCommentContext.JAVASCRIPT)) {
      return null;
    }
    if (commentRange.getStartOffset() > 0 && commentRange.getEndOffset() < document.getTextLength()) {
      TextRange wrapper = new TextRange(commentRange.getStartOffset() - 1, commentRange.getEndOffset() + 1);
      if (isWrapped(document, wrapper, MdxCommentContext.MDX)) {
        return new ContextAndRange(MdxCommentContext.MDX, wrapper);
      }
    }
    return new ContextAndRange(MdxCommentContext.JAVASCRIPT, commentRange);
  }

  private static boolean isWrapped(Document document, TextRange range, MdxCommentContext context) {
    CharSequence chars = document.getCharsSequence();
    return range.getLength() >= context.blockPrefix.length() + context.blockSuffix.length() &&
           CharArrayUtil.regionMatches(chars, range.getStartOffset(), context.blockPrefix) &&
           CharArrayUtil.regionMatches(chars, range.getEndOffset() - context.blockSuffix.length(), context.blockSuffix);
  }

  private static TextRange trimmedRange(Document document, TextRange range) {
    CharSequence text = document.getCharsSequence();
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    while (start < end && Character.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return new TextRange(start, end);
  }

  private record ContextAndRange(MdxCommentContext context, TextRange range) {
  }

  public static final class State extends CommenterDataHolder {
    final MdxCommentContext context;
    final boolean lineSpace;
    final boolean blockSpace;
    final @Nullable TextRange blockComment;
    final boolean skipEmptyLines;

    State(MdxCommentContext context, boolean lineSpace, boolean blockSpace, @Nullable TextRange blockComment, boolean skipEmptyLines) {
      this.context = context;
      this.lineSpace = lineSpace;
      this.blockSpace = blockSpace;
      this.blockComment = blockComment;
      this.skipEmptyLines = skipEmptyLines;
    }
  }
}
