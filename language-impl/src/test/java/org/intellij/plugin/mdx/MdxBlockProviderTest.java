package org.intellij.plugin.mdx;

import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.plugin.mdx.MdxParserTestUtil.Node;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownLibElementTypes;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.intellij.plugin.mdx.MdxParserTestUtil.assertMdxFlowRangesNestedOrDisjoint;
import static org.intellij.plugin.mdx.MdxParserTestUtil.children;
import static org.intellij.plugin.mdx.MdxParserTestUtil.ofType;
import static org.intellij.plugin.mdx.MdxParserTestUtil.parseMdxNodes;
import static org.intellij.plugin.mdx.MdxParserTestUtil.single;
import static org.intellij.plugin.mdx.MdxParserTestUtil.singleOfType;
import static org.intellij.plugin.mdx.MdxParserTestUtil.texts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block-level structure of the MDX flavour: ESM blocks, JSX flow elements owning Markdown containers, and opaque
 * multiline expressions.
 */
@ExtendWith(ConsuloApplicationLoader.class)
public class MdxBlockProviderTest {
  private static final List<String> ESM_LIKE_LINES = List.of(
    "import these helpers before you start.",
    "export the virtual machine as an image.",
    "import Component from './component'",
    "export default Component"
  );

  @Test
  public void importAndExportDoNotInterruptParagraphs() {
    for (String line : ESM_LIKE_LINES) {
      for (int indent = 0; indent <= 3; indent++) {
        String text = "Some prose.\n" + " ".repeat(indent) + line;
        List<Node> nodes = parseMdxNodes(text);
        assertEquals(List.of(text), texts(ofType(nodes, MarkdownElementTypes.PARAGRAPH), text), text);
        assertTrue(ofType(nodes, MdxMarkdownLibElementTypes.MDX_ESM_BLOCK).isEmpty(), text);
      }
    }
  }

  @Test
  public void importAndExportStartBlocks() {
    for (String line : ESM_LIKE_LINES) {
      for (String prefix : List.of("", "Some prose.\n\n", "# Heading\n")) {
        String text = prefix + line;
        List<Node> esm = ofType(parseMdxNodes(text), MdxMarkdownLibElementTypes.MDX_ESM_BLOCK);
        assertEquals(List.of(line), texts(esm, text), text);
      }
    }
  }

  @Test
  public void listDedentsInsideJsxKeepSiblingItems() {
    for (String indent : List.of("", "  ", "    ", "      ", "\t")) {
      for (String prefix : List.of("", "> ")) {
        for (String marker : List.of("-", "1.", "- [ ]")) {
          String continuation = marker.equals("1.") ? "   " : "  ";
          String text = prefix + "<Box>\n" +
                        prefix + indent + marker + " one\n" +
                        prefix + indent + continuation + "- nested\n" +
                        prefix + indent + marker + " two\n" +
                        prefix + "</Box>";
          List<Node> nodes = parseMdxNodes(text);
          Node jsx = singleOfType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT, text);
          Node list = single(children(nodes, jsx),
                             it -> it.type() == MarkdownElementTypes.UNORDERED_LIST || it.type() == MarkdownElementTypes.ORDERED_LIST,
                             text);
          List<Node> items = ofType(children(nodes, list), MarkdownElementTypes.LIST_ITEM);
          assertEquals(2, items.size(), text);
          assertTrue(items.get(0).text(text).contains("one"), text);
          assertTrue(items.get(1).text(text).contains("two"), text);
          Node nested = singleOfType(children(nodes, items.get(0)), MarkdownElementTypes.UNORDERED_LIST, text);
          assertEquals(1, ofType(children(nodes, nested), MarkdownElementTypes.LIST_ITEM).size(), text);
          assertTrue(nested.text(text).contains("nested"), text);
          assertTrue(ofType(children(nodes, items.get(1)), MarkdownElementTypes.UNORDERED_LIST).isEmpty(), text);
          assertEquals(text.length(), jsx.endOffset(), text);
        }
      }
    }
  }

  @Test
  public void jsxInListDoesNotOutliveItsItemAfterDedent() {
    String text = "<div>\n    - outer\n\n      <section>\n  - inner\n    - child\n  </section>\n- sibling\n</div>";
    List<Node> nodes = parseMdxNodes(text);
    Node jsx = ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT).get(0);
    assertEquals(0, jsx.startOffset());
    assertEquals(text.length(), jsx.endOffset());
    assertTrue(nodes.stream().anyMatch(it -> it.type() == MarkdownElementTypes.LIST_ITEM && it.text(text).trim().equals("- sibling")));
  }

  @Test
  public void blockquoteDedentsInsideJsxKeepTheirParents() {
    for (String indent : List.of("", "  ", "    ", "      ", "\t")) {
      String text = "<Box>\n" + indent + "> outer\n" + indent + "> > inner\n" + indent + ">\n" + indent + "> after\n</Box>";
      List<Node> nodes = parseMdxNodes(text);
      Node jsx = singleOfType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT, text);
      List<Node> quotes = ofType(nodes, MarkdownElementTypes.BLOCK_QUOTE);
      assertEquals(2, quotes.size(), text);
      assertEquals(jsx, quotes.get(0).parent(), text);
      assertEquals(quotes.get(0), quotes.get(1).parent(), text);
      Node after = single(nodes, it -> it.type() == MarkdownElementTypes.PARAGRAPH && it.text(text).contains("after"), text);
      assertEquals(quotes.get(0), after.parent(), text);
      assertEquals(text.length(), jsx.endOffset(), text);
    }
  }

  @Test
  public void markdownLikeTextInExpressionAttributeBelongsToJsx() {
    String text = "Text <Alert value={[Target](./target.mdx)} /> end";
    List<Node> nodes = parseMdxNodes(text);

    assertFalse(ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT).isEmpty());
  }

  @Test
  public void nestedMultilineTemplateExpressionStaysOpaque() {
    for (String body : List.of("a\nb\nc", "<ProfileCard\n  name=\"Alice Dupont\"\n  isOnline={true}\n/>")) {
      String expression = "{`" + body + "`}";
      String text = "<Exercise>\n<CodeBlock language=\"tsx\">" + expression + "</CodeBlock>\n</Exercise>";
      List<Node> nodes = parseMdxNodes(text);

      assertTrue(nodes.stream().anyMatch(it -> it.type() == MdxMarkdownLibElementTypes.MDX_EXPRESSION && it.text(text).equals(expression)),
                 text);
      assertEquals(2, ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT).size(), text);
    }
  }

  @Test
  public void markdownBlocksResumeAfterMultilineJavaScript() {
    String expression = "{\n/*\n# hidden\n- hidden\n```js\nignored\n```\n*/\n1\n}";
    for (String opening : List.of("<B>" + expression, "<B value=" + expression + ">")) {
      String text = "<A>\n" + opening + "\n# after\n</B>\n</A>";
      List<Node> nodes = parseMdxNodes(text);
      Node heading = singleOfType(nodes, MarkdownElementTypes.ATX_1, text);

      assertEquals("# after", heading.text(text), text);
      assertTrue(ofType(nodes, MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.CODE_FENCE).isEmpty(), text);
    }
  }

  @Test
  public void expressionClosingLineStaysOpaque() {
    String expression = "{`a\nb\nc`}";
    String text = "<A>\n<B>" + expression + "\n# after\n</B>\n</A>";
    List<Node> nodes = parseMdxNodes(text);

    assertTrue(nodes.stream().anyMatch(it -> it.type() == MdxMarkdownLibElementTypes.MDX_EXPRESSION && it.text(text).equals(expression)));
    assertEquals("# after", singleOfType(nodes, MarkdownElementTypes.ATX_1, text).text(text));
    assertTrue(nodes.stream().noneMatch(it -> it.type() == MarkdownElementTypes.PARAGRAPH && it.text(text).startsWith("c")));
  }

  @Test
  public void nestedMultilineExpressionPrefixesRemainParseable() {
    String text = "<A>\n<B>{`a\nb\nc`}</B>\n</A>";
    for (int end = 1; end <= text.length(); end++) {
      parseMdxNodes(text.substring(0, end));
    }
  }

  @Test
  public void transientAutoCloseWithMixedNamesKeepsFlowRangesLaminar() {
    String text = "<Outer>\n<Item>\n<Data>\n<Item>\n<Leaf</Leaf>\n</Item>\n</Data>\n</Item>\n</Outer>";

    assertFlowRangesNestedOrDisjoint(text, "mixed-name auto-close intermediate");
  }

  @Test
  public void transientAutoCloseWithRepeatedNamesKeepsFlowRangesLaminar() {
    String text = "<Outer>\n<Item>\n<Item>\n<Item</Item>\n</Item>\n</Item>\n</Outer>";

    assertFlowRangesNestedOrDisjoint(text, "repeated-name auto-close intermediate");
  }

  @Test
  @Timeout(120)
  public void nestedJsxPrefixesRemainParseableThroughDepthOneHundred() {
    for (int depth = 1; depth <= 100; depth++) {
      Map<String, String> cases = new LinkedHashMap<>();
      cases.put("named tags", nestedElements(depth, null, it -> "Tag" + it));
      cases.put("fragments", nestedElements(depth, null, it -> null));
      cases.put("repeated names", nestedElements(depth, null, it -> "Item"));
      cases.put("auto-close intermediate", nestedElements(depth, "Pending", it -> "Tag" + it));

      for (Map.Entry<String, String> entry : cases.entrySet()) {
        List<Node> flowElements = ofType(parseMdxNodes(entry.getValue()), MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT);
        assertMdxFlowRangesNestedOrDisjoint(flowElements, entry.getKey() + " at depth " + depth);
      }
    }
  }

  private static void assertFlowRangesNestedOrDisjoint(CharSequence text, String description) {
    List<Node> flowElements = ofType(parseMdxNodes(text), MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT);
    assertFalse(flowElements.isEmpty(), description + ": no JSX flow elements");
    assertMdxFlowRangesNestedOrDisjoint(flowElements, description);
  }

  /**
   * {@code depth} nested elements around a body line; a {@code null} name is a fragment.
   */
  private static String nestedElements(int depth, @Nullable String incompleteChild, IntFunction<@Nullable String> nameAt) {
    List<@Nullable String> names = new ArrayList<>(depth);
    for (int i = 0; i < depth; i++) {
      names.add(nameAt.apply(i));
    }
    StringBuilder builder = new StringBuilder();
    for (String name : names) {
      builder.append(name == null ? "<>" : "<" + name + ">").append('\n');
    }
    if (incompleteChild != null) {
      builder.append('<').append(incompleteChild).append("</").append(incompleteChild).append(">\n");
    }
    else {
      builder.append("body\n");
    }
    for (int i = names.size() - 1; i >= 0; i--) {
      String name = names.get(i);
      builder.append(name == null ? "</>" : "</" + name + ">").append('\n');
    }
    return builder.toString().stripTrailing();
  }
}
