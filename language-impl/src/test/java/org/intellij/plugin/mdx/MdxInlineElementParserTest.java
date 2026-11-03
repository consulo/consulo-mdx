package org.intellij.plugin.mdx;

import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownLibElementTypes;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.intellij.plugin.mdx.MdxParserTestUtil.Node;
import static org.intellij.plugin.mdx.MdxParserTestUtil.hasAncestor;
import static org.intellij.plugin.mdx.MdxParserTestUtil.parseMdxNodes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ConsuloApplicationLoader.class)
public class MdxInlineElementParserTest {
  @Test
  public void nestedInlineElementsOwnTheirMarkdown() {
    String text = "Text <A><B>*one*</B><C>*two*</C></A>";
    List<Node> nodes = parseMdxNodes(text);
    List<Node> elements = filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
    assertEquals(List.of("<A><B>*one*</B><C>*two*</C></A>", "<B>*one*</B>", "<C>*two*</C>"), texts(elements, text));
    assertEquals(elements.get(0), elements.get(1).parent());
    assertEquals(elements.get(0), elements.get(2).parent());
    List<Node> emphasis = filter(nodes, it -> it.type() == MarkdownElementTypes.EMPH);
    assertEquals(List.of("*one*", "*two*"), texts(emphasis, text));
    assertEquals(elements.get(1), jsxOwner(emphasis.get(0)));
    assertEquals(elements.get(2), jsxOwner(emphasis.get(1)));
  }

  @Test
  public void parentEmphasisCanWrapAChildWithoutEnteringItsBody() {
    String text = "Text *outside <A>*before <B>**inside**</B> after*</A> tail*";
    List<Node> nodes = parseMdxNodes(text);
    List<Node> elements = filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
    assertEquals(2, elements.size());
    List<Node> emphasis = filter(nodes, it -> it.type() == MarkdownElementTypes.EMPH);
    assertEquals(2, emphasis.size());
    assertNull(jsxOwner(emphasis.get(0)));
    assertEquals(elements.get(0), jsxOwner(emphasis.get(1)));
    Node strong = single(nodes, it -> it.type() == MarkdownElementTypes.STRONG);
    assertEquals("**inside**", strong.text(text));
    assertEquals(elements.get(1), jsxOwner(strong));
  }

  @Test
  public void outerLinkCanWrapNestedInlineElements() {
    String text = "[before <A>one <B>two</B></A> after](./target)";
    List<Node> nodes = parseMdxNodes(text);
    Node link = single(nodes, it -> it.type() == MarkdownElementTypes.INLINE_LINK);
    List<Node> elements = filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
    assertEquals(2, elements.size());
    assertTrue(hasAncestor(elements.get(0), link));
    assertEquals(elements.get(0), jsxOwner(elements.get(1)));
  }

  @Test
  public void fragmentsAndSelfClosingChildrenKeepTheirOwners() {
    String text = "Text <A><><B /></></A>";
    List<Node> elements = filter(parseMdxNodes(text), it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
    assertEquals(List.of("<A><><B /></></A>", "<><B /></>", "<B />"), texts(elements, text));
    assertEquals(elements.get(0), jsxOwner(elements.get(1)));
    assertEquals(elements.get(1), jsxOwner(elements.get(2)));
  }

  @Test
  public void deeplyNestedInlineElementsKeepEveryOwner() {
    int depth = 200;
    String text = "Text " + "<A>".repeat(depth) + "*body*" + "</A>".repeat(depth);
    List<Node> nodes = parseMdxNodes(text);
    List<Node> elements = filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
    assertEquals(depth, elements.size());
    for (int index = 1; index < elements.size(); index++) {
      assertEquals(elements.get(index - 1), jsxOwner(elements.get(index)));
    }
    assertEquals(elements.get(elements.size() - 1), jsxOwner(single(nodes, it -> it.type() == MarkdownElementTypes.EMPH)));
  }

  private static @Nullable Node jsxOwner(Node node) {
    return MdxParserTestUtil.jsxOwner(node, MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
  }

  static List<Node> filter(List<Node> nodes, Predicate<Node> predicate) {
    List<Node> result = new ArrayList<>();
    for (Node node : nodes) {
      if (predicate.test(node)) result.add(node);
    }
    return result;
  }

  static Node single(List<Node> nodes, Predicate<Node> predicate) {
    List<Node> result = filter(nodes, predicate);
    assertEquals(1, result.size(), "expected a single node: " + result);
    return result.get(0);
  }

  static List<String> texts(List<Node> nodes, CharSequence source) {
    List<String> result = new ArrayList<>();
    for (Node node : nodes) {
      result.add(node.text(source));
    }
    return result;
  }
}
