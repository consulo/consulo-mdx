package org.intellij.plugin.mdx;

import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.plugin.mdx.MdxParserTestUtil.Node;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownLibElementTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.intellij.plugin.mdx.MdxParserTestUtil.assertMdxFlowRangesNestedOrDisjoint;
import static org.intellij.plugin.mdx.MdxParserTestUtil.hasAncestor;
import static org.intellij.plugin.mdx.MdxParserTestUtil.ofType;
import static org.intellij.plugin.mdx.MdxParserTestUtil.parseMdxNodes;
import static org.intellij.plugin.mdx.MdxParserTestUtil.singleOfType;
import static org.intellij.plugin.mdx.MdxParserTestUtil.texts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fenced code blocks inside JSX flow elements and Markdown containers: fence ownership, opaque fence content and
 * recovery of unclosed fences at the active JSX closer.
 */
@ExtendWith(ConsuloApplicationLoader.class)
public class MdxCodeFenceProviderTest {
  @Test
  public void fenceDelimitersUseMarkdownOwnershipInsideJsx() {
    List<String> fences = List.of(
      "  ```tsx\n{value}\n  ```",
      "    ~~~md\n</not-a-tag>\n    ~~~",
      "````md\n```js\nconst x = 1\n```\n````"
    );
    for (String fence : fences) {
      String text = "<Box>\n" + fence + "\n</Box>";
      List<Node> nodes = parseMdxNodes(text);
      Node code = singleOfType(nodes, MarkdownElementTypes.CODE_FENCE, text);
      assertEquals(text.indexOf("\n</Box>"), code.endOffset(), text);
      Node parent = code.parent();
      assertNotNull(parent, text);
      assertEquals(MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT, parent.type(), text);
      assertEquals(text.length(), parent.endOffset(), text);
    }
  }

  @Test
  public void fenceInformationIsOpaqueBeforeJsxScansTheLine() {
    for (String prefix : List.of("", "> ")) {
      for (String indent : List.of("", "  ", "    ", "      ")) {
        String text = prefix + "<A>\n" + prefix + indent + "```lang </A>\n" + prefix + indent + "code\n" + prefix + indent + "```\n" +
                      prefix + "</A>";
        List<Node> nodes = parseMdxNodes(text);
        Node jsx = singleOfType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT, text);
        Node fence = singleOfType(nodes, MarkdownElementTypes.CODE_FENCE, text);

        assertEquals(text.length(), jsx.endOffset(), text);
        assertTrue(hasAncestor(fence, jsx), text);
        assertEquals(List.of(text.lastIndexOf("</A>")), startOffsets(ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT)),
                     text);
      }
    }
  }

  @Test
  public void unclosedFenceYieldsToActiveJsxCloser() {
    List<String> cases = List.of(
      "<div>\n    ``````\n</div>",
      "<div>\n    ~~~~~~\n</div>",
      "<>\n    ``````\n</>"
    );

    for (String text : cases) {
      List<Node> nodes = parseMdxNodes(text);
      int closerStart = text.lastIndexOf("</");
      Node fence = singleOfType(nodes, MarkdownElementTypes.CODE_FENCE, text);
      Node closingElement = singleOfType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT, text);

      assertTrue(fence.endOffset() <= closerStart, text + ": unclosed fence consumed the active JSX closer");
      assertEquals(closerStart, closingElement.startOffset(), text);
      assertEquals(text.length(), closingElement.endOffset(), text);
      assertTrue(nodes.stream().anyMatch(it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT &&
                                               it.startOffset() == 0 &&
                                               it.endOffset() == text.length()),
                 text + ": the enclosing JSX flow element did not recover");
    }
  }

  @Test
  public void unclosedFenceKeepsMismatchedCloserOpaque() {
    String text = "<div>\n``````\n</span>\n</div>";
    List<Node> nodes = parseMdxNodes(text);
    int mismatchedCloser = text.indexOf("</span>");
    int activeCloser = text.indexOf("</div>");
    Node fence = singleOfType(nodes, MarkdownElementTypes.CODE_FENCE, text);
    List<Node> closingElements = ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);

    assertTrue(fence.endOffset() >= mismatchedCloser + "</span>".length());
    assertTrue(fence.endOffset() <= activeCloser);
    assertEquals(List.of("</div>"), texts(closingElements, text));
  }

  @Test
  public void closedFenceKeepsMatchingCloserOpaque() {
    String text = "<div>\n``````md\n</div>\n``````\n</div>";
    List<Node> nodes = parseMdxNodes(text);
    Node fence = singleOfType(nodes, MarkdownElementTypes.CODE_FENCE, text);
    int matchingTextInsideFence = text.indexOf("</div>");
    int activeCloser = text.lastIndexOf("</div>");
    List<Node> closingElements = ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);

    assertTrue(fence.startOffset() < matchingTextInsideFence && fence.endOffset() > matchingTextInsideFence + "</div>".length());
    assertEquals(List.of(activeCloser), startOffsets(closingElements));
    assertTrue(nodes.stream().anyMatch(it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT &&
                                             it.startOffset() == 0 &&
                                             it.endOffset() == text.length()));
  }

  @Test
  public void closedFenceKeepsMatchingCloserOpaqueInMarkdownContainers() {
    List<String> cases = List.of(
      "- <div>\n  ``````md\n  </div>\n  ``````\n  </div>",
      "> <div>\n> ``````md\n> </div>\n> ``````\n> </div>"
    );

    for (String text : cases) {
      List<Node> nodes = parseMdxNodes(text);
      int matchingTextInsideFence = text.indexOf("</div>");
      int activeCloser = text.lastIndexOf("</div>");
      List<Node> fences = ofType(nodes, MarkdownElementTypes.CODE_FENCE);
      assertEquals(1, fences.size(), text + ": " + fences);
      Node fence = fences.get(0);
      List<Node> closingElements = ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);

      assertTrue(fence.startOffset() < matchingTextInsideFence && fence.endOffset() > matchingTextInsideFence + "</div>".length(), text);
      assertEquals(List.of(activeCloser), startOffsets(closingElements), text);
    }
  }

  @Test
  public void unclosedFenceRecoveryUsesListAndBlockquoteConstraints() {
    List<String> cases = List.of(
      "- <div>\n  ``````\n  </div>",
      "> <div>\n> ``````\n> </div>"
    );

    for (String text : cases) {
      List<Node> nodes = parseMdxNodes(text);
      int closerStart = text.lastIndexOf("</div>");
      List<Node> fences = ofType(nodes, MarkdownElementTypes.CODE_FENCE);
      List<Node> closingElements = ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);
      assertEquals(1, fences.size(), text + ": fences=" + fences);
      assertEquals(1, closingElements.size(), text + ": nodes=" + nodes);
      Node fence = fences.get(0);
      Node closingElement = closingElements.get(0);

      assertTrue(fence.endOffset() <= closerStart, text + ": container fence consumed the JSX closer");
      assertEquals(closerStart, closingElement.startOffset(), text);
    }
  }

  @Test
  public void nestedJsxRecoversAfterUnclosedFence() {
    String text = "<Outer>\n  <Inner>\n    ``````\n  </Inner>\n</Outer>";

    assertNestedFlowElements(text, "unclosed fence in nested JSX");
  }

  @Test
  public void unclosedFenceYieldsToAncestorJsxCloser() {
    String text = "<Outer>\n  <Inner>\n    ``````\n</Outer>";

    assertNestedFlowElements(text, "unclosed fence before ancestor closer");
  }

  private static void assertNestedFlowElements(CharSequence text, String description) {
    List<Node> flowElements = ofType(parseMdxNodes(text), MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT);

    assertEquals(2, flowElements.size(), description + ": " + flowElements);
    Set<List<Integer>> ranges = new HashSet<>();
    for (Node element : flowElements) {
      ranges.add(List.of(element.startOffset(), element.endOffset()));
    }
    assertEquals(flowElements.size(), ranges.size(), description + ": duplicate JSX ranges");
    long outerCount = flowElements.stream().filter(it -> it.startOffset() == 0 && it.endOffset() == text.length()).count();
    assertEquals(1, outerCount, description + ": outer JSX does not own the complete source range");

    Node deepest = flowElements.stream().min(Comparator.comparingInt(it -> it.endOffset() - it.startOffset())).orElseThrow();
    int ancestorFlows = 0;
    for (Node parent = deepest.parent(); parent != null; parent = parent.parent()) {
      if (parent.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT) ancestorFlows++;
    }
    assertEquals(1, ancestorFlows, description + ": " + flowElements);

    assertMdxFlowRangesNestedOrDisjoint(flowElements, description);
  }

  private static List<Integer> startOffsets(List<Node> nodes) {
    List<Integer> result = new ArrayList<>(nodes.size());
    for (Node node : nodes) {
      result.add(node.startOffset());
    }
    return result;
  }
}
