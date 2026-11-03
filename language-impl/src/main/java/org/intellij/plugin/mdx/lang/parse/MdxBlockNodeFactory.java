package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;

import java.util.ArrayList;
import java.util.List;

final class MdxBlockNodeFactory {
  private MdxBlockNodeFactory() {
  }

  static List<SequentialParser.Node> createFlowElementNodes(MdxJsxScanner.Element element) {
    return createFlowElementNodes(element, 0, true);
  }

  static List<SequentialParser.Node> createFlowElementNodes(MdxJsxScanner.Element element, int shift, boolean includeRoot) {
    List<SequentialParser.Node> nodes = createElementNodes(element, shift);
    if (includeRoot) {
      nodes.add(MdxTextRangeSet.toMarkdownNode(element.range().shiftRight(shift), MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT));
    }
    return nodes;
  }

  static List<SequentialParser.Node> createEsmNodes(MdxEsmScanner.Block block) {
    return createEsmNodes(block, 0, true);
  }

  static List<SequentialParser.Node> createEsmNodes(MdxEsmScanner.Block block, int shift, boolean includeRoot) {
    List<SequentialParser.Node> nodes = new ArrayList<>(2);
    nodes.add(MdxTextRangeSet.toMarkdownNode(block.range().shiftRight(shift), MdxMarkdownLibTokenTypes.EMBEDDED_JS_CONTENT));
    if (includeRoot) {
      nodes.add(MdxTextRangeSet.toMarkdownNode(block.range().shiftRight(shift), MdxMarkdownLibElementTypes.MDX_ESM_BLOCK));
    }
    return nodes;
  }

  private static List<SequentialParser.Node> createElementNodes(MdxJsxScanner.Element element, int shift) {
    List<SequentialParser.Node> nodes = new ArrayList<>();
    for (MdxJsxScanner.Tag tag : element.tags()) {
      addTagContentNodes(nodes, tag, shift);
      var tagType = switch (tag.kind()) {
        case OPENING -> MdxMarkdownLibElementTypes.MDX_JSX_OPENING_ELEMENT;
        case CLOSING -> MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT;
        case SELF_CLOSING -> MdxMarkdownLibElementTypes.MDX_JSX_SELF_CLOSING_ELEMENT;
      };
      nodes.add(MdxTextRangeSet.toMarkdownNode(tag.range().shiftRight(shift), tagType));
    }
    for (TextRange expression : element.expressions()) {
      nodes.add(MdxTextRangeSet.toMarkdownNode(expression.shiftRight(shift), MdxMarkdownLibTokenTypes.EMBEDDED_JS_CONTENT));
      nodes.add(MdxTextRangeSet.toMarkdownNode(expression.shiftRight(shift), MdxMarkdownLibElementTypes.MDX_EXPRESSION));
    }
    return nodes;
  }

  private static void addTagContentNodes(List<SequentialParser.Node> nodes, MdxJsxScanner.Tag tag, int shift) {
    int offset = tag.range().getStartOffset();
    for (TextRange attribute : tag.attributes()) {
      addContentNode(nodes, new TextRange(offset, attribute.getStartOffset()), shift);
      addContentNode(nodes, attribute, shift);
      nodes.add(MdxTextRangeSet.toMarkdownNode(attribute.shiftRight(shift), MdxMarkdownLibElementTypes.MDX_JSX_ATTRIBUTE));
      offset = attribute.getEndOffset();
    }
    addContentNode(nodes, new TextRange(offset, tag.range().getEndOffset()), shift);
  }

  private static void addContentNode(List<SequentialParser.Node> nodes, TextRange range, int shift) {
    if (!range.isEmpty()) {
      nodes.add(MdxTextRangeSet.toMarkdownNode(range.shiftRight(shift), MdxMarkdownLibTokenTypes.EMBEDDED_JS_CONTENT));
    }
  }
}
