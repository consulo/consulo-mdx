package org.intellij.plugin.mdx.editor;

import consulo.annotation.access.RequiredWriteAction;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.action.EditorActionManager;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.DocumentWindow;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.file.LanguageFileType;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiUtilCore;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils;
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Replays an editor action that lands inside an MDX code fence in a throwaway file of the fence language.
 * <p>
 * Editing the injected fence fragment directly is unreliable, since the injected document is a standalone
 * 0-based file whose formatter/indenter fights the host indentation. Instead the fence body is copied into
 * a standalone file of the fence language (stripped of the indent its lines carry — see {@link MdxCodeFenceIndent#fenceIndent}
 * — since indented input confuses the language formatter), the same action is replayed there, and the result is written
 * back with that indent restored, with caret and selection mapped both ways.
 * <p>
 * The replayed handler gets a data context with the sandbox editor, file and project.
 */
public final class MdxCodeFenceSandbox {
  private MdxCodeFenceSandbox() {
  }

  /**
   * If the caret (and any selection) sits inside an MDX code fence body, runs {@code actionId} against a sandbox of
   * the fence language and writes the result back, returning true. Returns false if the action does not apply
   * (no fence, multiple carets, selection crossing the fence boundary, ...) so the caller can fall back.
   */
  public static boolean replay(Editor editor, String actionId) {
    Project project = editor.getProject();
    if (project == null) {
      return false;
    }
    if (!editor.getDocument().isWritable() || editor.isViewer() || editor.getCaretModel().getCaretCount() != 1) {
      return false;
    }

    Editor hostEditor = editor instanceof EditorWindow editorWindow ? editorWindow.getDelegate() : editor;
    Document hostDocument = hostEditor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(hostDocument);
    if (!(documentManager.getPsiFile(hostDocument) instanceof MdxFile hostFile)) {
      return false;
    }

    DocumentWindow documentWindow = editor.getDocument() instanceof DocumentWindow window ? window : null;
    int caret = toHost(documentWindow, editor.getCaretModel().getOffset());
    int selectionStart = toHost(documentWindow, editor.getSelectionModel().getSelectionStart());
    int selectionEnd = toHost(documentWindow, editor.getSelectionModel().getSelectionEnd());

    PsiElement element = PsiUtilCore.getElementAtOffset(hostFile, Math.max(caret - 1, 0));
    MarkdownCodeFenceImpl fence = MarkdownCodeFenceUtils.getCodeFence(element);
    if (fence == null) {
      return false;
    }
    String fenceLanguage = fence.getFenceLanguage();
    Pair<Language, @Nullable String> guessedLanguageAndExtension =
      fenceLanguage == null ? null : CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection(fenceLanguage);
    Pair<Language, @Nullable String> languageAndExtension =
      guessedLanguageAndExtension != null ? guessedLanguageAndExtension : Pair.create(PlainTextLanguage.INSTANCE, "text");
    boolean isPlainTextFallback = guessedLanguageAndExtension == null;

    TextRange fenceRange = fence.getTextRange();
    int startLine = hostDocument.getLineNumber(fenceRange.getStartOffset());
    int endLine = hostDocument.getLineNumber(Math.max(fenceRange.getEndOffset() - 1, fenceRange.getStartOffset()));

    boolean hasBody = endLine > startLine + 1;

    int contentStart;
    int contentEnd;
    if (hasBody) {
      contentStart = hostDocument.getLineStartOffset(startLine + 1);
      contentEnd = hostDocument.getLineEndOffset(endLine - 1);
      if (caret < contentStart || caret > contentEnd || selectionStart < contentStart || selectionEnd > contentEnd) {
        return false;
      }
    }
    else {
      // past the closing backticks, or before the fence starts, belongs to whatever runs after this delegate
      if (caret <= fenceRange.getStartOffset() || caret >= fenceRange.getEndOffset()) {
        return false;
      }
      if (selectionStart != caret || selectionEnd != caret) {
        return false; // nothing to sandbox a real selection into
      }
      contentStart = caret;
      contentEnd = caret;
    }

    String content = hasBody ? hostDocument.getText(new TextRange(contentStart, contentEnd)) : "";
    // Every line of the fence carries what its opening line has in front of the backticks, so that is what the
    // round trip strips and puts back. With no body at all the fence's opening line is the caret's own line.
    boolean hasCode = content.lines().anyMatch(it -> !it.isBlank());
    // A language-less fence has no formatter to reflow anything, so on Enter the platform's own plain-text indent
    // copying already carries the caret line's indentation onto the line it opens. Applying the fence indent on top
    // would add it a second time, so leave the body exactly as the host has it.
    boolean preservesHostIndentation = isPlainTextFallback && actionId.equals("EditorEnter") && hasCode &&
                                       lineAt(content, caret - contentStart).isBlank();
    String indent = preservesHostIndentation ? ""
                    : MdxCodeFenceIndent.fenceIndent(hostDocument, hasBody ? startLine : hostDocument.getLineNumber(caret));
    int[] offsets = {caret - contentStart, selectionStart - contentStart, selectionEnd - contentStart};
    Shifted dedented = shift(content, offsets, indent, true, hasBody);

    String extension = languageAndExtension.getSecond();
    if (extension == null) {
      LanguageFileType fileType = languageAndExtension.getFirst().getAssociatedFileType();
      extension = fileType != null ? fileType.getDefaultExtension() : "text";
    }
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("fence." + extension, languageAndExtension.getFirst(), dedented.text());
    Document sandboxDocument = documentManager.getDocument(file);
    if (sandboxDocument == null) {
      return false;
    }
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    Editor sandboxEditor = EditorFactory.getInstance().createEditor(sandboxDocument, project, virtualFile, false);
    // createEditor(document, project, file, isViewer) uses the file only to build the highlighter: set it so the sandbox
    // is a genuine file-backed editor, language line-indent providers dereference editor.getVirtualFile().
    if (sandboxEditor instanceof EditorEx editorEx) {
      editorEx.setFile(virtualFile);
    }
    try {
      int length = sandboxDocument.getTextLength();
      sandboxEditor.getCaretModel().moveToOffset(clamp(dedented.offsets()[0], length));
      if (dedented.offsets()[1] != dedented.offsets()[2]) {
        sandboxEditor.getSelectionModel().setSelection(clamp(dedented.offsets()[1], length), clamp(dedented.offsets()[2], length));
      }
      DataContext sandboxContext = new SandboxDataContext(sandboxEditor, file, project);
      // The action handler asserts a current command; one is active in the intercepted action, so this names it.
      CommandProcessor.getInstance().executeCommand(
        () -> EditorActionManager.getInstance().getActionHandler(actionId).execute(sandboxEditor, null, sandboxContext),
        null, null);
      documentManager.commitDocument(sandboxDocument);

      int[] resultOffsets = {
        sandboxEditor.getCaretModel().getOffset(),
        sandboxEditor.getSelectionModel().getSelectionStart(),
        sandboxEditor.getSelectionModel().getSelectionEnd()
      };
      Shifted reindented = shift(sandboxDocument.getText(), resultOffsets, indent, false, hasBody);
      ContentReplacement replacement = changedContent(content, reindented.text());
      if (replacement != null) {
        int replaceStart = contentStart + replacement.start();
        int replaceEnd = contentStart + replacement.end();
        WriteAction.run(() -> hostDocument.replaceString(replaceStart, replaceEnd, replacement.text()));
      }
      documentManager.commitDocument(hostDocument);
      int[] hostOffsets = reindented.offsets();
      hostEditor.getCaretModel().moveToOffset(contentStart + hostOffsets[0]);
      if (hostOffsets[1] != hostOffsets[2]) {
        hostEditor.getSelectionModel().setSelection(contentStart + hostOffsets[1], contentStart + hostOffsets[2]);
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(sandboxEditor);
    }
    return true;
  }

  private static int toHost(@Nullable DocumentWindow documentWindow, int offset) {
    return documentWindow != null ? documentWindow.injectedToHost(offset) : offset;
  }

  private static int clamp(int value, int max) {
    return Math.max(0, Math.min(value, max));
  }

  /**
   * The line of {@code text} containing character offset {@code offset}.
   */
  private static String lineAt(String text, int offset) {
    int lineStart = 0;
    for (String line : text.split("\n", -1)) {
      int lineEnd = lineStart + line.length();
      if (offset >= lineStart && offset <= lineEnd) {
        return line;
      }
      lineStart = lineEnd + 1;
    }
    return "";
  }

  static @Nullable ContentReplacement changedContent(String original, String updated) {
    if (original.equals(updated)) {
      return null;
    }
    int prefixLength = 0;
    while (prefixLength < original.length() &&
           prefixLength < updated.length() &&
           original.charAt(prefixLength) == updated.charAt(prefixLength)) {
      prefixLength++;
    }

    int suffixLength = 0;
    while (suffixLength < original.length() - prefixLength &&
           suffixLength < updated.length() - prefixLength &&
           original.charAt(original.length() - 1 - suffixLength) == updated.charAt(updated.length() - 1 - suffixLength)) {
      suffixLength++;
    }

    return new ContentReplacement(prefixLength, original.length() - suffixLength,
                                  updated.substring(prefixLength, updated.length() - suffixLength));
  }

  /**
   * Strips {@code indent} off every line of {@code text} ({@code strip}) or puts it back in front of every line, returning the
   * text along with the given {@code offsets} mapped into it.
   * <p>
   * {@code startsHostLine} is false for a fence with no body lines at all, where the replaced region is the caret's own
   * position rather than whole lines: the first line back from the sandbox continues the fence's opening line
   * there, so it must not be given an indent of its own.
   */
  static Shifted shift(String text, int[] offsets, String indent, boolean strip, boolean startsHostLine) {
    if (indent.isEmpty()) {
      return new Shifted(text, offsets);
    }
    StringBuilder out = new StringBuilder();
    int[] mapped = Arrays.copyOf(offsets, offsets.length);
    int lineStart = 0;
    List<String> lines = Arrays.asList(text.split("\n", -1));
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (index > 0) {
        out.append('\n');
      }
      boolean applies = index > 0 || startsHostLine;
      int delta;
      if (!applies) {
        delta = 0;
      }
      else if (strip) {
        delta = -commonPrefixLength(indent, line);
      }
      else {
        delta = indent.length();
      }
      int base = out.length();
      for (int k = 0; k < offsets.length; k++) {
        int offset = offsets[k];
        if (offset >= lineStart && offset <= lineStart + line.length()) {
          mapped[k] = base + Math.max(0, offset - lineStart + delta);
        }
      }
      if (strip) {
        out.append(line, -delta, line.length());
      }
      else {
        if (applies) {
          out.append(indent);
        }
        out.append(line);
      }
      lineStart += line.length() + 1;
    }
    return new Shifted(out.toString(), mapped);
  }

  static int commonPrefixLength(String a, String b) {
    int length = Math.min(a.length(), b.length());
    int i = 0;
    while (i < length && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    return i;
  }

  record Shifted(String text, int[] offsets) {
  }

  record ContentReplacement(int start, int end, String text) {
  }

  private static final class SandboxDataContext implements DataContext {
    private final Editor myEditor;
    private final PsiFile myFile;
    private final Project myProject;

    private SandboxDataContext(Editor editor, PsiFile file, Project project) {
      myEditor = editor;
      myFile = file;
      myProject = project;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getData(Key<T> key) {
      if (key == Editor.KEY) {
        return (T)myEditor;
      }
      if (key == PsiFile.KEY) {
        return (T)myFile;
      }
      if (key == Project.KEY) {
        return (T)myProject;
      }
      return null;
    }
  }
}
