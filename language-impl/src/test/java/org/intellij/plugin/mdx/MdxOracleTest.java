package org.intellij.plugin.mdx;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.xml.language.psi.XmlTag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests against the MDX AST of the reference toolchain (remark-mdx with remark-gfm): each test asserts the
 * PSI structure (real heading/list/code/expression nodes, not only the absence of parse errors).
 * <p>
 * These are light PSI tests: "no errors" means that no view-provider root has a {@code PsiErrorElement}. Highlighting
 * is not run, so ERROR-severity annotations and inspection results are not checked.
 */
public class MdxOracleTest extends MdxLightTestBase {
  /**
   * The fenced code must be a child of the enclosing JSX flow element in the MDX tree: no errors, opaque fence
   * content, an MDX ancestor of the fence spanning {@code <enclosingTagName>...</enclosingTagName>}, and no JSX tag
   * named one of {@code forbiddenTagNames}.
   */
  private void assertOpaqueFenceInsideJsx(String text, String code, String enclosingTagName, String... forbiddenTagNames) {
    assertNoErrors(text);
    assertOpaqueFenceContains(code);
    assertNoJsxTagNamed(forbiddenTagNames);

    String openTag = "<" + enclosingTagName;
    String closeTag = "</" + enclosingTagName + ">";
    PsiElement fenceNode = collectFenceContents().stream()
      .filter(it -> it.getText().contains(code) || code.contains(it.getText()))
      .findFirst()
      .orElse(null);
    assertNotNull(fenceNode, "Fenced code '" + code + "' must exist as a CODE_FENCE_CONTENT node. Fence nodes:\n" +
                             describe(collectFenceContents()));

    PsiElement jsxContainer = null;
    for (PsiElement parent = fenceNode.getParent(); parent != null && !(parent instanceof PsiFile); parent = parent.getParent()) {
      if (parent.getText().contains(openTag) && parent.getText().contains(closeTag)) {
        jsxContainer = parent;
        break;
      }
    }
    assertNotNull(jsxContainer, "Fenced code '" + code + "' must be a child of <" + enclosingTagName + "> in the MDX tree, " +
                                "but no MDX ancestor of the fence spans the whole JSX element");
    assertTrue(PsiTreeUtil.isAncestor(jsxContainer, fenceNode, false),
               "<" + enclosingTagName + "> element must be a PSI ancestor of the fenced code node");
  }

  // --- fenced code inside / alongside JSX ------------------------------------------------------

  /**
   * {@code <div>} is a flow element whose child is a code node with value "#include <stdio.h>"; {@code <stdio.h>} is
   * code, not a JSX tag.
   */
  @Test
  public void testCodeBlockInJsxHasNoErrors() {
    String text = """
      # Markdown

      <div>
      ```c
      #include <stdio.h>
      ```
      </div>

      # Markdown with errors""";
    assertOpaqueFenceInsideJsx(text, "#include <stdio.h>", "div", "stdio.h");
  }

  /**
   * Braces inside a fenced tsx block nested in JSX are code, not MDX expressions.
   */
  @Test
  public void testTsxFunctionCodeBlockInJsxHasNoErrors() {
    String text = """
      <div>
      ```tsx
      function foo() {
      }
      ```
      </div>""";
    assertOpaqueFenceInsideJsx(text, "function foo() {\n}", "div");
  }

  /**
   * A top-level fenced tsx block is a code node; {@code Promise<AddressDetailResponse>} is part of its opaque value.
   */
  @Test
  public void testTsxCodeBlockHasNoErrors() {
    String text = """
      ```tsx
      export interface Api {
        getDetails: (id: string) => Promise<AddressDetailResponse>;
      }
      ```""";
    assertNoErrors(text);
    assertOpaqueFenceContains("Promise<AddressDetailResponse>");
    assertNoJsxTagNamed("AddressDetailResponse");
  }

  /**
   * The {@code <Tabs>}/{@code <TabItem>} flow elements contain a code node; {@code Bar<Baz>} is opaque code.
   */
  @Test
  public void testGenericsInCodeBlockHaveNoErrors() {
    String text = """
      <Tabs values={[{label: "Kotlin", value: "kotlin"}]}>
      <TabItem value="kotlin">
      ```kotlin
      val bar: Bar<Baz> = Something()
      ```
      </TabItem>
      </Tabs>""";
    assertOpaqueFenceInsideJsx(text, "val bar: Bar<Baz> = Something()", "TabItem", "Baz");
  }

  /**
   * {@code <Source ... code={`...`}/>} is one flow element whose attribute is an expression; the following
   * {@code #### Selection Type} is a depth-4 heading, not swallowed by the JSX.
   */
  @Test
  public void testExpressionAttributeHasNoErrors() {
    String text = """
      import { Source } from '@storybook/addon-docs/blocks'

      <Source language="tsx" code={`
        const [state, setState] = useState<SomeType>()
      `}/>

      #### Selection Type

      Some text after.""";
    assertNoErrors(text);
    PsiElement heading = assertHasMarkdownHeading("Selection Type", 4);
    int sourceEnd = text.indexOf("/>");
    assertTrue(sourceEnd >= 0, "Test setup: '/>' not found in source");
    assertTrue(heading.getTextRange().getStartOffset() >= sourceEnd + 2,
               "'#### Selection Type' must start after the '/>' of <Source .../> (source offset " + (sourceEnd + 2) +
               "), but the heading was at " + heading.getTextRange());
  }

  // --- Markdown interleaving inside JSX --------------------------------------------------------

  /**
   * {@code <Callout>} is a flow element whose children include a real depth-2 heading and a list.
   */
  @Test
  public void testMarkdownInsideJsxHasNoErrors() {
    String text = """
      <Callout>
      ## Important

      - item one
      </Callout>""";
    assertNoErrors(text);
    PsiElement heading = assertHasMarkdownHeading("Important", 2);
    assertNestedInJsxElement(heading, "Callout");
    PsiElement list = firstContainer("UNORDERED_LIST");
    assertNotNull(list, "'- item one' inside <Callout> must be a Markdown list (UNORDERED_LIST), not opaque JSX_BLOCK_CONTENT");
    assertNestedInJsxElement(list, "Callout");
  }

  /**
   * The same interleaving with 2-space indentation.
   */
  @Test
  public void testMarkdownInsideJsxIndented() {
    String text = """
      <Box>
        ## Indented heading
        - list
      </Box>""";
    assertNoErrors(text);
    PsiElement heading = assertHasMarkdownHeading("Indented heading", 2);
    assertNestedInJsxElement(heading, "Box");
    PsiElement list = firstContainer("UNORDERED_LIST");
    assertNotNull(list, "The indented '- list' inside <Box> must be a Markdown list (UNORDERED_LIST), not opaque JSX_BLOCK_CONTENT");
    assertNestedInJsxElement(list, "Box");
  }

  @Test
  public void testNestedJsxKeepsBaseAndProjectedOwnershipAcrossMarkdownBlocks() {
    Map<String, String> cases = new LinkedHashMap<>();
    cases.put("empty list item", "<div>\n  <div>\n    - \n  </div>\n</div>");
    cases.put("unordered list item", "<div>\n  <div>\n    - item\n  </div>\n</div>");
    cases.put("ordered list item", "<div>\n  <div>\n    1. item\n  </div>\n</div>");
    cases.put("task list item", "<div>\n  <div>\n    - [ ] item\n  </div>\n</div>");
    cases.put("heading", "<div>\n  <div>\n    ## Heading\n  </div>\n</div>");
    cases.put("blockquote", "<div>\n  <div>\n    > quote\n  </div>\n</div>");
    cases.put("fence", "<div>\n  <div>\n    ```md\n    body\n    ```\n  </div>\n</div>");

    for (Map.Entry<String, String> entry : cases.entrySet()) {
      assertNestedJsxOwnership(entry.getValue(), 2, entry.getKey());
    }
  }

  @Test
  public void testThreeNestedJsxLevelsKeepBaseAndProjectedOwnership() {
    String text = "<div>\n  <div>\n    <div>\n      - item\n    </div>\n  </div>\n</div>";

    assertNestedJsxOwnership(text, 3, "three JSX levels");
  }

  /**
   * Both the MDX tree and the JavaScript tree must nest {@code expectedDepth} distinct {@code div} elements, the
   * outermost spanning the whole input.
   */
  private void assertNestedJsxOwnership(String text, int expectedDepth, String description) {
    configureByText("nested.mdx", text);

    List<PsiElement> baseElements = new ArrayList<>(nodesOfType(getFile(), "MDX_JSX_FLOW_ELEMENT").stream()
                                                      .filter(it -> it.getFirstChild() != null)
                                                      .toList());
    baseElements.sort(Comparator.comparingInt((PsiElement it) -> it.getTextRange().getLength()).reversed());
    assertEquals(expectedDepth, distinctRanges(baseElements).size(),
                 description + ": expected " + expectedDepth + " distinct MDX JSX elements, got\n" + describe(baseElements));
    assertEquals(text, baseElements.isEmpty() ? null : baseElements.get(0).getText(),
                 description + ": outer MDX JSX element must span the full input");
    for (int index = 0; index < baseElements.size() - 1; index++) {
      assertTrue(PsiTreeUtil.isAncestor(baseElements.get(index), baseElements.get(index + 1), true),
                 description + ": " + baseElements.get(index).getTextRange() + " must own " +
                 baseElements.get(index + 1).getTextRange() + " in the MDX PSI");
    }

    List<XmlTag> projectedTags = new ArrayList<>();
    Set<TextRange> seen = new LinkedHashSet<>();
    for (PsiFile root : allRoots()) {
      for (XmlTag tag : PsiTreeUtil.collectElementsOfType(root, XmlTag.class)) {
        if ("div".equals(tag.getName()) && seen.add(tag.getTextRange())) {
          projectedTags.add(tag);
        }
      }
    }
    projectedTags.sort(Comparator.comparingInt((XmlTag it) -> it.getTextRange().getLength()).reversed());
    assertEquals(expectedDepth, projectedTags.size(),
                 description + ": expected " + expectedDepth + " distinct projected XmlTag elements, got\n" + describe(projectedTags));
    assertEquals(text, projectedTags.get(0).getText(), description + ": outer projected JSX must span the full input");
    for (int index = 0; index < projectedTags.size() - 1; index++) {
      assertTrue(PsiTreeUtil.isAncestor(projectedTags.get(index), projectedTags.get(index + 1), true),
                 description + ": " + projectedTags.get(index).getTextRange() + " must own " +
                 projectedTags.get(index + 1).getTextRange() + " in the MdxJS PSI");
    }
  }

  private static Set<TextRange> distinctRanges(List<PsiElement> elements) {
    Set<TextRange> ranges = new LinkedHashSet<>();
    for (PsiElement element : elements) {
      ranges.add(element.getTextRange());
    }
    return ranges;
  }

  /**
   * {@code {/* *&#47;}} is an MDX expression whose body is a JavaScript block comment.
   */
  @Test
  public void testMdxComment() {
    assertNoErrors("{/* a comment */}");
    List<PsiComment> comments = new ArrayList<>();
    for (PsiFile root : allRoots()) {
      comments.addAll(PsiTreeUtil.collectElementsOfType(root, PsiComment.class));
    }
    assertTrue(comments.stream().anyMatch(it -> it.getText().contains("/* a comment */")),
               "expected a JS block comment for {/* */}, found: " + comments);
  }

  // --- inline JSX and JSX in Markdown block constructs ----------------------------------------

  /**
   * {@code <Icon />} is an inline JSX element inside emphasis, {@code <Logo />} inside a link.
   */
  @Test
  public void testInlineJsxInEmphasisHasNoErrors() {
    assertNoErrors("A paragraph with *emphasis and an <Icon /> inline* and a [<Logo />](https://x) link.");
    PsiElement emph = firstContainer("EMPH");
    assertNotNull(emph, "An emphasis node is expected around '<Icon /> inline'");
    List<PsiElement> iconTag = collect(emph, it -> typeName(it).equals("HTML_TAG") && it.getText().equals("<Icon />"));
    assertFalse(iconTag.isEmpty(),
                "'<Icon />' must be an inline tag nested in the emphasis node, but the emphasis subtree was:\n" +
                describe(collect(emph, it -> true)));
  }

  /**
   * A blockquote containing {@code <Note>quoted note</Note>}.
   */
  @Test
  public void testJsxInBlockquoteHasNoErrors() {
    assertNoErrors("> <Note>quoted note</Note>\n>\n> text after");
    PsiElement quote = firstContainer("BLOCK_QUOTE");
    assertNotNull(quote, "A blockquote container is expected");
    assertTrue(quote.getText().contains("<Note>quoted note</Note>"), "The <Note> JSX must live inside the blockquote");
  }

  /**
   * A list with list item children; the JSX elements are list item content.
   */
  @Test
  public void testJsxInListItemHasNoErrors() {
    assertNoErrors("- first item\n- <Item>second</Item>\n  - <Sub>nested</Sub>");
    assertNotNull(firstContainer("UNORDERED_LIST"), "An unordered list is expected");
    List<PsiElement> items = nodesOfType(getFile(), "LIST_ITEM");
    assertTrue(items.size() >= 2, "Expected >= 2 list items, found " + items.size());
  }

  /**
   * JSX fragments ({@code <>...</>}) are supported.
   */
  @Test
  public void testFragmentsHaveNoErrors() {
    assertNoErrors("<>\nfragment child\n</>");
  }

  /**
   * {@code \<} and {@code \{} are literal text: neither becomes an HTML_TAG nor an expression.
   */
  @Test
  public void testEscapesStayText() {
    assertNoErrors("Escaped \\< is not a tag and \\{ is not an expression.");
    List<PsiElement> escLt = collect(getFile(), it -> it.getText().equals("\\<"));
    List<PsiElement> escBrace = collect(getFile(), it -> it.getText().equals("\\{"));
    assertFalse(escLt.isEmpty(), "'\\<' fragment not found");
    assertFalse(escBrace.isEmpty(), "'\\{' fragment not found");
    assertEquals("TEXT", typeName(escLt.get(0)), "Escaped '\\<' must stay TEXT");
    assertEquals("TEXT", typeName(escBrace.get(0)), "Escaped '\\{' must stay TEXT");
    assertTrue(nodesOfTypeAllRoots("HTML_TAG").isEmpty(), "Escaped sequences must not produce any JSX tag");
  }

  @Test
  public void testMarkdownLikeTextInInlineJsxExpressionAttributeStaysJsx() {
    configureByText("Text <Alert value={[Target](./target.mdx)} /> end");
    assertTrue(jsxTagNames().contains("Alert"), "The inline Alert tag must remain in the MdxJS PSI");
  }

  /**
   * {@code <Comp a={1 < 2} b={useMemo<Foo>(() => x)} />} is one flow element.
   */
  @Test
  public void testOperatorInAttributeHasNoErrors() {
    assertNoErrors("<Comp a={1 < 2} b={useMemo<Foo>(() => x)} />");
    assertTrue(jsxTagNames().contains("Comp"), "A 'Comp' JSX element is expected in the JS root");
  }

  /**
   * A multiline JS expression attribute in a JSX opening tag keeps one surrounding JSX flow element.
   */
  @Test
  public void testMultilineExpressionAttributeInOpeningTagHasJsxFlowElement() {
    String text = """
      <div onClick={(e) => {
          console.log(e)
      }}>
          Hello
      </div>""";
    assertNoErrors(text);
    assertEquals(1, nodesOfType(getFile(), "MDX_JSX_FLOW_ELEMENT").stream().filter(it -> it.getText().contains("Hello")).count(),
                 "Expected one JSX flow element spanning the multiline opening tag and its body");
  }

  /**
   * As above, but the arrow function body also contains a blank line, which must not split the element.
   */
  @Test
  public void testMultilineExpressionAttributeWithBlankLineHasJsxFlowElement() {
    String text = """
      <div onClick={() => {
          console.log(1)

          console.log(2)
      }}>
          Hello
      </div>""";
    assertNoErrors(text);
    assertEquals(1, nodesOfType(getFile(), "MDX_JSX_FLOW_ELEMENT").stream().filter(it -> it.getText().contains("Hello")).count(),
                 "Expected one JSX flow element spanning the multiline opening tag (with a blank line inside its attribute " +
                 "expression) and its body");
  }

  /**
   * {@code <Tabs tabs={[<FileWithIcon .../>, ...]} />}: JSX inside a JS array attribute.
   */
  @Test
  public void testJsxInAttributeArrayHasNoErrors() {
    assertNoErrors("<Tabs tabs={[<FileWithIcon key=\"a\" />, <FileWithIcon key=\"b\" />]} />");
    assertTrue(jsxTagNames().contains("Tabs"), "A 'Tabs' JSX element is expected in the JS root");
  }

  // --- expressions -----------------------------------------------------------------------------

  /**
   * {@code {}} in prose is an empty text expression, modelled as MDX_JSX_EXPRESSION.
   */
  @Test
  public void testEmptyExpressionIsExpression() {
    assertNoErrors("An empty expression {} in prose.");
    List<PsiElement> expression = collect(getFile(), it -> typeName(it).equals("MDX_JSX_EXPRESSION") && it.getText().equals("{}"));
    assertFalse(expression.isEmpty(),
                "'{}' must be parsed as an (empty) MDX expression (MDX_JSX_EXPRESSION), not plain prose text. " +
                "Nodes with text '{}':\n" + describe(collect(getFile(), it -> it.getText().equals("{}"))));
  }

  /**
   * {@code {a < b ? <Yes/> : <No/>}} on its own line is a single flow expression; {@code <Yes/>}/{@code <No/>} must
   * not be split into standalone HTML_TAG tokens in the MDX tree.
   */
  @Test
  public void testFlowExpressionWithJsx() {
    assertNoErrors("{a < b ? <Yes/> : <No/>}");
    List<PsiElement> tags = collect(getFile(), it -> typeName(it).equals("HTML_TAG") &&
                                                     (it.getText().equals("<Yes/>") || it.getText().equals("<No/>")));
    assertTrue(tags.isEmpty(), "The flow expression must stay one JS expression; '<Yes/>'/'<No/>' must not be split into " +
                               "standalone inline tags in the MDX tree:\n" + describe(tags));
  }

  // --- GFM -------------------------------------------------------------------------------------

  /**
   * A table with alignment, a header and a row.
   */
  @Test
  public void testGfmTable() {
    assertNoErrors("| Left | Center | Right |\n| :--- | :----: | ----: |\n| a    | b      | c     |");
    PsiElement table = firstContainer("TABLE");
    assertNotNull(table, "A GFM table node is expected");
    assertTrue(table.getText().contains("Left") && table.getText().contains("Center") && table.getText().contains("Right"),
               "Table cells must hold the header text");
  }

  /**
   * {@code ~~struck through~~} is a strikethrough node.
   */
  @Test
  public void testStrikethrough() {
    assertNoErrors("This is ~~struck through~~ text.");
    PsiElement strike = firstContainer("STRIKETHROUGH");
    assertNotNull(strike, "A strikethrough node is expected");
    assertTrue(strike.getText().contains("struck through"), "Strikethrough must wrap 'struck through'");
  }

  /**
   * Unchecked/checked task items are list items with CHECK_BOX markers, not text or SHORT_REFERENCE_LINK.
   */
  @Test
  public void testTaskListItemsAreCheckable() {
    assertNoErrors("- [ ] unchecked task\n- [x] checked task");
    List<PsiElement> items = nodesOfType(getFile(), "LIST_ITEM");
    assertEquals(2, items.size(), "Two task list items are expected");
    List<PsiElement> checkboxes = nodesOfType(getFile(), "CHECK_BOX");
    assertEquals(2, checkboxes.size(), "Both list items must be GFM task items with CHECK_BOX markers");
    assertTrue(nodesOfType(getFile(), "SHORT_REFERENCE_LINK").isEmpty(), "'[x]' is a task list checkbox, not a SHORT_REFERENCE_LINK");
  }

  /**
   * {@code > [!NOTE]} is a GitHub alert; {@code [!NOTE]} must not be parsed as a SHORT_REFERENCE_LINK.
   */
  @Test
  @Disabled("Consulo's markdown engine has no GitHub alert provider (ALERT)")
  public void testGfmAlertIsRecognized() {
    assertNoErrors("> [!NOTE]\n> Useful information that users should know.");
    PsiElement alert = firstContainer("ALERT");
    assertNotNull(alert, "A GitHub alert (ALERT) node is expected for the '> [!NOTE]' blockquote");
    List<PsiElement> titles = nodesOfType(getFile(), "ALERT_TITLE");
    assertFalse(titles.isEmpty(), "The alert must carry an ALERT_TITLE node");
    assertEquals("[!NOTE]", titles.get(0).getText().trim(), "The alert title must be the literal '[!NOTE]'");
    assertTrue(nodesOfType(getFile(), "SHORT_REFERENCE_LINK").isEmpty(), "'[!NOTE]' must not be parsed as a reference link");
  }

  // --- JSX in lists ----------------------------------------------------------------------------

  /**
   * An ordered list whose first item contains a nested flow {@code <img/>} element.
   */
  @Test
  public void testJsxInOrderedListNestsUnderItem() {
    assertNoErrors("1. one\n   <img src=\"\" />\n2. two\n3. three");
    assertNotNull(firstContainer("ORDERED_LIST"), "An ordered list is expected");
    List<PsiElement> items = nodesOfType(getFile(), "LIST_ITEM");
    assertEquals(3, items.size(), "Three ordered list items are expected");
    PsiElement firstItem = items.get(0);
    assertTrue(firstItem.getText().contains("<img src=\"\" />"), "The <img/> must be inside the first list item");
    List<PsiElement> nestedJsx = collect(firstItem, it -> (typeName(it).equals("MDX_JSX_SELF_CLOSING_ELEMENT") ||
                                                           typeName(it).equals("MDX_JSX_FLOW_ELEMENT")) &&
                                                          it.getText().contains("<img"));
    assertFalse(nestedJsx.isEmpty(), "'<img/>' inside the ordered list item must be a nested MDX JSX element");
    assertFalse(nodesOfType(firstItem, "MDX_JSX_ATTRIBUTE").isEmpty(), "The nested '<img/>' must expose an MDX_JSX_ATTRIBUTE (src)");
  }

  /**
   * {@code <Answer options={[1, 2]}>text</Answer>} with a multiline attribute is one flow element whose body is a
   * Markdown paragraph child.
   */
  @Test
  public void testMultilineJsxAttributeChildIsMarkdown() {
    assertNoErrors("## Exercise\n\nThis is some question.\n\n<Answer\n  options={[1, 2]}\n>\ntext\n</Answer>\n\nThis is another question.");
    List<PsiElement> textChild = collect(getFile(), it -> typeName(it).equals(JSX_BLOCK_CONTENT) && it.getText().equals("text"));
    assertTrue(textChild.isEmpty(), "The 'text' body of <Answer> must be a Markdown paragraph, not opaque JSX_BLOCK_CONTENT");
    List<PsiElement> bodyParagraphs = collect(getFile(), it -> typeName(it).equals("PARAGRAPH") &&
                                                               it.getFirstChild() != null &&
                                                               it.getText().trim().equals("text"));
    assertFalse(bodyParagraphs.isEmpty(), "The 'text' body of <Answer> must be a Markdown PARAGRAPH");
    assertNestedInJsxElement(bodyParagraphs.get(0), "Answer");
  }

  // --- fenced code edge case -------------------------------------------------------------------

  /**
   * A 4-backtick fence whose body is a verbatim inner js fence stays opaque.
   */
  @Test
  public void testNestedFenceIsOpaque() {
    assertNoErrors("````md\n```js\nconst x = 1\n```\n````");
    assertOpaqueFenceContains("```js");
    assertOpaqueFenceContains("const x = 1");
  }

  // --- ESM / member expression / export default ------------------------------------------------

  @Test
  public void testImportAndExportInParagraphsAreText() {
    List<String> paragraphs = List.of(
      "This example builds a virtual machine from a guest operating system ISO file. The builder will start the\n" +
      "virtual machine, boot from the ISO, and install the operating system, and run any provisioners, and then\n" +
      "export the virtual machine as an image.",
      "some imports will be unused and look good in the text, but some will not and it will break the text like this. \n" +
      "import will be parsed as special word and not as regular import. lorem ipsum dolor sit."
    );
    assertNoErrors(String.join("\n\n", paragraphs));
    assertEquals(paragraphs, nodesOfType(getFile(), "PARAGRAPH").stream().map(PsiElement::getText).toList());
    for (String type : List.of("MDX_ESM_BLOCK", "ES6ImportDeclaration", "ES6ExportDeclaration", "ES6ExportDefaultAssignment")) {
      assertTrue(nodesOfTypeAllRoots(type).isEmpty(), "The paragraphs must contain no " + type + " nodes");
    }
  }

  /**
   * {@code import}/{@code export} mid-sentence stays prose text: no ESM node, no error.
   */
  @Test
  public void testImportWordInProseIsText() {
    assertNoErrors("You can import assets into your project whenever you export them.");
    List<PsiElement> esm = new ArrayList<>();
    for (PsiFile root : allRoots()) {
      esm.addAll(collect(root, it -> {
        String name = typeName(it);
        return name.equals("ES6ImportDeclaration") || name.equals("ES6ExportDeclaration") || name.equals("ES6ExportDefaultAssignment");
      }));
    }
    assertTrue(esm.isEmpty(), "'import'/'export' mid-sentence must not produce ESM declarations:\n" + describe(esm));
    assertFalse(collect(getFile(), it -> typeName(it).equals("TEXT") && it.getText().contains("import")).isEmpty(),
                "The prose must contain the word 'import' as Markdown text");
  }

  /**
   * {@code <Foo.Bar />} is a flow element named "Foo.Bar" (member expression component).
   */
  @Test
  public void testMemberExpressionComponent() {
    assertNoErrors("<Foo.Bar />");
    assertTrue(jsxTagNames().contains("Foo.Bar"),
               "A member expression component name 'Foo.Bar' is expected as the JSX tag name, got " + jsxTagNames());
  }

  /**
   * {@code export default function ...} and {@code export { Thing as default }} are two ESM nodes; the trailing
   * {@code # Heading} stays a real ATX_1 heading.
   */
  @Test
  public void testExportDefault() {
    assertNoErrors("export default function Layout(props) {\n  return props.children\n}\n\nexport { Thing as default } from './m'\n\n# Heading");
    assertTrue(collect(getFile(), it -> typeName(it).equals(JSX_BLOCK_CONTENT) && it.getText().contains("# Heading")).isEmpty(),
               "Top-level export statements are ESM, not opaque JSX_BLOCK_CONTENT");
    assertHasMarkdownHeading("Heading", 1);
  }

  @Test
  public void testExportedFunctionWithNextLineBodyDoesNotConsumeMarkdown() {
    assertNoErrors("export default function Layout(props)\n{\n  return props.children\n}\n\n# Heading");
    assertHasMarkdownHeading("Heading", 1);
  }

  // --- front matter ----------------------------------------------------------------------------

  /**
   * A leading {@code ---}...{@code ---} block is a FRONT_MATTER_HEADER.
   */
  @Test
  @Disabled("Consulo's markdown engine has no front matter provider (FRONT_MATTER_HEADER)")
  public void testFrontMatterIsRecognized() {
    assertNoErrors("---\ntitle: Hello MDX\npublished: true\n---\n\n# Heading");
    assertFalse(nodesOfTypeAllRoots("FRONT_MATTER_HEADER").isEmpty(),
                "A leading '---'...'---' block must parse to a FRONT_MATTER_HEADER node");
    assertHasMarkdownHeading("Heading", 1);
  }

  /**
   * A leading {@code +++}...{@code +++} block is a FRONT_MATTER_HEADER.
   */
  @Test
  @Disabled("Consulo's markdown engine has no front matter provider (FRONT_MATTER_HEADER)")
  public void testTomlFrontMatterIsRecognized() {
    assertNoErrors("+++\ntitle = \"Hello MDX\"\npublished = true\n+++\n\n# Heading");
    assertFalse(nodesOfTypeAllRoots("FRONT_MATTER_HEADER").isEmpty(),
                "A leading '+++'...'+++' block must parse to a FRONT_MATTER_HEADER node");
    assertHasMarkdownHeading("Heading", 1);
  }

  // --- whitespace / CRLF edge cases ------------------------------------------------------------

  /**
   * CRLF line endings parse like LF.
   */
  @Test
  public void testCrlf() {
    assertNoErrors("# Heading\r\nA paragraph line.\r\n\r\n<Note>flow</Note>\r\n");
    assertHasMarkdownHeading("Heading", 1);
    assertNotNull(firstContainer("PARAGRAPH"), "A paragraph is expected");
  }

  /**
   * A whitespace-only file has no block content.
   */
  @Test
  public void testWhitespaceOnlyFile() {
    assertNoErrors("   \n   \n");
    List<PsiElement> content = collect(getFile(), it -> it.getFirstChild() == null && !it.getText().isBlank());
    assertTrue(content.isEmpty(), "A whitespace-only file has no block content:\n" + describe(content));
  }

  /**
   * An empty file is valid MDX.
   */
  @Test
  public void testEmptyFile() {
    assertNoErrors("");
  }
}
