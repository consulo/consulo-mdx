package com.intellij.mdx.backend.format;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.PostFormatProcessor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.util.PsiTreeUtil;
import org.intellij.plugin.mdx.editor.MdxCodeFenceIndent;
import org.intellij.plugin.mdx.lang.psi.MdxFile;
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Formats a code fence body by round-tripping it through a temporary standalone file of the fence language, rather than
 * relying on the more fragile injection-based formatting.
 */
@ExtensionImpl
public class MdxCodeFencePostFormatProcessor implements PostFormatProcessor {
  @Override
  public PsiElement processElement(PsiElement source, CodeStyleSettings settings) {
    if (!(source.getContainingFile() instanceof MdxFile file)) {
      return source;
    }
    Document document = file.getViewProvider().getDocument();
    if (document == null) {
      return source;
    }
    List<MarkdownCodeFenceImpl> fences = source instanceof MarkdownCodeFenceImpl fence
                                         ? List.of(fence)
                                         : new ArrayList<>(PsiTreeUtil.findChildrenOfType(source, MarkdownCodeFenceImpl.class));
    processFences(fences, file, document);
    return source;
  }

  @Override
  public TextRange processText(PsiFile source, TextRange rangeToReformat, CodeStyleSettings settings) {
    if (!(source instanceof MdxFile file)) {
      return rangeToReformat;
    }
    Document document = file.getViewProvider().getDocument();
    if (document == null) {
      return rangeToReformat;
    }
    List<MarkdownCodeFenceImpl> fences = new ArrayList<>();
    for (MarkdownCodeFenceImpl fence : PsiTreeUtil.findChildrenOfType(source, MarkdownCodeFenceImpl.class)) {
      if (rangeToReformat.intersects(fence.getTextRange())) {
        fences.add(fence);
      }
    }
    processFences(fences, file, document);
    return source.getTextRange();
  }

  private static void processFences(List<MarkdownCodeFenceImpl> fences, MdxFile file, Document document) {
    // Replacing a fence body wholesale while a live template is running inside it can throw, since the template's
    // not-yet-parsed content has no counterpart in the injected PSI to patch onto: skip the rewrite and let the next
    // non-template-triggered reformat pick the fence up.
    if (isReformattingLiveTemplate()) {
      return;
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    documentManager.commitDocument(document);
    // Bottom-up so replacing one fence does not shift the offsets of the fences above it.
    List<MarkdownCodeFenceImpl> sorted = new ArrayList<>(fences);
    sorted.sort(Comparator.comparingInt((MarkdownCodeFenceImpl it) -> it.getTextRange().getStartOffset()).reversed());
    for (MarkdownCodeFenceImpl fence : sorted) {
      TextRange fenceRange = fence.getTextRange();
      int startLine = document.getLineNumber(fenceRange.getStartOffset());
      int endLine = document.getLineNumber(Math.max(fenceRange.getEndOffset() - 1, fenceRange.getStartOffset()));
      String replacement = buildFormattedFence(fence, file, document, startLine, endLine);
      if (replacement == null) {
        continue;
      }
      int start = document.getLineStartOffset(startLine);
      int end = document.getLineEndOffset(endLine);
      if (document.getText(new TextRange(start, end)).equals(replacement)) {
        continue;
      }
      document.replaceString(start, end, replacement);
      documentManager.commitDocument(document);
    }
  }

  /**
   * Whether the reformat comes from a live template.
   */
  private static boolean isReformattingLiveTemplate() {
    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
      if (element.getClassName().endsWith(".TemplateState") && element.getMethodName().equals("doReformat")) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable String buildFormattedFence(MarkdownCodeFenceImpl fence, MdxFile file, Document document, int startLine, int endLine) {
    if (endLine <= startLine) {
      return null; // opener and closer must be on different lines
    }
    String info = fence.getFenceLanguage();
    if (info == null) {
      return null;
    }
    Language language = CodeFenceLanguageGuesser.guessLanguageForInjection(info);
    if (language == null) {
      return null;
    }

    // Whatever the fence already carries is where it belongs: this runs after the formatter, which has already
    // placed the fence at its JSX nesting depth, and a blockquoted fence is placed by the quote rather than by the
    // code style either way. It is re-emitted in front of every line below, so the markers come stripped of it.
    String indent = MdxCodeFenceIndent.fenceIndent(document, startLine);
    String opener = bareLine(document, startLine, indent);
    String closer = bareLine(document, endLine, indent);
    List<String> contentLines = new ArrayList<>();
    for (int line = startLine + 1; line < endLine; line++) {
      contentLines.add(lineText(document, line));
    }

    // Formatting "" would yield a spurious blank line between the fences, so keep an empty body empty.
    List<String> formatted = contentLines.isEmpty() ? List.of() : formatCode(file, language, dedent(contentLines, indent));

    StringBuilder builder = new StringBuilder(indent).append(opener);
    for (String line : formatted) {
      builder.append('\n');
      // A line with nothing on it gets no indentation, but still the markers keeping it inside the quote.
      builder.append(line.isEmpty() ? indent.stripTrailing() : indent);
      builder.append(line);
    }
    builder.append('\n').append(indent).append(closer);
    return builder.toString();
  }

  private static String lineText(Document document, int line) {
    return document.getCharsSequence().subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line)).toString();
  }

  private static String bareLine(Document document, int line, String indent) {
    String text = lineText(document, line);
    return text.substring(commonPrefixLength(indent, text)).trim();
  }

  /**
   * Removes the fence's own indent from the lines so the sandbox formatter starts at column zero.
   */
  private static List<String> dedent(List<String> lines, String indent) {
    List<String> result = new ArrayList<>(lines.size());
    for (String line : lines) {
      result.add(line.substring(commonPrefixLength(indent, line)));
    }
    return result;
  }

  private static int commonPrefixLength(String a, String b) {
    int length = Math.min(a.length(), b.length());
    int i = 0;
    while (i < length && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    return i;
  }

  private static List<String> formatCode(MdxFile file, Language language, List<String> lines) {
    String code = String.join("\n", lines);
    PsiFile sandbox = PsiFileFactory.getInstance(file.getProject()).createFileFromText("fence.dummy", language, code);
    CodeStyleManager.getInstance(file.getProject()).reformat(sandbox);
    return List.of(sandbox.getText().split("\n", -1));
  }
}
