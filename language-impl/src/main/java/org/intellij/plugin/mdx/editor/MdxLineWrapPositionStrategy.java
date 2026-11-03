package org.intellij.plugin.mdx.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.EditorHighlighter;
import consulo.codeEditor.GenericLineWrapPositionStrategy;
import consulo.codeEditor.HighlighterIterator;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.LanguageLineWrapPositionStrategy;
import consulo.language.editor.highlight.LexerEditorHighlighter;
import consulo.project.Project;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Keeps wraps out of tables, headers and links. The editor highlighter is taken from an editor of the document; without
 * one a hard wrap is refused, a soft wrap allowed.
 */
@ExtensionImpl
public class MdxLineWrapPositionStrategy extends GenericLineWrapPositionStrategy implements LanguageLineWrapPositionStrategy {
  private static final int NEVER_WRAP = -1;
  private static final int NOT_FOUND = -1;
  private static final int MAX_SCAN_DISTANCE = 4096;

  private static final Set<IElementType> TABLE_TOKENS = Set.of(MarkdownElementTypes.TABLE_CELL, MarkdownTokenTypes.TABLE_SEPARATOR);

  private static final Set<IElementType> FORBIDDEN_INSIDE_LINK = Set.of(
    MarkdownTokenTypes.URL,
    MarkdownTokenTypes.AUTOLINK,
    MarkdownTokenTypes.GFM_AUTOLINK,
    MarkdownTokenTypes.EMAIL_AUTOLINK,
    MarkdownTokenTypes.LINK_TITLE
  );

  private static final Set<IElementType> LINK_BRACKETS = Set.of(
    MarkdownTokenTypes.LBRACKET,
    MarkdownTokenTypes.RBRACKET,
    MarkdownTokenTypes.LPAREN,
    MarkdownTokenTypes.RPAREN
  );

  private static final Set<IElementType> SETEXT_UNDERLINES = Set.of(
    MarkdownTokenTypes.SETEXT_1,
    MarkdownTokenTypes.SETEXT_2
  );

  public MdxLineWrapPositionStrategy() {
    // We should wrap after space, cause otherwise formatting will eat space once AutoWrapHandler made wrap
    addRule(new Rule(' ', WrapCondition.AFTER));
    addRule(new Rule('\t', WrapCondition.AFTER));

    // Punctuation.
    addRule(new Rule(',', WrapCondition.AFTER));
    addRule(new Rule('.', WrapCondition.AFTER));
    addRule(new Rule('!', WrapCondition.AFTER));
    addRule(new Rule('?', WrapCondition.AFTER));
    addRule(new Rule(';', WrapCondition.AFTER));

    // Brackets to wrap after.
    addRule(new Rule(')', WrapCondition.AFTER));
    addRule(new Rule(']', WrapCondition.AFTER));
    addRule(new Rule('}', WrapCondition.AFTER));

    // Brackets to wrap before
    addRule(new Rule('(', WrapCondition.BEFORE));
    addRule(new Rule('[', WrapCondition.BEFORE));
    addRule(new Rule('{', WrapCondition.BEFORE));
  }

  @Override
  public Language getLanguage() {
    return MdxLanguage.INSTANCE;
  }

  @Override
  public int calculateWrapPosition(Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean isSoftWrap) {
    int position = super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset,
                                               allowToBeyondMaxPreferredOffset, isSoftWrap);
    if (position < 0) {
      return position;
    }
    LexerEditorHighlighter highlighter = findHighlighter(document, project);
    if (highlighter == null) {
      return isSoftWrap ? position : -1;
    }
    Integer forbiddenStart = classifyForbidden(highlighter, document, position);
    if (forbiddenStart == null) {
      return position;
    }
    if (forbiddenStart == NEVER_WRAP || forbiddenStart <= startOffset) {
      return -1;
    }
    int retry = super.calculateWrapPosition(document, project, startOffset, forbiddenStart,
                                            Math.min(maxPreferredOffset, forbiddenStart),
                                            allowToBeyondMaxPreferredOffset, isSoftWrap);
    return retry > 0 ? retry : forbiddenStart;
  }

  private static @Nullable LexerEditorHighlighter findHighlighter(Document document, @Nullable Project project) {
    for (Editor editor : EditorFactory.getInstance().getEditors(document, project)) {
      if (editor instanceof EditorEx editorEx) {
        EditorHighlighter highlighter = editorEx.getHighlighter();
        if (highlighter instanceof LexerEditorHighlighter lexerHighlighter) {
          return lexerHighlighter;
        }
      }
    }
    return null;
  }

  /**
   * Returns the offset of the construct the wrap has to be moved in front of, NEVER_WRAP when no position on this
   * line is acceptable, or null when wrapping at position is fine.
   */
  private static @Nullable Integer classifyForbidden(LexerEditorHighlighter highlighter, Document document, int position) {
    HighlighterIterator iterator = highlighter.createIterator(position);
    if (iterator.atEnd()) {
      return null;
    }
    Object type = iterator.getTokenType();
    if (type == null) {
      return null;
    }

    // A table row is one CELL token per cell, links inside included, so every position in it is inside the table.
    if (TABLE_TOKENS.contains(type)) {
      return NEVER_WRAP;
    }

    int line = document.getLineNumber(position);
    if (isSetextHeaderContent(highlighter, document, line)) {
      return NEVER_WRAP;
    }

    // The ATX marker and the `[` that opens a link are both found by one walk back over the line. The marker has to be
    // looked for whatever the token at position is, since the content of a header is plain text.
    int linkStart = NOT_FOUND;
    int lineStart = Math.max(document.getLineStartOffset(line), position - MAX_SCAN_DISTANCE);
    HighlighterIterator tokens = highlighter.createIterator(position);
    while (!tokens.atEnd() && tokens.getStart() >= lineStart) {
      Object walked = tokens.getTokenType();
      if (walked == MarkdownTokenTypes.ATX_HEADER) {
        return NEVER_WRAP;
      }
      if (walked == MarkdownTokenTypes.LBRACKET && linkStart == NOT_FOUND) {
        linkStart = tokens.getStart();
      }
      if (tokens.getStart() == 0) {
        break;
      }
      tokens.retreat();
    }

    // Inside a destination, an autolink or a title the wrap belongs in front of the whole link.
    if (FORBIDDEN_INSIDE_LINK.contains(type)) {
      return linkStart == NOT_FOUND ? iterator.getStart() : linkStart;
    }
    // Ambiguous brackets/parens: forbidden only as part of a `[…](…)` sequence, and a wrap exactly at the opening
    // `[` is allowed, since that already wraps in front of the link.
    if (!LINK_BRACKETS.contains(type) || linkStart == NOT_FOUND ||
        (type == MarkdownTokenTypes.LBRACKET && iterator.getStart() == position)) {
      return null;
    }
    return hasClosingParen(highlighter, position, document.getLineEndOffset(line)) ? linkStart : null;
  }

  private static boolean isSetextHeaderContent(LexerEditorHighlighter highlighter, Document document, int line) {
    if (line + 1 >= document.getLineCount()) {
      return false;
    }
    HighlighterIterator iterator = highlighter.createIterator(document.getLineStartOffset(line + 1));
    return !iterator.atEnd() && SETEXT_UNDERLINES.contains(iterator.getTokenType());
  }

  private static boolean hasClosingParen(LexerEditorHighlighter highlighter, int position, int lineEnd) {
    int end = Math.min(lineEnd, position + MAX_SCAN_DISTANCE);
    HighlighterIterator tokens = highlighter.createIterator(position);
    while (!tokens.atEnd() && tokens.getStart() < end) {
      if (tokens.getTokenType() == MarkdownTokenTypes.RPAREN) {
        return true;
      }
      tokens.advance();
    }
    return false;
  }
}
