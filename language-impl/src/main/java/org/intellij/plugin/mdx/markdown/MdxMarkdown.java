package org.intellij.plugin.mdx.markdown;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugin.mdx.lang.parse.MdxElementTypes;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Whether a place of an MDX file is Markdown (not JSX, ESM, an expression or code).

 */
public final class MdxMarkdown {
  private static final Set<IElementType> SPECIAL_REGION_TYPES = Set.of(
    MdxElementTypes.MDX_BLOCK,
    MdxElementTypes.MDX_ESM_BLOCK,
    MdxElementTypes.MDX_JSX_OPENING_ELEMENT,
    MdxElementTypes.MDX_JSX_CLOSING_ELEMENT,
    MdxElementTypes.MDX_JSX_SELF_CLOSING_ELEMENT,
    MdxElementTypes.MDX_JSX_ATTRIBUTE,
    MdxElementTypes.MDX_EXPRESSION,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_SPAN
  );

  private MdxMarkdown() {
  }

  public static boolean isMarkdownContext(@Nullable DataContext dataContext) {
    if (dataContext == null) {
      return true;
    }
    PsiFile file = dataContext.getData(PsiFile.KEY);
    if (file == null) {
      return true;
    }
    if (!(file instanceof MdxFile mdxFile)) {
      return false;
    }
    Editor editor = dataContext.getData(Editor.KEY);
    if (editor == null) {
      return true;
    }
    return areCaretsInMarkdown(mdxFile, editor);
  }

  public static boolean areCaretsInMarkdown(MdxFile file, Editor editor) {
    return ReadAction.compute(() -> {
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        if (!isMarkdownRange(file, caret.getSelectionStart(), caret.getSelectionEnd())) {
          return false;
        }
      }
      return true;
    });
  }

  @RequiredReadAction
  public static boolean isMarkdownElement(PsiElement element) {
    if (element instanceof MdxFile) {
      return true;
    }
    FileAndRange fileAndRange = findHostMdxFileAndRange(element);
    if (fileAndRange == null) {
      return false;
    }
    return hasMarkdownTextInRange(fileAndRange.file(), fileAndRange.range());
  }

  @RequiredReadAction
  public static boolean isMarkdownRange(PsiFile file, int startOffset, int endOffset) {
    if (!(file instanceof MdxFile mdxFile) || file.getTextLength() == 0) {
      return false;
    }
    int length = file.getTextLength();
    if (startOffset < 0 || startOffset > length || endOffset < 0 || endOffset > length || startOffset > endOffset) {
      return false;
    }
    if (startOffset == endOffset) {
      return isMarkdownOffset(mdxFile, startOffset);
    }

    TextRange range = new TextRange(startOffset, endOffset);
    if (isInsideSpecialRegion(file, startOffset) || isInsideSpecialRegion(file, endOffset)) {
      return false;
    }
    return hasMarkdownTextInRange(file, range);
  }

  @RequiredReadAction
  private static boolean isMarkdownOffset(MdxFile file, int offset) {
    if (offset < 0 || offset > file.getTextLength()) {
      return false;
    }
    return !isInsideSpecialRegion(file, offset);
  }

  @RequiredReadAction
  private static @Nullable FileAndRange findHostMdxFileAndRange(PsiElement element) {
    TextRange range = element.getTextRange();
    if (range == null) {
      return null;
    }
    PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof MdxFile mdxFile) {
      return new FileAndRange(mdxFile, range);
    }
    if (containingFile != null && containingFile.getViewProvider().getPsi(MdxLanguage.INSTANCE) instanceof MdxFile mdxFile) {
      return new FileAndRange(mdxFile, range);
    }

    InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(element.getProject());
    if (!(injectionManager.getTopLevelFile(element) instanceof MdxFile topLevelFile)) {
      return null;
    }
    return new FileAndRange(topLevelFile, injectionManager.injectedToHost(element, range));
  }

  @RequiredReadAction
  private static boolean isInsideSpecialRegion(PsiElement element, int offset) {
    TextRange range = element.getTextRange();
    if (range == null) {
      return false;
    }
    if (offset <= range.getStartOffset() || offset >= range.getEndOffset()) {
      return false;
    }
    if (SPECIAL_REGION_TYPES.contains(PsiUtilCore.getElementType(element))) {
      return true;
    }
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (isInsideSpecialRegion(child, offset)) {
        return true;
      }
    }
    return false;
  }

  @RequiredReadAction
  private static boolean hasMarkdownTextInRange(PsiElement element, TextRange range) {
    TextRange elementRange = element.getTextRange();
    if (elementRange == null || !elementRange.intersects(range)) {
      return false;
    }
    if (SPECIAL_REGION_TYPES.contains(PsiUtilCore.getElementType(element))) {
      return false;
    }
    if (element.getFirstChild() == null) {
      return hasNonBlankTextInRange(element, range);
    }
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (hasMarkdownTextInRange(child, range)) {
        return true;
      }
    }
    return false;
  }

  @RequiredReadAction
  private static boolean hasNonBlankTextInRange(PsiElement element, TextRange range) {
    TextRange elementRange = element.getTextRange();
    if (elementRange == null) {
      return false;
    }
    int startOffset = Math.max(range.getStartOffset(), elementRange.getStartOffset());
    int endOffset = Math.min(range.getEndOffset(), elementRange.getEndOffset());
    if (startOffset >= endOffset) {
      return false;
    }
    return !element.getText().substring(startOffset - elementRange.getStartOffset(), endOffset - elementRange.getStartOffset()).isBlank();
  }

  private record FileAndRange(MdxFile file, TextRange range) {
  }
}
