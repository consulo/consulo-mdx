package org.intellij.plugin.mdx.lang.template;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressManager;
import consulo.document.util.TextRange;
import consulo.javascript.language.JavaScriptFileTypeWithVersion;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.file.LanguageFileType;
import consulo.language.impl.psi.template.TemplateDataElementType;
import consulo.language.impl.psi.template.TemplateDataModifications;
import consulo.language.lexer.Lexer;
import consulo.language.version.LanguageVersion;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import org.intellij.markdown.MarkdownElementType;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.markdown.parser.MarkdownProductionSink;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.plugin.mdx.js.MdxJSLanguage;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugin.mdx.lang.parse.MdxCancellableCharSequence;
import org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner;
import org.intellij.plugin.mdx.lang.parse.MdxFlavourDescriptor;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownLibElementTypes;
import org.intellij.plugin.mdx.lang.parse.MdxTextRangeSet;
import org.intellij.plugin.mdx.lang.parse.MdxTokenTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * The template data (JavaScript) of an MDX file: its ESM blocks, JSX elements and expressions, in one module.
 * <p>
 * The template text is parsed by the {@link MdxJSLanguage} version: the fake file type of the template file names it.
 */
public class MdxTemplateDataElementType extends TemplateDataElementType {
  public static final MdxTemplateDataElementType INSTANCE = new MdxTemplateDataElementType();

  private static final Set<IElementType> OPAQUE_MARKDOWN_ELEMENTS = Set.of(
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_SPAN
  );

  private static final MarkdownElementType TEMPLATE_ROOT = new MarkdownElementType("MDX_TEMPLATE_ROOT");

  protected MdxTemplateDataElementType() {
    super("MDX_TEMPLATE_JSX", MdxLanguage.INSTANCE, MdxTokenTypes.EMBEDDED_JS_CONTENT, MdxTemplateElementTypes.OUTER_MARKDOWN_CONTENT);
  }

  @Override
  protected LanguageFileType createTemplateFakeFileType(Language language) {
    return new MdxJSTemplateFileType(language);
  }

  @Override
  protected TemplateDataModifications collectTemplateModifications(CharSequence sourceCode, Lexer baseLexer) {
    TemplateDataModifications modifications = new TemplateDataModifications();
    for (Modification operation : collectModifications(MdxCancellableCharSequence.mdxCancellableText(sourceCode))) {
      if (operation instanceof Modification.Outer outer) {
        modifications.addOuterRange(outer.range(), true);
      }
      else if (operation instanceof Modification.Remove remove) {
        modifications.addRangeToRemove(remove.offset(), remove.text());
      }
    }
    return modifications;
  }

  /**
   * Applies {@link #collectTemplateModifications} through the public range collector API: the template text is the source
   * without the outer ranges, plus the inserted statement separators.
   */
  @Override
  protected CharSequence createTemplateText(CharSequence sourceCode, Lexer baseLexer, RangeCollector rangeCollector) {
    CharSequence source = MdxCancellableCharSequence.mdxCancellableText(sourceCode);
    StringBuilder result = new StringBuilder(source.length());
    int offset = 0;
    for (Modification operation : collectModifications(source)) {
      if (operation instanceof Modification.Outer outer) {
        TextRange range = outer.range();
        result.append(source, offset, range.getStartOffset());
        offset = range.getEndOffset();
        rangeCollector.addOuterRange(range, true);
      }
      else if (operation instanceof Modification.Remove remove) {
        result.append(source, offset, remove.offset());
        offset = remove.offset();
        result.append(remove.text());
        rangeCollector.addRangeToRemove(TextRange.from(remove.offset(), remove.text().length()));
      }
    }
    result.append(source, offset, source.length());
    return result;
  }

  private static List<Modification> collectModifications(CharSequence source) {
    TemplateStructure structure = collectTemplateStructure(source);
    List<TextRange> pieceRanges = new ArrayList<>(structure.pieces().size());
    for (EmbeddedPiece piece : structure.pieces()) {
      pieceRanges.add(piece.range());
    }
    MdxTextRangeSet embeddedRanges = MdxTextRangeSet.of(pieceRanges);

    List<Modification> operations = new ArrayList<>();
    int offset = 0;
    for (TextRange range : embeddedRanges) {
      if (offset < range.getStartOffset()) {
        operations.add(new Modification.Outer(new TextRange(offset, range.getStartOffset())));
      }
      offset = Math.max(offset, range.getEndOffset());
    }
    if (offset < source.length()) {
      operations.add(new Modification.Outer(new TextRange(offset, source.length())));
    }

    List<EmbeddedPiece> pieces = structure.pieces();
    for (int index = 0; index < pieces.size(); index++) {
      EmbeddedPiece piece = pieces.get(index);
      if (!piece.rootKind().requiresStatementSeparator) {
        continue;
      }
      TextRange nextRange = index + 1 < pieces.size() ? pieces.get(index + 1).range() : null;
      boolean continuesAtBoundary = nextRange != null &&
                                    nextRange.getStartOffset() == piece.range().getEndOffset() &&
                                    nextRange.getEndOffset() > piece.range().getEndOffset();
      if (!continuesAtBoundary && !endsWithSemicolon(source, piece.range())) {
        operations.add(new Modification.Remove(piece.range().getEndOffset(), ";"));
      }
    }
    operations.sort(Comparator.comparingInt(Modification::offset).thenComparingInt(Modification::priority));
    return operations;
  }

  /**
   * Derives template ranges from the same Markdown structure that drives the base PSI and editor
   * highlighter. This follows RMarkdown's template-data pattern and deliberately does not rescan JSX.
   * <p>
   * The parse is walked through a sink (open/close in document order), inline content included.
   */
  private static TemplateStructure collectTemplateStructure(CharSequence sourceCode) {
    MarkdownParser parser = new MarkdownParser(MdxFlavourDescriptor.INSTANCE, true, ProgressManager::checkCanceled);
    List<SequentialParser.Node> production = parser.parseBlocks(TEMPLATE_ROOT, sourceCode);

    List<TemplateRoot> roots = new ArrayList<>();
    List<TextRange> opaqueRanges = new ArrayList<>();
    LinePrefixIndex linePrefixes = new LinePrefixIndex(sourceCode);
    StructureSink sink = new StructureSink(roots, opaqueRanges, linePrefixes);
    parser.walkBlocks(production, sourceCode, true, sink);

    List<TemplateRoot> exactRoots = new ArrayList<>(roots.size());
    for (TemplateRoot root : roots) {
      exactRoots.add(withExactExpressionRange(root, sourceCode));
    }
    List<TemplateRoot> normalizedRoots = mergeAdjacentEsm(exactRoots, sourceCode);
    MdxTextRangeSet normalizedOpaqueRanges = MdxTextRangeSet.of(opaqueRanges);
    return new TemplateStructure(projectRoots(normalizedRoots, normalizedOpaqueRanges), normalizedOpaqueRanges);
  }

  /**
   * Collects the template roots and opaque Markdown elements: a template root counts only when no other template root
   * encloses it, an opaque Markdown element only when no other opaque element encloses it.
   */
  private static final class StructureSink implements MarkdownProductionSink {
    private final List<TemplateRoot> myRoots;
    private final List<TextRange> myOpaqueRanges;
    private final LinePrefixIndex myLinePrefixes;
    private final Deque<Frame> myFrames = new ArrayDeque<>();
    private int myTemplateRootDepth;
    private int myOpaqueDepth;

    private StructureSink(List<TemplateRoot> roots, List<TextRange> opaqueRanges, LinePrefixIndex linePrefixes) {
      myRoots = roots;
      myOpaqueRanges = opaqueRanges;
      myLinePrefixes = linePrefixes;
    }

    @Override
    public void open(IElementType type, int startOffset) {
      ProgressManager.checkCanceled();
      RootKind rootKind = rootKind(type);
      boolean opaque = OPAQUE_MARKDOWN_ELEMENTS.contains(type);
      myFrames.push(new Frame(startOffset, rootKind, rootKind != null && myTemplateRootDepth == 0, opaque && myOpaqueDepth == 0));
      if (rootKind != null) {
        myTemplateRootDepth++;
      }
      if (opaque) {
        myOpaqueDepth++;
      }
    }

    @Override
    public void close(IElementType type, int endOffset) {
      Frame frame = myFrames.pop();
      if (frame.rootKind() != null) {
        myTemplateRootDepth--;
        if (frame.topRoot()) {
          myRoots.add(new TemplateRoot(new TextRange(frame.startOffset(), endOffset), frame.rootKind()));
        }
      }
      if (OPAQUE_MARKDOWN_ELEMENTS.contains(type)) {
        myOpaqueDepth--;
        if (frame.topOpaque()) {
          myOpaqueRanges.add(new TextRange(myLinePrefixes.opaqueStart(frame.startOffset()), endOffset));
        }
      }
    }

    @Override
    public void leaf(IElementType type, int startOffset, int endOffset) {
      // a token production is a leaf of the tree
      RootKind rootKind = rootKind(type);
      if (rootKind != null && myTemplateRootDepth == 0) {
        myRoots.add(new TemplateRoot(new TextRange(startOffset, endOffset), rootKind));
      }
      if (OPAQUE_MARKDOWN_ELEMENTS.contains(type) && myOpaqueDepth == 0) {
        myOpaqueRanges.add(new TextRange(myLinePrefixes.opaqueStart(startOffset), endOffset));
      }
    }

    private record Frame(int startOffset, @Nullable RootKind rootKind, boolean topRoot, boolean topOpaque) {
    }
  }

  private static TemplateRoot withExactExpressionRange(TemplateRoot root, CharSequence sourceCode) {
    if (root.kind() != RootKind.EXPRESSION) {
      return root;
    }
    int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(sourceCode, root.range().getStartOffset(), root.range().getEndOffset());
    return expressionEnd == -1 ? root : new TemplateRoot(new TextRange(root.range().getStartOffset(), expressionEnd), root.kind());
  }

  private static List<TemplateRoot> mergeAdjacentEsm(List<TemplateRoot> roots, CharSequence sourceCode) {
    if (roots.isEmpty()) {
      return List.of();
    }
    List<TemplateRoot> result = new ArrayList<>(roots.size());
    TemplateRoot current = roots.get(0);
    for (int i = 1; i < roots.size(); i++) {
      TemplateRoot next = roots.get(i);
      if (current.kind() == RootKind.ESM && next.kind() == RootKind.ESM &&
          isSingleWhitespaceLine(sourceCode, current.range().getEndOffset(), next.range().getStartOffset())) {
        current = new TemplateRoot(new TextRange(current.range().getStartOffset(), next.range().getEndOffset()), current.kind());
      }
      else {
        result.add(current);
        current = next;
      }
    }
    result.add(current);
    return result;
  }

  private static boolean isSingleWhitespaceLine(CharSequence sourceCode, int startOffset, int endOffset) {
    if (startOffset >= endOffset) {
      return false;
    }
    int lineBreaks = 0;
    int offset = startOffset;
    while (offset < endOffset) {
      char c = sourceCode.charAt(offset);
      offset++;
      if (!Character.isWhitespace(c)) {
        return false;
      }
      if (c == '\n' && ++lineBreaks > 1) {
        return false;
      }
    }
    return lineBreaks == 1;
  }

  private static List<EmbeddedPiece> projectRoots(List<TemplateRoot> roots, MdxTextRangeSet exclusions) {
    List<TextRange> jsxRanges = new ArrayList<>();
    for (TemplateRoot root : roots) {
      if (root.kind() == RootKind.JSX) {
        jsxRanges.add(root.range());
      }
    }
    MdxTextRangeSet projectedJsxRanges = MdxTextRangeSet.of(jsxRanges).subtract(exclusions);
    int projectedJsxIndex = 0;
    List<EmbeddedPiece> result = new ArrayList<>();
    for (TemplateRoot root : roots) {
      TextRange rootRange = root.range();
      if (root.kind() != RootKind.JSX) {
        result.add(new EmbeddedPiece(rootRange, root.kind()));
        continue;
      }
      while (projectedJsxIndex < projectedJsxRanges.size() &&
             projectedJsxRanges.get(projectedJsxIndex).getStartOffset() < rootRange.getEndOffset()) {
        TextRange range = projectedJsxRanges.get(projectedJsxIndex);
        if (range.getEndOffset() > rootRange.getStartOffset()) {
          result.add(new EmbeddedPiece(
            new TextRange(Math.max(range.getStartOffset(), rootRange.getStartOffset()), Math.min(range.getEndOffset(), rootRange.getEndOffset())),
            RootKind.JSX
          ));
        }
        if (range.getEndOffset() > rootRange.getEndOffset()) {
          break;
        }
        projectedJsxIndex++;
      }
    }
    return result;
  }

  private static @Nullable RootKind rootKind(IElementType type) {
    if (type == MdxMarkdownLibElementTypes.MDX_ESM_BLOCK) {
      return RootKind.ESM;
    }
    if (type == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT || type == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT) {
      return RootKind.JSX;
    }
    if (type == MdxMarkdownLibElementTypes.MDX_EXPRESSION) {
      return RootKind.EXPRESSION;
    }
    return null;
  }

  private static boolean endsWithSemicolon(CharSequence sourceCode, TextRange range) {
    int offset = range.getEndOffset() - 1;
    while (offset >= range.getStartOffset() && Character.isWhitespace(sourceCode.charAt(offset))) {
      offset--;
    }
    return offset >= range.getStartOffset() && sourceCode.charAt(offset) == ';';
  }

  private record TemplateStructure(List<EmbeddedPiece> pieces, MdxTextRangeSet opaqueRanges) {
  }

  private record TemplateRoot(TextRange range, RootKind kind) {
  }

  private record EmbeddedPiece(TextRange range, RootKind rootKind) {
  }

  private sealed interface Modification {
    int offset();

    int priority();

    record Remove(int offset, String text) implements Modification {
      @Override
      public int priority() {
        return 0;
      }
    }

    record Outer(TextRange range) implements Modification {
      @Override
      public int offset() {
        return range.getStartOffset();
      }

      @Override
      public int priority() {
        return 1;
      }
    }
  }

  private enum RootKind {
    ESM(true),
    JSX(true),
    EXPRESSION(false);

    final boolean requiresStatementSeparator;

    RootKind(boolean requiresStatementSeparator) {
      this.requiresStatementSeparator = requiresStatementSeparator;
    }
  }

  private static final class LinePrefixIndex {
    private int[] myEntries = new int[32];
    private int myEntryCount;

    LinePrefixIndex(CharSequence sourceCode) {
      int lineStart = 0;
      while (lineStart <= sourceCode.length()) {
        int offset = lineStart;
        int firstContent = sourceCode.length();
        while (offset < sourceCode.length()) {
          char c = sourceCode.charAt(offset);
          if (c == '\n') {
            break;
          }
          if (firstContent == sourceCode.length() && !Character.isWhitespace(c)) {
            firstContent = offset;
          }
          offset++;
        }
        add(lineStart, firstContent);
        if (offset >= sourceCode.length()) {
          break;
        }
        lineStart = offset + 1;
      }
    }

    int opaqueStart(int offset) {
      int low = 0;
      int high = myEntryCount;
      while (low + 1 < high) {
        int middle = (low + high) >>> 1;
        if (lineStart(middle) <= offset) {
          low = middle;
        }
        else {
          high = middle;
        }
      }
      return firstContentOffset(low) >= offset ? lineStart(low) : offset;
    }

    private void add(int lineStart, int firstContentOffset) {
      if (myEntryCount * 2 == myEntries.length) {
        myEntries = Arrays.copyOf(myEntries, myEntries.length * 2);
      }
      myEntries[myEntryCount * 2] = lineStart;
      myEntries[myEntryCount * 2 + 1] = firstContentOffset;
      myEntryCount++;
    }

    private int lineStart(int index) {
      return myEntries[index * 2];
    }

    private int firstContentOffset(int index) {
      return myEntries[index * 2 + 1];
    }
  }

  /**
   * The fake file type of the template file: its JavaScript is of the {@link MdxJSLanguage} version.
   */
  private static final class MdxJSTemplateFileType extends TemplateFileType implements JavaScriptFileTypeWithVersion {
    private MdxJSTemplateFileType(Language language) {
      super(language);
    }

    @RequiredReadAction
    @Override
    public LanguageVersion getLanguageVersion(@Nullable Module module, @Nullable VirtualFile virtualFile) {
      return MdxJSLanguage.getInstance();
    }
  }
}
