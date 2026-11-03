package org.intellij.plugin.mdx;

import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.plugin.mdx.MdxParserTestUtil.Node;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownLibElementTypes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
 * Paragraphs inside JSX flow elements: their ranges end at the owner's closing tag, follow blank lines and block
 * interruptions, and stay owned by the innermost enclosing JSX element.
 */
@ExtendWith(ConsuloApplicationLoader.class)
public class MdxFlowParagraphTest {
  @Test
  public void wrappedParagraphInsideNestedJsx() {
    String text = "<div>\n    <p>this\n        is\n        broken</p>\n</div>";
    assertParagraphs(text, List.of("this\n        is\n        broken"), 2);
  }

  @Test
  public void paragraphBoundariesFollowTheirJsxOwner() {
    for (String openingBreak : List.of("", "\n")) {
      for (String closingBreak : List.of("", "\n")) {
        for (String indent : List.of("", "  ", "    ", "\t")) {
          for (int lineCount = 1; lineCount <= 4; lineCount++) {
            List<String> lines = new ArrayList<>();
            for (int line = 1; line <= lineCount; line++) {
              lines.add("line" + line);
            }
            String body = String.join("\n" + indent, lines);
            String element = "<p>" + openingBreak + body + closingBreak + "</p>";
            for (String text : List.of(element, element + "\n\n", "<div>\n" + element + "\n</div>")) {
              assertParagraphs(text, List.of(body), text.startsWith("<div>") ? 2 : 1);
            }
          }
        }
      }
    }
  }

  @Test
  public void paragraphsRemainSeparateAcrossBlankLinesAndSiblings() {
    assertParagraphs("<p>one\ntwo\n\nthree\nfour</p>\n\nafter", List.of("one\ntwo", "three\nfour", "after"));
    assertParagraphs("<div>\n<p>one\ntwo\nthree</p>\n<p>four\nfive\nsix</p>\n</div>",
                     List.of("one\ntwo\nthree", "four\nfive\nsix"), 3);
  }

  @Test
  public void paragraphStartsAfterMultilineOpeningTag() {
    assertParagraphs("<p\n  title=\"value\"\n>one\ntwo\nthree</p>", List.of("one\ntwo\nthree"));
    assertParagraphs("<p\n  title=\"value\"\n>\none\ntwo\nthree</p>", List.of("one\ntwo\nthree"));
  }

  @Test
  public void singleLineJsxDoesNotConsumeFollowingBlocks() {
    for (String[] tags : List.of(new String[]{"<p>", "</p>"}, new String[]{"<>", "</>"})) {
      for (String suffix : List.of("", "\n", "\n# After", "\n\nafter", "\n<p>after</p>")) {
        String text = tags[0] + "text" + tags[1] + suffix;
        List<String> expected = suffix.contains("after") ? List.of("text", "after") : List.of("text");
        List<Node> nodes = assertParagraphs(text, expected, suffix.contains("<p>") ? 2 : 1);
        if (suffix.contains("# After")) {
          assertEquals("# After", singleOfType(nodes, MarkdownElementTypes.ATX_1, text).text(text));
        }
      }
    }
  }

  @Test
  public void emptyJsxBodiesHaveNoParagraphs() {
    for (String text : List.of("<p></p>", "<p> </p>", "<></>", "<Box />")) {
      assertParagraphs(text, List.of());
      assertParagraphs(text + "\n\nafter", List.of("after"));
    }
  }

  @Test
  public void paragraphRangesPreserveMultilineWhitespace() {
    assertParagraphs("<p>text </p>", List.of("text"));
    for (String separator : List.of("\n", "\r\n")) {
      String body = "one  " + separator + "two ";
      for (String closingBreak : List.of("", separator, separator + "    ")) {
        assertParagraphs("<p>" + body + closingBreak + "</p>", List.of(body));
        assertParagraphs("<div>" + separator + "<p>" + body + closingBreak + "</p>" + separator + "</div>", List.of(body), 2);
      }
    }
  }

  @Test
  public void expressionsRemainInsideTheirParagraphs() {
    for (String expression : List.of("{name.toUpperCase()}", "{\nname.toUpperCase()\n}")) {
      String text = "<div>" + expression + "</div>";
      List<Node> nodes = assertParagraphs(text, List.of(expression));
      Node expressionNode = singleOfType(nodes, MdxMarkdownLibElementTypes.MDX_EXPRESSION, text);
      Node parent = expressionNode.parent();
      assertNotNull(parent, text);
      assertEquals(MarkdownElementTypes.PARAGRAPH, parent.type(), text);
    }
  }

  @Test
  public void unfinishedChildKeepsCompletedParagraphs() {
    String text = "<p>first\nsecond\n\n<Child>\nunfinished";
    List<Node> paragraphs = ofType(parseMdxNodes(text), MarkdownElementTypes.PARAGRAPH);
    assertEquals(List.of("first\nsecond", "unfinished"), texts(paragraphs, text));
  }

  @Test
  public void recoveryKeepsFollowingMarkdown() {
    for (String element : List.of("<div>\n  Hello\n</span>", "<Outer>\n<Inner>\ntext\n</Outer>")) {
      String text = element + "\n\n# After";
      List<Node> nodes = parseMdxNodes(text);
      Node heading = singleOfType(nodes, MarkdownElementTypes.ATX_1, text);
      assertEquals("# After", heading.text(text), text);
      assertEquals(nodes.get(0), heading.parent(), text);
    }
  }

  @Test
  @Disabled("Consulo's markdown engine emits the '>' markers of blockquote continuation lines as WHITE_SPACE, not as BLOCK_QUOTE tokens (plain Markdown too)")
  public void quotedParagraphKeepsQuoteMarkers() {
    String text = "> <Note>quoted note</Note>\n>\n> text after";
    List<Node> nodes = assertParagraphs(text, List.of("quoted note", "text after"));
    assertEquals(List.of("> ", ">", "> "), texts(ofType(nodes, MarkdownTokenTypes.BLOCK_QUOTE), text));
  }

  @Test
  public void wrappedParagraphPrefixesRemainParseable() {
    for (String text : List.of("<div>\n<p>one\ntwo\nthree</p>\n</div>", "<div>\n<p>one\ntwo\nthree</div>")) {
      for (int end = 0; end <= text.length(); end++) {
        parseMdxNodes(text.substring(0, end));
      }
    }
  }

  @Test
  public void fragmentsAndInlineContentKeepParagraphBoundaries() {
    assertParagraphs("<>one\n**two**\nthree</>", List.of("one\n**two**\nthree"));
    assertParagraphs("<p>one\n`</p>`\nthree</p>", List.of("one\n`</p>`\nthree"));
    assertParagraphs("<p>\none `code\nvalue </p>\ncode` three</p>", List.of("one `code\nvalue </p>\ncode` three"));
    assertParagraphs("<p>one\ntwo {value}\nthree</p>", List.of("one\ntwo {value}\nthree"));
  }

  @Test
  public void blocksInterruptParagraphsInsideJsx() {
    assertParagraphs("<p>one\ntwo\n# Heading\nthree\nfour</p>", List.of("one\ntwo", "three\nfour"));
    assertParagraphs("<p>one\ntwo\n```text\n</p>\n```\nthree\nfour</p>", List.of("one\ntwo", "three\nfour"));
    assertParagraphs("<p>one\ntwo\n- item\n\nthree\nfour</p>", List.of("one\ntwo", "item", "three\nfour"));
  }

  private static List<Node> assertParagraphs(String text, List<String> expected) {
    return assertParagraphs(text, expected, 1);
  }

  /**
   * Asserts the paragraph texts and the number of JSX flow elements; each paragraph must not overlap a closing tag
   * and must lie inside (and be a descendant of) its innermost enclosing JSX flow element.
   */
  private static List<Node> assertParagraphs(String text, List<String> expected, int expectedElementCount) {
    List<Node> nodes = parseMdxNodes(text);
    List<Node> paragraphs = ofType(nodes, MarkdownElementTypes.PARAGRAPH);
    assertEquals(expected, texts(paragraphs, text), text);
    List<Node> elements = ofType(nodes, MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT);
    assertEquals(expectedElementCount, elements.size(), text);
    for (Node paragraph : paragraphs) {
      assertTrue(nodes.stream().noneMatch(it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT &&
                                                it.startOffset() < paragraph.endOffset() &&
                                                paragraph.startOffset() < it.endOffset()), text);
      Node owner = elements.stream()
        .filter(it -> it.startOffset() <= paragraph.startOffset() && paragraph.startOffset() < it.endOffset())
        .min(Comparator.comparingInt(it -> it.endOffset() - it.startOffset()))
        .orElse(null);
      if (owner == null) continue;
      assertTrue(paragraph.endOffset() <= owner.endOffset(), text);
      assertTrue(hasAncestor(paragraph, owner), text);
    }
    assertMdxFlowRangesNestedOrDisjoint(elements, text);
    return nodes;
  }
}
