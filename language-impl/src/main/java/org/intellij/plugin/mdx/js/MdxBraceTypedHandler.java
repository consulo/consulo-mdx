package org.intellij.plugin.mdx.js;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import org.intellij.plugin.mdx.lang.parse.MdxJsxScanner;
import org.intellij.plugin.mdx.lang.template.MdxFileViewProvider;

/**
 * Auto-closes {@code {} to {@code {}} when typed inside a JSX element body in an MDX file. The standard JS
 * {@code {}→{@code {}} handler gates on the base file being a JS dialect, which MDX (template-data, not
 * injection) never is, so this bridges the gap by detecting the JSX-body context directly.
 */
@ExtensionImpl
public class MdxBraceTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result beforeCharTyped(char c, Project project, Editor editor, PsiFile file, FileType fileType) {
    if (c != '{') {
      return Result.CONTINUE;
    }
    if (!(file.getViewProvider() instanceof MdxFileViewProvider)) {
      return Result.CONTINUE;
    }
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
      return Result.CONTINUE;
    }

    int offset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getCharsSequence();

    // Don't double-close: if `}` is already the next character, skip.
    if (offset < text.length() && text.charAt(offset) == '}') {
      return Result.CONTINUE;
    }

    // Scan document text rather than PSI: the platform may pass the MdxJS virtual file for the caret's context.
    if (!isInsideJsxElementBody(text, offset)) {
      return Result.CONTINUE;
    }

    EditorModificationUtil.insertStringAtCaret(editor, "}", false, 0);
    return Result.CONTINUE;
  }

  // True if the nearest `>` closing a line-start JSX opening tag before offset has no matching
  // `</tag>` between it and offset.
  private static boolean isInsideJsxElementBody(CharSequence text, int offset) {
    int depth = 0;
    int i = offset - 1;
    while (i >= 0) {
      if (text.charAt(i) == '>') {
        int ltPos = findMatchingLt(text, i);
        if (ltPos >= 0) {
          if (ltPos + 1 < text.length() && text.charAt(ltPos + 1) == '/') {
            depth++;  // closing tag </...>
          }
          else if (i > 0 && text.charAt(i - 1) == '/') {
            // self-closing <.../>: skip
          }
          else if (depth > 0) {
            depth--;
          }
          else if (MdxJsxScanner.isLineStartJsx(text, ltPos)) {
            return true;
          }
          i = ltPos - 1;
          continue;
        }
      }
      i--;
    }
    return false;
  }

  private static int findMatchingLt(CharSequence text, int gtPos) {
    int i = gtPos - 1;
    while (i >= 0) {
      if (text.charAt(i) == '<') {
        return i;
      }
      if (text.charAt(i) == '\n') {
        break;  // don't scan across lines
      }
      i--;
    }
    return -1;
  }
}
