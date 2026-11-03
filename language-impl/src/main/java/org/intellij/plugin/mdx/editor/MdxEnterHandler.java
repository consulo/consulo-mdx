package org.intellij.plugin.mdx.editor;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.editor.action.EnterHandlerDelegate;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.xml.language.psi.XmlTag;
import consulo.xml.language.psi.XmlText;
import org.intellij.plugin.mdx.lang.parse.MdxElementTypes;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Registered order="first": must win over the platform XML enter handler, which would otherwise expand tags directly on the
 * injected fragment.
 */
@ExtensionImpl(id = "mdxEnter", order = "first")
public class MdxEnterHandler implements EnterHandlerDelegate {
  @RequiredReadAction
  @Override
  public Result preprocessEnter(PsiFile file,
                                Editor editor,
                                SimpleReference<Integer> caretOffset,
                                SimpleReference<Integer> caretAdvance,
                                DataContext dataContext,
                                @Nullable EditorActionHandler originalHandler) {
    if (MdxCodeFenceSandbox.replay(editor, "EditorEnter")) {
      return Result.Stop;
    }
    MdxEnterContext context = MdxEnterContext.resolve(file, editor);
    if (context == null) {
      return Result.Continue;
    }
    return insertLineAfterFenceInFlow(context) ||
           insertLineBetweenEmptyJsxTagsInEsmBlock(context) ||
           insertLineSplittingJsxText(context) ? Result.Stop : Result.Continue;
  }

  @Override
  public Result postProcessEnter(PsiFile file, Editor editor, DataContext dataContext) {
    return Result.Continue;
  }

  /**
   * Enter right after a fence closes inside a JSX flow element: the default XML formatter would over-indent it a level.
   */
  @RequiredReadAction
  private static boolean insertLineAfterFenceInFlow(MdxEnterContext context) {
    Document document = context.document;
    int caret = context.caret;

    PsiElement element = PsiUtilCore.getElementAtOffset(context.mdxFile, Math.max(caret - 1, 0));
    MarkdownCodeFenceImpl fence = MarkdownCodeFenceUtils.getCodeFence(element);
    if (fence == null) {
      return false;
    }
    int fenceEnd = fence.getTextRange().getEndOffset();
    if (caret < fenceEnd) {
      return false; // still inside the fence body (sandbox handles it)
    }
    CharSequence between = document.getCharsSequence().subSequence(fenceEnd, caret);
    for (int i = 0; i < between.length(); i++) {
      if (between.charAt(i) != ' ' && between.charAt(i) != '\t') {
        return false;
      }
    }
    if (!isInsideFlowElement(fence)) {
      return false; // top-level fences already indent correctly
    }

    int fenceLine = document.getLineNumber(fence.getTextRange().getStartOffset());
    String indent = MdxCodeFenceIndent.fenceIndent(document, fenceLine);
    context.insertAndMoveCaret("\n" + indent, 1 + indent.length());
    return true;
  }

  private static boolean isInsideFlowElement(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent != null) {
      ASTNode node = parent.getNode();
      if (node != null && node.getElementType() == MdxElementTypes.MDX_JSX_FLOW_ELEMENT) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  /**
   * {@code <div><caret></div>} inside an ESM block: MDX_ESM_BLOCK is an opaque leaf, so the default handling drops the
   * closing tag to column 0.
   */
  @RequiredReadAction
  private static boolean insertLineBetweenEmptyJsxTagsInEsmBlock(MdxEnterContext context) {
    Document document = context.document;
    int caret = context.caret;
    if (caret == 0 || document.getCharsSequence().charAt(caret - 1) != '>') {
      return false;
    }

    PsiFile jsFile = context.jsFile();
    if (jsFile == null) {
      return false;
    }
    XmlTag tag = emptyJsxTagEndingAt(jsFile, caret);
    if (tag == null) {
      return false;
    }
    if (!isInsideEsmBlock(context.mdxFile, tag.getTextRange().getStartOffset())) {
      return false;
    }

    int lineStart = document.getLineStartOffset(document.getLineNumber(caret));
    String baseIndent = leadingWhitespace(document.getCharsSequence(), lineStart);
    String childIndent = baseIndent + " ".repeat(CodeStyle.getIndentSize(jsFile));
    context.insertAndMoveCaret("\n" + childIndent + "\n" + baseIndent, 1 + childIndent.length());
    return true;
  }

  @RequiredReadAction
  private static @Nullable XmlTag emptyJsxTagEndingAt(PsiFile jsFile, int offset) {
    PsiElement element = PsiUtilCore.getElementAtOffset(jsFile, offset - 1);
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag == null) {
      return null;
    }
    if (tag.isEmpty()) {
      return null; // self-closing <div/>: no separate closing tag to split away from
    }
    var value = tag.getValue().getTextRange();
    return value.isEmpty() && value.getStartOffset() == offset ? tag : null;
  }

  // walks the raw host AST, not PSI: PsiFile.findElementAt here would resolve straight into the foreign JS tree
  private static boolean isInsideEsmBlock(PsiFile mdxFile, int offset) {
    ASTNode node = mdxFile.getNode().findLeafElementAt(offset);
    while (node != null) {
      if (node.getElementType() == MdxElementTypes.MDX_ESM_BLOCK) {
        return true;
      }
      node = node.getTreeParent();
    }
    return false;
  }

  /**
   * Enter splitting a run of JSX text ({@code <div>ab<caret>cd</div>}): the default XML/JS formatter over-indents it a level
   * once any reformat has already run earlier in the session (WEB-78468) — inserting the line directly sidesteps that.
   */
  @RequiredReadAction
  private static boolean insertLineSplittingJsxText(MdxEnterContext context) {
    Document document = context.document;
    int caret = context.caret;

    PsiFile jsFile = context.jsFile();
    if (jsFile == null) {
      return false;
    }
    if (caret < 1 || caret >= jsFile.getTextLength()) {
      return false;
    }
    PsiElement before = PsiUtilCore.getElementAtOffset(jsFile, caret - 1);
    PsiElement after = PsiUtilCore.getElementAtOffset(jsFile, caret);
    PsiElement owner = jsxTextRunOwner(before);
    if (owner == null) {
      return false;
    }
    if (!Objects.equals(jsxTextRunOwner(after), owner)) {
      return false;
    }

    int lineStart = document.getLineStartOffset(document.getLineNumber(caret));
    String indent = leadingWhitespace(document.getCharsSequence(), lineStart);
    context.insertAndMoveCaret("\n" + indent, 1 + indent.length());
    return true;
  }

  // Text runs split into separate XmlText siblings around embedded whitespace, with the whitespace itself a
  // standalone sibling belonging to neither — so caret needs matching owners on both sides, not one XmlText's interior.
  private static @Nullable PsiElement jsxTextRunOwner(PsiElement element) {
    XmlText text = PsiTreeUtil.getParentOfType(element, XmlText.class);
    if (text != null) {
      return text.getParent();
    }
    return element instanceof PsiWhiteSpace ? element.getParent() : null;
  }

  /**
   * The indentation of the line starting at lineStart, spaces or tabs as written.
   */
  private static String leadingWhitespace(CharSequence text, int lineStart) {
    int offset = lineStart;
    while (offset < text.length() && (text.charAt(offset) == ' ' || text.charAt(offset) == '\t')) {
      offset++;
    }
    return text.subSequence(lineStart, offset).toString();
  }

  private static final class MdxEnterContext {
    final Editor editor;
    final Document document;
    final MdxFile mdxFile;
    final int caret;
    private @Nullable PsiFile myJsFile;
    private boolean myJsFileComputed;

    private MdxEnterContext(Editor editor, Document document, MdxFile mdxFile, int caret) {
      this.editor = editor;
      this.document = document;
      this.mdxFile = mdxFile;
      this.caret = caret;
    }

    @Nullable
    PsiFile jsFile() {
      if (!myJsFileComputed) {
        myJsFile = mdxFile.getViewProvider().getPsi(JavaScriptLanguage.INSTANCE);
        myJsFileComputed = true;
      }
      return myJsFile;
    }

    static @Nullable MdxEnterContext resolve(PsiFile file, Editor editor) {
      if (editor.getCaretModel().getCaretCount() != 1) {
        return null;
      }
      MdxFile mdxFile = null;
      for (PsiFile root : file.getViewProvider().getAllFiles()) {
        if (root instanceof MdxFile mdx) {
          mdxFile = mdx;
          break;
        }
      }
      if (mdxFile == null) {
        return null;
      }
      Document document = editor.getDocument();
      PsiDocumentManager.getInstance(mdxFile.getProject()).commitDocument(document);
      return new MdxEnterContext(editor, document, mdxFile, editor.getCaretModel().getOffset());
    }

    void insertAndMoveCaret(String text, int caretOffset) {
      document.insertString(caret, text);
      PsiDocumentManager.getInstance(mdxFile.getProject()).commitDocument(document);
      editor.getCaretModel().moveToOffset(caret + caretOffset);
    }
  }
}
