package org.intellij.plugin.mdx;

import consulo.annotation.access.RequiredReadAction;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.test.junit.impl.language.SimpleParsingTest;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.plugin.mdx.lang.MdxFileType;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses {@code parsing/<testName>.mdx} and compares every view-provider root against the checked-in
 * {@code <testName>.MDX.txt} (Markdown/MDX) and {@code <testName>.MdxJS.txt} (the embedded JavaScript) snapshots, with
 * whitespace and ranges.
 */
public class MdxParsingTest extends SimpleParsingTest<Object> {
  public MdxParsingTest() {
    super("parsing", "mdx");
  }

  @Test
  public void testParsingTestData(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingList(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingWithNewLines(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxScenarios(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingFlowJsxMarkdown(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingAlert(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingEmbedded(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingPrisma(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingLongText(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingFlowContentTrailingSpace(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingIncompleteClosingBeforeFence(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingCodeBlockInJsx(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingTsxCodeBlock(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingGenericsInCodeBlock(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxExpressionAttribute(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingMarkdownInJsx(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingInlineJsx(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingExpressions(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingEsm(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testEsmInMarkdownContainersIsText(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingNestedComponents(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingInlineExpressionComment(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxInMarkdownInline(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingMultilineEsm(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingIndentedCode(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingAutolink(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingEmptyAndUnknownFence(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingInlineJsxInEmphasis(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingInlineJsxOwnership(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxInBlockquote(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxInListItem(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingMarkdownInJsxIndented(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingFragments(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingEscapes(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingOperatorInAttribute(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxInAttributeArray(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingFrontMatter(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingTomlFrontMatter(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingGfmTable(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingStrikethrough(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingTaskList(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingGfmAlert(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingImportInProse(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingEmptyExpression(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingFlowExpressionWithJsx(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingNestedFence(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingCrlf(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingWhitespaceOnly(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingMemberExpressionComponent(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingExportDefault(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingJsxInOrderedList(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingMultilineJsxAttribute(Context context) throws Exception {
    doTest(context, null);
  }

  @Test
  public void testParsingMismatchedMultilineTagRecovery(Context context) throws Exception {
    PsiFile file = loadPsiFile(context, null);

    List<String> tagNames = tagNames(file);
    assertTrue(tagNames.contains("div") && tagNames.contains("span"),
               "Both sides of the malformed JSX element must remain in MdxJS PSI: " + tagNames);
    // the JSX parser reports no error for a mismatched closing tag
    assertHasMarkdownHeading(file, "After");
  }

  @Test
  public void testParsingAncestorClosingTagRecovery(Context context) throws Exception {
    PsiFile file = loadPsiFile(context, null);

    List<String> tagNames = tagNames(file);
    assertTrue(tagNames.contains("Outer") && tagNames.contains("Inner"), "Recovered JSX names are missing: " + tagNames);
    assertFalse(errorElements(file).isEmpty(), "The JSX parser must report the unclosed Inner element");
    assertHasMarkdownHeading(file, "After");
  }

  @Test
  public void testParsingUnclosedMultilineTagAtEof(Context context) throws Exception {
    PsiFile file = loadPsiFile(context, null);

    assertTrue(tagNames(file).contains("Panel"), "An incomplete opening tag must remain in MdxJS PSI");
  }

  @Override
  protected LanguageFileType getFileType(Context context, @Nullable Object testContext) {
    return MdxFileType.INSTANCE;
  }

  /**
   * Line separators are converted and the text is trimmed.
   */
  @Override
  @SuppressWarnings("NullAway")
  protected String loadText(Context context, Object testContext, String extension) throws Exception {
    String path = "/parsing/" + getFileName(context, testContext) + "." + extension;
    try (InputStream stream = getClass().getResourceAsStream(path)) {
      if (stream == null) {
        return "";
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }
  }

  /**
   * On a mismatch the actual trees are written to target/parsing-actual.
   */
  @Override
  @SuppressWarnings("NullAway")
  protected void checkResult(Context context, @Nullable Object testContext, PsiFile file) throws Exception {
    FileViewProvider provider = file.getViewProvider();
    String name = getFileName(context, testContext);
    String mdxActual = toParseTreeText(provider.getPsi(MdxLanguage.INSTANCE), false, true).trim();
    String jsActual = toParseTreeText(provider.getPsi(JavaScriptLanguage.INSTANCE), false, true).trim();
    String mdxExpected = loadText(context, testContext, "MDX.txt");
    String jsExpected = loadText(context, testContext, "MdxJS.txt");
    if (!mdxExpected.equals(mdxActual) || !jsExpected.equals(jsActual)) {
      Path dir = Path.of("target/parsing-actual");
      Files.createDirectories(dir);
      Files.writeString(dir.resolve(name + ".MDX.txt"), mdxActual + "\n", StandardCharsets.UTF_8);
      Files.writeString(dir.resolve(name + ".MdxJS.txt"), jsActual + "\n", StandardCharsets.UTF_8);
    }
    assertEquals(mdxExpected, mdxActual, "MDX tree");
    assertEquals(jsExpected, jsActual, "MdxJS tree");
  }

  @RequiredReadAction
  private static List<String> tagNames(PsiFile file) {
    List<String> names = new ArrayList<>();
    for (PsiFile root : file.getViewProvider().getAllFiles()) {
      collectTagNames(root.getNode(), names);
    }
    return names;
  }

  private static void collectTagNames(ASTNode node, List<String> names) {
    IElementType type = node.getElementType();
    String typeName = type.toString();
    if (typeName.equals("XML_TAG_NAME") || typeName.equals("XML_NAME")) {
      names.add(node.getText());
    }
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      collectTagNames(child, names);
    }
  }

  @RequiredReadAction
  private static List<PsiErrorElement> errorElements(PsiFile file) {
    List<PsiErrorElement> errors = new ArrayList<>();
    for (PsiFile root : file.getViewProvider().getAllFiles()) {
      errors.addAll(PsiTreeUtil.findChildrenOfType(root, PsiErrorElement.class));
    }
    return errors;
  }

  @RequiredReadAction
  private static void assertHasMarkdownHeading(PsiFile file, String text) {
    PsiFile mdx = file.getViewProvider().getPsi(MdxLanguage.INSTANCE);
    List<String> headings = new ArrayList<>();
    collectNodes(mdx.getNode(), MarkdownElementTypes.ATX_1, headings);
    assertTrue(headings.stream().anyMatch(it -> it.contains(text)), "Missing heading " + text + ": " + headings);
  }

  private static void collectNodes(ASTNode node, IElementType type, List<String> texts) {
    if (node.getElementType() == type) {
      texts.add(node.getText());
    }
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      collectNodes(child, type, texts);
    }
  }
}
