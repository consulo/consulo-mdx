package org.intellij.plugin.mdx;

import consulo.document.util.TextRange;
import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.plugin.mdx.MdxParserTestUtil.Node;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownCodeSpanScanner;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownLibElementTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.intellij.plugin.mdx.MdxInlineElementParserTest.filter;
import static org.intellij.plugin.mdx.MdxInlineElementParserTest.single;
import static org.intellij.plugin.mdx.MdxParserTestUtil.hasAncestor;
import static org.intellij.plugin.mdx.MdxParserTestUtil.parseMdxNodes;
import static org.intellij.plugin.mdx.MdxScannerTestSupport.lineEnds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ConsuloApplicationLoader.class)
public class MdxMarkdownCodeSpanScannerTest {
  @Test
  public void inlineCodeSpanIsOpaqueInsideInlineJsx() {
    CharSequence text = "before <span>`</span>` body</span> after";
    List<Node> nodes = parseMdxNodes(text);
    Node jsx = single(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
    Node codeSpan = single(nodes, it -> it.type() == MarkdownElementTypes.CODE_SPAN);

    assertEquals("<span>`</span>` body</span>", jsx.text(text));
    assertTrue(hasAncestor(codeSpan, jsx));
  }

  @Test
  public void codeSpanIsOpaqueInsideFlowJsx() {
    String text = "<div>\n`</div>`\n</div>";
    List<Node> nodes = parseMdxNodes(text);
    Node jsx = single(nodes, it ->
      it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT && it.startOffset() == 0 && it.endOffset() == text.length());
    Node codeSpan = single(nodes, it -> it.type() == MarkdownElementTypes.CODE_SPAN);
    List<Node> closingElements = filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);

    assertEquals(List.of(text.lastIndexOf("</div>")), startOffsets(closingElements));
    assertTrue(hasAncestor(codeSpan, jsx));
  }

  @Test
  public void multilineCodeSpanIsOpaqueInsideFlowJsx() {
    String text = "<div>\n`before </div>\nafter`\n</div>";
    List<Node> nodes = parseMdxNodes(text);
    Node jsx = single(nodes, it ->
      it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT && it.startOffset() == 0 && it.endOffset() == text.length());
    Node codeSpan = single(nodes, it -> it.type() == MarkdownElementTypes.CODE_SPAN);
    List<Node> closingElements = filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);

    assertEquals(List.of(text.lastIndexOf("</div>")), startOffsets(closingElements));
    assertTrue(hasAncestor(codeSpan, jsx));
  }

  @Test
  public void unclosedCodeSpanDoesNotHideCloserAcrossMarkdownBlocks() {
    String text = "<div>\n`open\n\n</div>\n`later";
    int closerStart = text.indexOf("</div>");
    int closerEnd = closerStart + "</div>".length();
    List<Node> nodes = parseMdxNodes(text);
    Node jsx = single(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT && it.startOffset() == 0);
    Node closingElement = single(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT);

    assertEquals(closerEnd, jsx.endOffset());
    assertEquals(closerStart, closingElement.startOffset());
  }

  @Test
  public void headingCodeSpansStartAfterThePreviousParagraph() {
    for (int depth = 1; depth <= 6; depth++) {
      for (String prefix : List.of("", "> ")) {
        String text = prefix + "<A>\n" + prefix + "before `\n" + prefix + "#".repeat(depth) + " head `</A>`\n" + prefix + "</A>";
        List<Node> nodes = parseMdxNodes(text);
        Node jsx = single(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT);
        Node code = single(nodes, it -> it.type() == MarkdownElementTypes.CODE_SPAN);

        assertEquals(text.length(), jsx.endOffset(), text);
        assertEquals("`</A>`", code.text(text), text);
        String headingType = "Markdown:ATX_" + depth;
        boolean inHeading = false;
        for (Node parent = code.parent(); parent != null; parent = parent.parent()) {
          if (parent.type().toString().equals(headingType)) inHeading = true;
        }
        assertTrue(inHeading, text);
        assertTrue(hasAncestor(code, jsx), text);
        assertEquals(
          List.of(text.lastIndexOf("</A>")),
          startOffsets(filter(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT)),
          text
        );
      }
    }
  }

  @Test
  public void codeSpansFollowModifierAndSetextTransitions() {
    for (String middle : List.of("- item `</A>`", "> quote `</A>`", "---\nnext `</A>`")) {
      String text = "<A>\nbefore `\n" + middle + "\n</A>";
      List<Node> nodes = parseMdxNodes(text);
      Node jsx = single(nodes, it -> it.type() == MdxMarkdownLibElementTypes.MDX_JSX_FLOW_ELEMENT);
      Node code = single(nodes, it -> it.type() == MarkdownElementTypes.CODE_SPAN);
      assertEquals(text.length(), jsx.endOffset(), text);
      assertEquals("`</A>`", code.text(text), text);
      assertTrue(hasAncestor(code, jsx), text);
    }
  }

  @Test
  public void escapedBackticksCanCloseButCannotOpenCodeSpans() {
    String escapedOpener = "\\`not code`";
    String escapedCloser = "`code \\`";

    assertEquals(List.<TextRange>of(), scan(escapedOpener));
    assertEquals(List.of(new TextRange(0, escapedCloser.length())), scan(escapedCloser));
  }

  @Test
  public void incrementalSessionEmitsEachSpanOnce() {
    String text = "before `one`\nmiddle ``two``\nafter";
    MdxMarkdownCodeSpanScanner.Session session = new MdxMarkdownCodeSpanScanner.Session(text, 0);

    List<TextRange> spans = new ArrayList<>();
    for (int limit : lineEnds(text)) {
      spans.addAll(session.advanceTo(limit));
    }

    assertEquals(
      List.of(
        new TextRange(text.indexOf('`'), text.indexOf('`', text.indexOf('`') + 1) + 1),
        new TextRange(text.indexOf("``"), text.indexOf("``", text.indexOf("``") + 2) + 2)
      ),
      spans
    );
  }

  private static List<TextRange> scan(String text) {
    return new MdxMarkdownCodeSpanScanner.Session(text, 0).advanceTo(text.length());
  }

  private static List<Integer> startOffsets(List<Node> nodes) {
    List<Integer> result = new ArrayList<>();
    for (Node node : nodes) {
      result.add(node.startOffset());
    }
    return result;
  }
}
