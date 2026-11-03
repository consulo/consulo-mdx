package org.intellij.plugin.mdx.highlighting;

import consulo.codeEditor.HighlighterIterator;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesKey;
import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor highlighter of MDX files: the Markdown layer and the JavaScript/JSX layer of the embedded code. The
 * highlighter runs over the text without an editor.
 */
@ExtendWith(ConsuloApplicationLoader.class)
public class MdxEditorHighlighterTest {
  private record Token(int start, String text, List<String> keys) {
  }

  private static List<Token> editorHighlightingTokens(String text) {
    MdxEditorHighlighter highlighter = new MdxEditorHighlighter(emptyScheme());
    highlighter.setText(text);
    List<Token> tokens = new ArrayList<>();
    HighlighterIterator it = highlighter.createIterator(0);
    while (!it.atEnd()) {
      List<String> keys = new ArrayList<>();
      for (TextAttributesKey key : it.getTextAttributesKeys()) {
        keys.add(serializeKey(key));
      }
      tokens.add(new Token(it.getStart(), text.substring(it.getStart(), it.getEnd()), keys));
      it.advance();
    }
    return tokens;
  }

  /**
   * A color scheme without colors: the light test application has no color scheme manager, and only the attribute keys
   * of the tokens are checked.
   */
  private static EditorColorsScheme emptyScheme() {
    return (EditorColorsScheme)Proxy.newProxyInstance(MdxEditorHighlighterTest.class.getClassLoader(), new Class[]{EditorColorsScheme.class},
                                                      (proxy, method, args) -> {
      Class<?> type = method.getReturnType();
      if (type == TextAttributes.class) {
        return new TextAttributes();
      }
      if (type == boolean.class) {
        return false;
      }
      if (type == int.class) {
        return 0;
      }
      if (type == float.class) {
        return 0f;
      }
      if (type == String.class) {
        return "empty";
      }
      if (method.getName().equals("equals")) {
        return proxy == args[0];
      }
      return null;
    });
  }

  private static String testDataText(String name) throws IOException {
    try (InputStream stream = MdxEditorHighlighterTest.class.getResourceAsStream("/highlight/" + name + ".mdx")) {
      Objects.requireNonNull(stream, name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  /**
   * The external name and the fallback chain of a key.
   */
  private static String serializeKey(TextAttributesKey key) {
    TextAttributesKey fallback = key.getFallbackAttributeKey();
    return fallback == null ? key.getExternalName() : key.getExternalName() + " => " + serializeKey(fallback);
  }

  /**
   * The key chain of the first token that exactly equals {@code tokenText}.
   */
  private static List<String> keysOf(List<Token> tokens, String tokenText) {
    Token match = tokens.stream().filter(it -> it.text().equals(tokenText)).findFirst().orElse(null);
    assertNotNull(match, "Editor highlighter produced no token equal to '" + tokenText + "'. Tokens: " + tokens);
    return match.keys();
  }

  /**
   * The key chain of the token covering {@code offset}.
   */
  private static List<String> keysAt(List<Token> tokens, int offset) {
    Token match = tokens.stream().filter(it -> offset >= it.start() && offset < it.start() + it.text().length()).findFirst().orElse(null);
    assertNotNull(match, "No editor token covers offset " + offset + ". Tokens: " + tokens);
    return match.keys();
  }

  private static boolean anyContains(List<String> keys, String part) {
    return keys.stream().anyMatch(it -> it.contains(part));
  }

  @Test
  public void testJsxTagAndAttributeAreColoredAsJsx() throws IOException {
    List<Token> tokens = editorHighlightingTokens(testDataText("JsxTagAndAttributeAreColoredAsJsx"));
    assertTrue(anyContains(keysOf(tokens, "Button"), "XML_TAG_NAME"),
               "JSX tag name `Button` must carry an XML/JSX tag-name color, got " + keysOf(tokens, "Button"));
    assertTrue(anyContains(keysOf(tokens, "kind"), "XML_ATTRIBUTE_NAME"),
               "JSX attribute name `kind` must carry an XML/JSX attribute color, got " + keysOf(tokens, "kind"));
  }

  @Test
  public void testMismatchedMultilineTagNamesRemainColoredAsJsx() {
    List<Token> tokens = editorHighlightingTokens("<dix>\n    Hello\n</div>");
    assertTrue(anyContains(keysOf(tokens, "dix"), "XML_TAG_NAME"), "A renamed opening tag must keep JSX coloring");
    assertTrue(anyContains(keysOf(tokens, "div"), "XML_TAG_NAME"), "The mismatched closing tag must keep JSX coloring");
  }

  @Test
  public void testNestedMultilineTemplateExpressionIsColoredAsJavaScript() {
    String text = "<A>\n<B>{`a\nb\nc`}</B>\n</A>";
    List<Token> tokens = editorHighlightingTokens(text);

    StringBuilder joined = new StringBuilder();
    tokens.forEach(it -> joined.append(it.text()));
    assertEquals(text, joined.toString());
    assertTrue(anyContains(keysAt(tokens, text.indexOf("\nb\n") + 1), "JS.STRING"));
  }

  @Test
  public void testFlowExpressionIsColoredAsJs() throws IOException {
    List<Token> tokens = editorHighlightingTokens(testDataText("FlowExpressionIsColoredAsJs"));
    assertTrue(anyContains(keysOf(tokens, "typeof"), "JS.KEYWORD"),
               "`typeof` inside the {expression} must carry a JS keyword color, got " + keysOf(tokens, "typeof"));
    assertTrue(anyContains(keysOf(tokens, "{"), "JS.BRACES"),
               "the expression braces must carry a JS braces color, got " + keysOf(tokens, "{"));
  }

  @Test
  public void testJsInExpressionAttributeIsColoredAsJs() throws IOException {
    List<Token> tokens = editorHighlightingTokens(testDataText("JsInExpressionAttributeIsColoredAsJs"));
    assertTrue(anyContains(keysOf(tokens, "const x = 1"), "JS.STRING"),
               "the template literal in `code={`...`}` must carry a JS string color, got " + keysOf(tokens, "const x = 1"));
  }

  @Test
  public void testEsmInListIsNotColoredAsJavaScript() throws IOException {
    String text = testDataText("EsmInListIsNotColoredAsJavaScript");
    List<Token> tokens = editorHighlightingTokens(text);
    List<String> topLevelKeys = keysAt(tokens, text.indexOf("import bbb from"));
    assertTrue(anyContains(topLevelKeys, "JS.KEYWORD"), "top-level ESM must carry a JS keyword color, got " + topLevelKeys);

    List<String> listKeys = keysAt(tokens, text.lastIndexOf("import bbb"));
    assertFalse(anyContains(listKeys, "JS.KEYWORD"), "List-item `import` must remain Markdown text: " + listKeys);
  }

  @Test
  public void testImportAndExportInParagraphsAreNotColoredAsJavaScript() {
    String text = "Some prose.\nimport these helpers before you start.\n\n" +
                  "Some more prose.\nexport the virtual machine as an image.";
    List<Token> tokens = editorHighlightingTokens(text);
    for (String keyword : List.of("import", "export")) {
      List<String> keys = keysAt(tokens, text.indexOf(keyword));
      assertFalse(anyContains(keys, "JS.KEYWORD"), "The prose keyword '" + keyword + "' must use Markdown colors: " + keys);
    }
  }

  @Test
  public void testInlineExpressionIsColoredAsJs() throws IOException {
    String blockText = testDataText("BlockLevelExpression");
    List<Token> block = editorHighlightingTokens(blockText);
    List<String> blockPlusKeys = keysAt(block, blockText.indexOf('+'));
    assertTrue(anyContains(blockPlusKeys, "JS."), "the `+` in a block-level {expression} should carry a JS color, got " + blockPlusKeys);

    String inlineText = testDataText("InlineTextExpression");
    List<Token> inline = editorHighlightingTokens(inlineText);
    assertEquals(blockPlusKeys, keysAt(inline, inlineText.indexOf('+')),
                 "the `+` of an inline `{1 + 2}` expression must be colored as in a block-level flow expression");
  }

  @Test
  public void testUnterminatedBraceInProseIsColoredAsJs() {
    List<Token> tokens = editorHighlightingTokens("some text {");
    assertTrue(anyContains(keysOf(tokens, "{"), "JS.BRACES"), "an unterminated `{` in prose must carry a JS braces color, got " + keysOf(tokens, "{"));
  }

  @Test
  public void testUnterminatedBraceInJsxBodyIsColoredAsJs() {
    List<Token> tokens = editorHighlightingTokens("<div>\n  {\n</div>");
    assertTrue(anyContains(keysOf(tokens, "{"), "JS.BRACES"),
               "an unterminated `{` in a JSX element's body must carry a JS braces color, got " + keysOf(tokens, "{"));
  }

  @Test
  public void testFencedCodeColorInsideJsxMatchesTopLevel() throws IOException {
    List<Token> topLevel = editorHighlightingTokens(testDataText("TopLevelFencedCode"));
    List<Token> insideJsx = editorHighlightingTokens(testDataText("FencedCodeInsideJsx"));
    assertEquals(keysOf(topLevel, "const answer = 42"), keysOf(insideJsx, "const answer = 42"),
                 "the ```js fence body inside <TabItem> must be colored the same as a top-level ```js fence");
  }
}
