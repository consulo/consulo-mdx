package org.intellij.plugin.mdx.js;

import com.intellij.xml.util.HtmlUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.xml.application.options.editor.XmlEditorOptions;
import org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner;
import org.intellij.plugin.mdx.lang.template.MdxFileViewProvider;
import org.jspecify.annotations.Nullable;

/**
 * Auto-closes JSX/HTML tags (and the {@code <>} fragment) when {@code >} is typed in an MDX file. Neither
 * the JavaScript typed handler (gates on the base file being a JS dialect, which MDX is not) nor {@code XmlGtTypedHandler}
 * (gates on {@code XMLLanguage}, but JSX tag leaves report the MdxJS dialect) fires here, so this bridges the gap.
 * <p>
 * Follows {@link XmlEditorOptions#isAutomaticallyInsertClosingTag()}; the {@code MdxJS} version always has JSX.
 */
@ExtensionImpl(id = "mdxTagClosing", order = "after xmlGt")
public class MdxTagClosingTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result beforeCharTyped(char c, Project project, Editor editor, PsiFile file, FileType fileType) {
    if (c != '>') {
      return Result.CONTINUE;
    }
    if (!XmlEditorOptions.getInstance().isAutomaticallyInsertClosingTag()) {
      return Result.CONTINUE;
    }

    FileViewProvider viewProvider = file.getViewProvider();
    if (!(viewProvider instanceof MdxFileViewProvider)) {
      return Result.CONTINUE;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile mdxJs = viewProvider.getPsi(JavaScriptLanguage.INSTANCE);
    if (mdxJs == null) {
      return Result.CONTINUE;
    }

    int offset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getCharsSequence();
    String closingTag = closingTagToInsert(text, offset);
    if (closingTag == null) {
      return Result.CONTINUE;
    }

    // Don't insert twice if an earlier handler in the chain already added it.
    if (text.subSequence(offset, text.length()).toString().startsWith(closingTag)) {
      return Result.DEFAULT;
    }

    // Insert without moving the caret, then DEFAULT so the typed `>` still lands normally between it and
    // the close, but no later delegate in the chain also tries to insert its own closing tag for the same keystroke.
    EditorModificationUtil.insertStringAtCaret(editor, closingTag, false, 0);
    return Result.DEFAULT;
  }

  static @Nullable String closingTagToInsert(CharSequence text, int caretOffset) {
    if (caretOffset <= 0 || caretOffset > text.length()) {
      return null;
    }
    Integer tagStart = findTagStart(text, caretOffset);
    if (tagStart == null) {
      return null;
    }
    if (charAt(text, tagStart + 1) == '/') {
      return null;
    }
    if (tagStart == caretOffset - 1) {
      return "</>";
    }

    int nameEnd = tagStart + 1;
    if (!isJsxNameStart(charAt(text, nameEnd))) {
      return null;
    }
    nameEnd++;
    while (nameEnd < caretOffset && isJsxNamePart(text.charAt(nameEnd))) {
      nameEnd++;
    }
    String name = text.subSequence(tagStart + 1, nameEnd).toString();
    if (caretInsideTagExpressionOrQuote(text, nameEnd, caretOffset)) {
      return null;
    }
    if (charAt(text, caretOffset - 1) == '/') {
      return null;
    }
    // void HTML elements, case-sensitive
    if (HtmlUtil.isSingleHtmlTagL(name)) {
      return null;
    }
    return "</" + name + ">";
  }

  /**
   * Walks back over complete expression and quoted attribute values so their internal tag-like
   * characters do not hide the opening {@code <}.
   */
  private static @Nullable Integer findTagStart(CharSequence text, int caretOffset) {
    int index = caretOffset - 1;
    while (index >= 0) {
      switch (text.charAt(index)) {
        case '}' -> {
          Integer start = matchingExpressionStart(text, index);
          if (start == null) {
            return null;
          }
          index = start - 1;
        }
        case '\'', '"', '`' -> {
          Integer start = matchingQuoteStart(text, index);
          if (start == null) {
            return null;
          }
          index = start - 1;
        }
        case '>', '\n' -> {
          return null;
        }
        case '<' -> {
          return index;
        }
        default -> index--;
      }
    }
    return null;
  }

  private static boolean caretInsideTagExpressionOrQuote(CharSequence text, int from, int caretOffset) {
    int offset = from;
    while (offset < caretOffset) {
      char c = text.charAt(offset);
      if (c == '{') {
        int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(text, offset, text.length());
        if (expressionEnd == -1 || caretOffset < expressionEnd) {
          return true;
        }
        offset = expressionEnd;
      }
      else if (c == '\'' || c == '"') {
        int quoteEnd = scanQuoted(text, offset, text.length(), c);
        if (quoteEnd == -1 || caretOffset < quoteEnd) {
          return true;
        }
        offset = quoteEnd;
      }
      else {
        offset++;
      }
    }
    return false;
  }

  private static @Nullable Integer matchingExpressionStart(CharSequence text, int closeBrace) {
    int depth = 0;
    int offset = closeBrace;
    while (offset >= 0) {
      char c = text.charAt(offset);
      if (c == '}') {
        depth++;
      }
      else if (c == '{') {
        depth--;
        if (depth == 0) {
          return MdxExpressionBoundaryScanner.findExpressionEnd(text, offset, text.length()) == closeBrace + 1 ? offset : null;
        }
      }
      offset--;
    }
    return null;
  }

  private static @Nullable Integer matchingQuoteStart(CharSequence text, int closeQuote) {
    char quote = text.charAt(closeQuote);
    int offset = closeQuote - 1;
    while (offset >= 0) {
      if (text.charAt(offset) == '\n') {
        return null;
      }
      if (text.charAt(offset) == quote && scanQuoted(text, offset, text.length(), quote) == closeQuote + 1) {
        return offset;
      }
      offset--;
    }
    return null;
  }

  private static int scanQuoted(CharSequence text, int start, int limit, char quote) {
    int offset = start + 1;
    while (offset < limit) {
      char c = text.charAt(offset);
      if (c == '\\') {
        offset += 2;
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

  private static char charAt(CharSequence text, int index) {
    return index >= 0 && index < text.length() ? text.charAt(index) : '\0';
  }

  private static boolean isJsxNameStart(char c) {
    return c != '\0' && (Character.isLetter(c) || c == '_');
  }

  private static boolean isJsxNamePart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
  }
}
