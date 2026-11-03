package com.intellij.mdx.backend.format;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.AbstractBlock;
import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.ChildAttributes;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.DocumentBasedFormattingModel;
import consulo.language.codeStyle.FormatterUtil;
import consulo.language.codeStyle.FormattingContext;
import consulo.language.codeStyle.FormattingDocumentModel;
import consulo.language.codeStyle.FormattingModel;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Spacing;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.WrapType;
import consulo.language.codeStyle.template.BlockWithParent;
import consulo.language.codeStyle.template.DataLanguageBlockWrapper;
import consulo.language.codeStyle.template.SimpleTemplateLanguageFormattingModelBuilder;
import consulo.language.codeStyle.template.TemplateLanguageBlock;
import consulo.language.codeStyle.template.TemplateLanguageBlockFactory;
import consulo.language.codeStyle.template.TemplateLanguageFormattingModelBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.xml.language.psi.XmlTag;
import consulo.xml.psi.formatter.xml.SyntheticBlock;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.intellij.plugin.mdx.lang.parse.MdxElementTypes;
import org.intellij.plugin.mdx.lang.parse.MdxTokenTypes;
import org.intellij.plugin.mdx.lang.template.MdxTemplateElementTypes;
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The formatter of MDX files: Markdown blocks with the blocks of the JavaScript/JSX formatter of the embedded code.
 * Markdown text outside JSX is split into words, so long lines can be wrapped; code fences, block quotes and link
 * definitions stay as they are.
 */
@ExtensionImpl
public class MdxFormattingModelBuilder extends TemplateLanguageFormattingModelBuilder {
  /**
   * Element types whose embedded JavaScript must not be reformatted by the JS formatter: inline JSX/expressions in
   * Markdown text.
   */
  private static final Set<IElementType> IGNORED_FOREIGN_ELEMENT_TYPES = Set.of(
    MdxElementTypes.MDX_EXPRESSION,
    MdxElementTypes.MDX_JSX_TEXT_ELEMENT
  );

  @Override
  public Language getLanguage() {
    return MdxLanguage.INSTANCE;
  }

  @Override
  public TemplateLanguageBlock createTemplateLanguageBlock(ASTNode node,
                                                           @Nullable Wrap wrap,
                                                           @Nullable Alignment alignment,
                                                           @Nullable List<DataLanguageBlockWrapper> foreignChildren,
                                                           CodeStyleSettings codeStyleSettings) {
    if (isWrapEligibleMarkdownText(node, foreignChildren)) {
      return new MdxTextWrappingBlock(this, codeStyleSettings, node, wrap, alignment);
    }

    PsiFile file = node.getPsi(PsiElement.class).getContainingFile();
    FormattingDocumentModel documentModel = FormattingDocumentModel.create(file);
    List<DataLanguageBlockWrapper> mdxForeignChildren = filterForeignChildren(node, foreignChildren);
    return new MdxBlock(this, codeStyleSettings, node, mdxForeignChildren, new MdxJsxHtmlPolicy(codeStyleSettings, documentModel));
  }

  private static boolean isWrapEligibleMarkdownText(ASTNode node, @Nullable List<DataLanguageBlockWrapper> foreignChildren) {
    ASTNode parent = node.getTreeParent();
    if (node.getElementType() != MarkdownTokenTypes.TEXT ||
        parent == null || parent.getElementType() != MarkdownElementTypes.PARAGRAPH ||
        (foreignChildren != null && !foreignChildren.isEmpty())) {
      return false;
    }

    for (ASTNode ancestor = parent; ancestor != null; ancestor = ancestor.getTreeParent()) {
      if (ancestor.getElementType() == MdxElementTypes.MDX_JSX_FLOW_ELEMENT ||
          ancestor.getElementType() == MdxElementTypes.MDX_JSX_TEXT_ELEMENT) {
        return false;
      }
    }
    return true;
  }

  @Override
  public FormattingModel createModel(FormattingContext formattingContext) {
    PsiFile file = formattingContext.getContainingFile();
    ASTNode node = formattingContext.getPsiElement().getNode();
    if (node != null && node.getElementType() == MdxTemplateElementTypes.OUTER_MARKDOWN_CONTENT) {
      return new SimpleTemplateLanguageFormattingModelBuilder() {
        @Override
        public Language getLanguage() {
          return MdxLanguage.INSTANCE;
        }
      }.createModel(formattingContext);
    }
    CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
    // Fall back to a no-op dummy model only while the PSI is transiently broken (e.g. an unbalanced `{`
    // typed before auto-close inserts `}`), where the parser mislabels tokens and the real formatter crashes.
    if (!isMdxPsiComplete(file)) {
      return new DocumentBasedFormattingModel(createDummyBlock(file.getNode()), file.getProject(), settings, file.getFileType(), file);
    }
    return new DocumentBasedFormattingModel(getRootBlock(file, file.getViewProvider(), settings), file.getProject(), settings,
                                            file.getFileType(), file);
  }

  /**
   * Valid leading/trailing whitespace nodes use the real model. False only when transiently incomplete MDX PSI (e.g. an
   * unclosed `{`/code fence swallowing a following JSX tag) mislabels non-blank JSX tokens as whitespace, leaving a gap
   * that crashes the formatter.
   */
  private static boolean isMdxPsiComplete(PsiFile file) {
    ASTNode fileNode = file.getNode();
    for (ASTNode child = fileNode == null ? null : fileNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      // containsWhiteSpacesOnly trusts the declared whitespace type of a node, so its text is verified too
      if (FormatterUtil.containsWhiteSpacesOnly(child) && !child.getChars().toString().isBlank()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean dontFormatMyModel() {
    return false;
  }

  private static @Nullable List<DataLanguageBlockWrapper> filterForeignChildren(ASTNode node,
                                                                                @Nullable List<DataLanguageBlockWrapper> foreignChildren) {
    if (foreignChildren == null || foreignChildren.isEmpty() || isSingleInlineJsxParagraph(node)) {
      return null;
    }

    Set<TextRange> ignoredRanges = new HashSet<>();
    collectIgnoredForeignRanges(node, foreignChildren, ignoredRanges);
    if (ignoredRanges.isEmpty()) {
      return foreignChildren;
    }

    List<DataLanguageBlockWrapper> result = new ArrayList<>();
    for (DataLanguageBlockWrapper foreignChild : foreignChildren) {
      if (!isIgnoredForeignChild(foreignChild, ignoredRanges)) {
        result.add(foreignChild);
      }
    }
    return result;
  }

  private static boolean isIgnoredForeignChild(DataLanguageBlockWrapper foreignChild, Set<TextRange> ignoredRanges) {
    for (TextRange range : ignoredRanges) {
      if (range.contains(foreignChild.getTextRange())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSingleInlineJsxParagraph(ASTNode node) {
    if (node.getElementType() != MarkdownElementTypes.PARAGRAPH) {
      return false;
    }

    ASTNode jsxChild = null;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        if (child.getElementType() != MdxElementTypes.MDX_JSX_TEXT_ELEMENT || jsxChild != null) {
          return false;
        }
        jsxChild = child;
      }
    }
    return jsxChild != null && jsxChild.getTextRange().equals(node.getTextRange());
  }

  /**
   * One walk collecting every host-tree range whose embedded JavaScript must stay untouched by the JS formatter: a lone
   * inline JSX paragraph, an inline JSX/expression element in Markdown text, or a plain ESM (import/export) statement
   * with no JSX in it. A plain import/export declaration stays opaque because reformatting one can trip a formatter
   * block-covering assertion when multiple ESM statements are packed onto one line; an ESM statement whose body contains
   * JSX is formatted like a .tsx file. A matched node is a boundary (added, not recursed into).
   */
  private static void collectIgnoredForeignRanges(ASTNode node, List<DataLanguageBlockWrapper> foreignChildren, Set<TextRange> ranges) {
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (isSingleInlineJsxParagraph(child)) {
        ranges.add(child.getTextRange());
      }
      else if (IGNORED_FOREIGN_ELEMENT_TYPES.contains(child.getElementType())) {
        ranges.add(child.getTextRange());
      }
      else if (child.getElementType() == MdxElementTypes.MDX_ESM_BLOCK && !esmBlockContainsJsx(child.getTextRange(), foreignChildren)) {
        ranges.add(child.getTextRange());
      }
      else {
        collectIgnoredForeignRanges(child, foreignChildren, ranges);
      }
    }
  }

  private static boolean esmBlockContainsJsx(TextRange esmRange, List<DataLanguageBlockWrapper> foreignChildren) {
    for (DataLanguageBlockWrapper foreignChild : foreignChildren) {
      if (esmRange.contains(foreignChild.getTextRange()) && containsJsxTag(foreignChild)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsJsxTag(DataLanguageBlockWrapper foreignChild) {
    ASTNode node = foreignChild.getNode();
    PsiElement psi = node == null ? null : node.getPsi();
    if (psi == null) {
      return false;
    }
    return psi instanceof XmlTag || PsiTreeUtil.findChildOfType(psi, XmlTag.class) != null;
  }

  private static List<TextRange> splitTextForWrapping(String text) {
    List<TextRange> ranges = new ArrayList<>();
    int start = -1;
    for (int index = 0; index < text.length(); index++) {
      char c = text.charAt(index);
      if (c == ' ' || c == '\t' || c == '\n') {
        if (start != -1) {
          ranges.add(new TextRange(start, index));
        }
        start = -1;
      }
      else if (start == -1) {
        start = index;
      }
    }
    if (start != -1) {
      ranges.add(new TextRange(start, text.length()));
    }
    return ranges;
  }

  /**
   * Splits a text token into word blocks, so Reformat Code has places to insert a wrap.
   */
  private static final class MdxTextWrappingBlock extends TemplateLanguageBlock {
    private MdxTextWrappingBlock(TemplateLanguageBlockFactory blockFactory,
                                 CodeStyleSettings settings,
                                 ASTNode node,
                                 @Nullable Wrap wrap,
                                 @Nullable Alignment alignment) {
      super(node, wrap, alignment, blockFactory, settings, null);
    }

    @Override
    protected IElementType getTemplateTextElementType() {
      return MdxTokenTypes.EMBEDDED_JS_CONTENT;
    }

    @Override
    public Indent getIndent() {
      return Indent.getNoneIndent();
    }

    @Override
    public boolean isLeaf() {
      return false;
    }

    @Override
    protected List<Block> buildChildren() {
      MarkdownCustomCodeStyleSettings markdownSettings = getSettings().getCustomSettings(MarkdownCustomCodeStyleSettings.class);
      Wrap wrapping = Wrap.createWrap(markdownSettings.WRAP_TEXT_IF_LONG ? WrapType.NORMAL : WrapType.NONE, false);
      List<Block> blocks = new ArrayList<>();
      for (TextRange range : splitTextForWrapping(myNode.getText())) {
        blocks.add(new MdxTextRangeBlock(myNode, range.shiftRight(myNode.getStartOffset()), wrapping));
      }
      return blocks;
    }

    @Override
    public Spacing getSpacing(@Nullable Block child1, Block child2) {
      MarkdownCustomCodeStyleSettings markdownSettings = getSettings().getCustomSettings(MarkdownCustomCodeStyleSettings.class);
      int maxSpaces = markdownSettings.FORCE_ONE_SPACE_BETWEEN_WORDS ? 1 : Integer.MAX_VALUE;
      return Spacing.createSpacing(1, maxSpaces, 0, markdownSettings.KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS, 0);
    }
  }

  private static final class MdxTextRangeBlock extends AbstractBlock {
    private final TextRange myRange;

    private MdxTextRangeBlock(ASTNode node, TextRange range, Wrap wrap) {
      super(node, wrap, null);
      myRange = range;
    }

    @Override
    public TextRange getTextRange() {
      return myRange;
    }

    @Override
    protected List<Block> buildChildren() {
      return List.of();
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, Block child2) {
      return null;
    }

    @Override
    public Indent getIndent() {
      return Indent.getNoneIndent();
    }

    @Override
    public boolean isLeaf() {
      return true;
    }
  }

  private static final class MdxBlock extends TemplateLanguageBlock {
    private final MdxJsxHtmlPolicy myJsxPolicy;

    private MdxBlock(TemplateLanguageBlockFactory blockFactory,
                     CodeStyleSettings settings,
                     ASTNode node,
                     @Nullable List<DataLanguageBlockWrapper> foreignChildren,
                     MdxJsxHtmlPolicy jsxPolicy) {
      super(blockFactory, settings, node, foreignChildren);
      myJsxPolicy = jsxPolicy;
    }

    @Override
    protected IElementType getTemplateTextElementType() {
      return MdxTokenTypes.EMBEDDED_JS_CONTENT;
    }

    @Override
    public Indent getIndent() {
      Indent listItemChildIndent = getListItemChildIndent();
      if (listItemChildIndent != null) {
        return listItemChildIndent;
      }
      if (MarkdownTokenTypeSets.LISTS.contains(myNode.getElementType()) || myNode.getElementType() == MarkdownElementTypes.BLOCK_QUOTE) {
        DataLanguageBlockWrapper foreignParent = getForeignBlockParent();
        ASTNode foreignNode = foreignParent == null ? null : foreignParent.getNode();
        if (foreignNode != null && foreignNode.getPsi() instanceof XmlTag tag && myJsxPolicy.indentChildrenOf(tag)) {
          return Indent.getNormalIndent();
        }
      }
      if (MarkdownCodeFenceUtils.isCodeFence(myNode)) {
        for (ASTNode ancestor = myNode.getTreeParent(); ancestor != null; ancestor = ancestor.getTreeParent()) {
          if (ancestor.getElementType() == MdxElementTypes.MDX_JSX_FLOW_ELEMENT) {
            return Indent.getNormalIndent();
          }
        }
      }
      return Indent.getNoneIndent();
    }

    private @Nullable Indent getListItemChildIndent() {
      ASTNode parentItem = myNode.getTreeParent();
      if (parentItem == null || parentItem.getElementType() != MarkdownElementTypes.LIST_ITEM) {
        return null;
      }
      if (getForeignBlockParent() == null) {
        return null;
      }
      PsiFile file = myNode.getPsi(PsiElement.class).getContainingFile();
      Document document = file == null ? null : file.getViewProvider().getDocument();
      if (document == null) {
        return null;
      }
      int indent = contentColumn(myNode, document) - contentColumn(parentItem, document);
      // Paragraphs start after the marker. Apply their offset to continuation lines too.
      return Indent.getIndent(Indent.Type.SPACES, Math.max(indent, 0), false, true);
    }

    private static int contentColumn(ASTNode node, Document document) {
      CharSequence text = document.getImmutableCharSequence();
      int offset = CharArrayUtil.shiftForward(text, node.getStartOffset(), " \t");
      int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
      // Markdown uses tab stops every four columns.
      int column = 0;
      for (int index = lineStart; index < offset; index++) {
        column += text.charAt(index) == '\t' ? 4 - column % 4 : 1;
      }
      return column;
    }

    @Override
    public boolean isLeaf() {
      if (myNode.getElementType() == MarkdownElementTypes.LINK_DEFINITION) {
        return true;
      }
      // The Markdown parser represents block quote continuation markers (`>`) as whitespace nodes. The
      // template formatter normally omits whitespace-only children, which leaves those markers uncovered
      // when it is asked for indentation while hard-wrapping a quoted line. Keep the quote opaque instead.
      if (myNode.getElementType() == MarkdownElementTypes.BLOCK_QUOTE) {
        return true;
      }
      // A code fence is opaque to the formatter (its body is reformatted by MdxCodeFencePostFormatProcessor),
      // which also avoids tripping the "nonempty text is not covered by block" assertion when a
      // less-indented body line is mis-lexed so its leading chars land in a non-blank whitespace token.
      if (MarkdownCodeFenceUtils.isCodeFence(myNode)) {
        return true;
      }
      return super.isLeaf();
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, Block child2) {
      // Lists inside JSX follow the body indentation. Other lists preserve their spacing.
      if (isMarkdownListNode(myNode) && getForeignBlockParent() == null) {
        return Spacing.getReadOnlySpacing();
      }
      if (child1 != null && child2 instanceof MdxBlock mdxBlock && mdxBlock.shouldWrapBeforeClosingTag(mdxBlock.getForeignBlockParent())) {
        return createLineBreakSpacing();
      }
      return super.getSpacing(child1, child2);
    }

    @Override
    public ChildAttributes getChildAttributes(int newChildIndex) {
      // A new line inside a JSX flow element's body (e.g. Enter after a nested code fence) indents one level
      // under the opening tag; other blocks defer, preserving the caret line's existing indentation.
      if (myNode.getElementType() == MdxElementTypes.MDX_JSX_FLOW_ELEMENT) {
        return new ChildAttributes(Indent.getNormalIndent(), null);
      }
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }

    private @Nullable DataLanguageBlockWrapper getForeignBlockParent() {
      for (BlockWithParent parent = getParent(); parent != null; parent = parent.getParent()) {
        if (parent instanceof DataLanguageBlockWrapper wrapper && !(wrapper.getOriginal() instanceof SyntheticBlock)) {
          return wrapper;
        }
      }
      return null;
    }

    private static boolean isMarkdownListNode(ASTNode node) {
      return node.getElementType() == MarkdownElementTypes.LIST_ITEM || MarkdownTokenTypeSets.LISTS.contains(node.getElementType());
    }

    /**
     * A closing tag is its own MDX_JSX_CLOSING_ELEMENT block in the host tree, separate from the foreign XmlTag block, so
     * it doesn't inherit a line break from a child tag that wrapped: wrap before it iff at least one subtag would itself
     * force its own line.
     */
    private boolean shouldWrapBeforeClosingTag(@Nullable DataLanguageBlockWrapper parent) {
      if (myNode.getElementType() != MdxElementTypes.MDX_JSX_CLOSING_ELEMENT) {
        return false;
      }
      ASTNode parentNode = parent == null ? null : parent.getNode();
      if (parentNode == null || !(parentNode.getPsi() instanceof XmlTag parentTag)) {
        return false;
      }
      for (XmlTag subTag : parentTag.getSubTags()) {
        if (myJsxPolicy.getWrappingTypeForTagBegin(subTag) == WrapType.ALWAYS) {
          return true;
        }
      }
      return false;
    }

    private Spacing createLineBreakSpacing() {
      return Spacing.createSpacing(0, 0, 1, true, myJsxPolicy.getKeepBlankLines());
    }
  }
}
