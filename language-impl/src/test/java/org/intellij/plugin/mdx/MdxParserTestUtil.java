package org.intellij.plugin.mdx;

import consulo.language.ast.IElementType;
import org.intellij.markdown.MarkdownElementType;
import org.intellij.markdown.parser.CancellationToken;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.markdown.parser.MarkdownProductionSink;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.plugin.mdx.lang.parse.MdxFlavourDescriptor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * Parses MDX with the markdown engine. The engine builds no tree, so the parse result is exposed as {@link Node}s
 * (depth-first, parents before children).
 */
final class MdxParserTestUtil {
  private MdxParserTestUtil() {
  }

  record Node(IElementType type, int startOffset, int endOffset, @Nullable Node parent) {
    String text(CharSequence source) {
      return source.subSequence(startOffset, endOffset).toString();
    }

    @Override
    public String toString() {
      return type + "[" + startOffset + ", " + endOffset + ")";
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  /**
   * All nodes of the MDX parse of {@code text}, inline content included.
   */
  static List<Node> parseMdxNodes(CharSequence text) {
    MarkdownParser parser = new MarkdownParser(MdxFlavourDescriptor.INSTANCE, true, CancellationToken.NON_CANCELLABLE);
    List<SequentialParser.Node> production = parser.parseBlocks(new MarkdownElementType("MDX_TEST_ROOT"), text);
    // open events in document order, closed by the sink: a parent comes before its children
    List<int[]> frames = new ArrayList<>();
    List<IElementType> types = new ArrayList<>();
    Deque<Integer> open = new ArrayDeque<>();
    parser.walkBlocks(production, text, true, new MarkdownProductionSink() {
      @Override
      public void open(IElementType type, int startOffset) {
        int parent = open.isEmpty() ? -1 : open.peek();
        frames.add(new int[]{startOffset, -1, parent});
        types.add(type);
        open.push(frames.size() - 1);
      }

      @Override
      public void close(IElementType type, int endOffset) {
        frames.get(open.pop())[1] = endOffset;
      }

      @Override
      public void leaf(IElementType type, int startOffset, int endOffset) {
        int parent = open.isEmpty() ? -1 : open.peek();
        frames.add(new int[]{startOffset, endOffset, parent});
        types.add(type);
      }
    });
    List<Node> nodes = new ArrayList<>(frames.size());
    for (int i = 0; i < frames.size(); i++) {
      int[] frame = frames.get(i);
      nodes.add(new Node(types.get(i), frame[0], frame[1], frame[2] == -1 ? null : nodes.get(frame[2])));
    }
    return nodes;
  }

  static void assertMdxFlowRangesNestedOrDisjoint(List<Node> flowElements, String description) {
    for (int left = 0; left < flowElements.size(); left++) {
      for (int right = left + 1; right < flowElements.size(); right++) {
        Node first = flowElements.get(left);
        Node second = flowElements.get(right);
        boolean nested = first.startOffset() <= second.startOffset() && first.endOffset() >= second.endOffset() ||
                         second.startOffset() <= first.startOffset() && second.endOffset() >= first.endOffset();
        boolean disjoint = first.endOffset() <= second.startOffset() || second.endOffset() <= first.startOffset();
        Assertions.assertTrue(nested || disjoint,
                              description + ": intersecting JSX ranges " + first.startOffset() + ".." + first.endOffset() +
                              " and " + second.startOffset() + ".." + second.endOffset());
      }
    }
  }

  static @Nullable Node jsxOwner(Node node, IElementType jsxTextElementType) {
    Node parent = node.parent();
    while (parent != null) {
      if (parent.type() == jsxTextElementType) return parent;
      parent = parent.parent();
    }
    return null;
  }

  static boolean hasAncestor(Node node, Node ancestor) {
    Node parent = node.parent();
    while (parent != null) {
      if (parent == ancestor) return true;
      parent = parent.parent();
    }
    return false;
  }

  static List<Node> filter(List<Node> nodes, Predicate<Node> predicate) {
    List<Node> result = new ArrayList<>();
    for (Node node : nodes) {
      if (predicate.test(node)) result.add(node);
    }
    return result;
  }

  /**
   * The nodes of one of the given types, in document order.
   */
  static List<Node> ofType(List<Node> nodes, IElementType... types) {
    return filter(nodes, node -> {
      for (IElementType type : types) {
        if (node.type() == type) return true;
      }
      return false;
    });
  }

  /**
   * The direct children of {@code parent}, in document order.
   */
  static List<Node> children(List<Node> nodes, Node parent) {
    return filter(nodes, node -> node.parent() == parent);
  }

  static Node single(List<Node> nodes, Predicate<Node> predicate, String message) {
    List<Node> result = filter(nodes, predicate);
    Assertions.assertEquals(1, result.size(), message + ": expected a single node, got " + result);
    return result.get(0);
  }

  static Node singleOfType(List<Node> nodes, IElementType type, String message) {
    return single(nodes, node -> node.type() == type, message);
  }

  static List<String> texts(List<Node> nodes, CharSequence source) {
    List<String> result = new ArrayList<>(nodes.size());
    for (Node node : nodes) {
      result.add(node.text(source));
    }
    return result;
  }
}
