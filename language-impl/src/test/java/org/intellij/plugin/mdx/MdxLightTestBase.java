package org.intellij.plugin.mdx;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.version.LanguageVersionUtil;
import consulo.test.junit.impl.extension.ConsuloProjectLoader;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base for light PSI tests: {@link #configureByText} parses MDX text into an in-memory file (no project files, no
 * highlighting), and the helpers inspect the Markdown/MDX root and the embedded JavaScript root of its view provider.
 * <p>
 * Element types are matched by their normalized name: the last {@code ':'}-separated segment of
 * {@code elementType.toString()}.
 */
@ExtendWith(ConsuloProjectLoader.class)
public abstract class MdxLightTestBase {
  protected static final String JSX_BLOCK_CONTENT = "JSX_BLOCK_CONTENT";

  private @Nullable PsiFileFactory myPsiFileFactory;
  private @Nullable PsiFile myFile;

  @BeforeEach
  public void setUpPsiFileFactory(PsiFileFactory psiFileFactory) {
    myPsiFileFactory = psiFileFactory;
    myFile = null;
  }

  /**
   * Parses {@code text} as {@code foo.mdx}; the result becomes {@link #getFile()}.
   */
  @RequiredReadAction
  protected PsiFile configureByText(String text) {
    return configureByText("foo.mdx", text);
  }

  @RequiredReadAction
  protected PsiFile configureByText(String fileName, String text) {
    PsiFileFactory factory = Objects.requireNonNull(myPsiFileFactory, "PsiFileFactory is not injected");
    PsiFile file = factory.createFileFromText(fileName,
                                              MdxLanguage.INSTANCE,
                                              LanguageVersionUtil.findDefaultVersion(MdxLanguage.INSTANCE),
                                              text);
    Assertions.assertEquals(text, file.getText(), "psi text mismatch");
    myFile = file;
    return file;
  }

  /**
   * The Markdown/MDX root of the last configured file.
   */
  protected PsiFile getFile() {
    return Objects.requireNonNull(myFile, "configureByText was not called");
  }

  @RequiredReadAction
  protected List<PsiFile> allRoots() {
    return getFile().getViewProvider().getAllFiles();
  }

  // --- no-error assertions -------------------------------------------------------------------

  @RequiredReadAction
  protected List<PsiErrorElement> collectPsiErrorElements() {
    List<PsiErrorElement> errors = new ArrayList<>();
    for (PsiFile root : allRoots()) {
      errors.addAll(PsiTreeUtil.collectElementsOfType(root, PsiErrorElement.class));
    }
    return errors;
  }

  @RequiredReadAction
  protected void assertNoPsiErrors() {
    StringBuilder found = new StringBuilder();
    for (PsiFile root : allRoots()) {
      for (PsiErrorElement error : PsiTreeUtil.collectElementsOfType(root, PsiErrorElement.class)) {
        found.append("\n  ").append(error.getErrorDescription()).append(" @ ").append(error.getTextRange())
          .append(" in ").append(root.getLanguage());
      }
    }
    Assertions.assertTrue(found.isEmpty(), "Expected no PsiErrorElement, but found:" + found);
  }

  /**
   * Parses {@code text} and asserts that no view-provider root contains a {@link PsiErrorElement}. Highlighting is not
   * run, so error-severity annotations and inspections are not checked.
   */
  @RequiredReadAction
  protected void assertNoErrors(String text) {
    configureByText(text);
    assertNoPsiErrors();
  }

  // --- PSI structure helpers -----------------------------------------------------------------

  @RequiredReadAction
  protected static String typeName(PsiElement element) {
    String name = element.getNode().getElementType().toString();
    return name.substring(name.lastIndexOf(':') + 1);
  }

  /**
   * A JSX tag name token of the JavaScript root.
   */
  @RequiredReadAction
  protected static boolean isJsxTagName(PsiElement element) {
    String name = typeName(element);
    return name.equals("XML_NAME") || name.equals("XML_TAG_NAME");
  }

  @RequiredReadAction
  protected static List<PsiElement> collect(PsiElement root, Predicate<PsiElement> predicate) {
    return Arrays.asList(PsiTreeUtil.collectElements(root, predicate::test));
  }

  @RequiredReadAction
  protected static List<PsiElement> nodesOfType(PsiElement root, String name) {
    return collect(root, it -> typeName(it).equals(name));
  }

  @RequiredReadAction
  protected List<PsiElement> nodesOfTypeAllRoots(String name) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiFile root : allRoots()) {
      result.addAll(nodesOfType(root, name));
    }
    return result;
  }

  /**
   * The JSX tag names of all view-provider roots.
   */
  @RequiredReadAction
  protected List<String> jsxTagNames() {
    List<String> result = new ArrayList<>();
    for (PsiFile root : allRoots()) {
      for (PsiElement element : collect(root, MdxLightTestBase::isJsxTagName)) {
        result.add(element.getText());
      }
    }
    return result;
  }

  @RequiredReadAction
  protected List<PsiElement> collectFenceContents() {
    return nodesOfType(getFile(), "CODE_FENCE_CONTENT");
  }

  /**
   * Asserts that the fence content joined by newlines contains {@code code} verbatim, i.e. the code is opaque and not
   * split into JSX tags or expressions.
   */
  @RequiredReadAction
  protected void assertOpaqueFenceContains(String code) {
    String joined = collectFenceContents().stream().map(PsiElement::getText).collect(Collectors.joining("\n"));
    Assertions.assertTrue(joined.contains(code),
                          "Opaque fenced code containing '" + code + "' is expected, but the joined CODE_FENCE_CONTENT " +
                          "did not hold it. Joined fence content:\n" + joined);
  }

  /**
   * Asserts that no view-provider root has a JSX tag named one of {@code forbiddenTagNames}.
   */
  @RequiredReadAction
  protected void assertNoJsxTagNamed(String... forbiddenTagNames) {
    if (forbiddenTagNames.length == 0) return;
    Set<String> forbidden = Set.of(forbiddenTagNames);
    List<PsiElement> tagNames = new ArrayList<>();
    for (PsiFile root : allRoots()) {
      tagNames.addAll(collect(root, it -> isJsxTagName(it) && forbidden.contains(it.getText())));
    }
    Assertions.assertTrue(tagNames.isEmpty(),
                          "Opaque code must not be parsed as a JSX tag, but found tag name(s) " + forbidden + ":\n" +
                          describe(tagNames));
  }

  /**
   * Asserts that the MDX root has an ATX heading of {@code depth} whose text contains {@code content}; returns the
   * first one.
   */
  @RequiredReadAction
  protected PsiElement assertHasMarkdownHeading(String content, int depth) {
    String expected = "ATX_" + depth;
    List<PsiElement> headings = collect(getFile(), it -> typeName(it).equals(expected) && it.getText().contains(content));
    List<PsiElement> allAtx = collect(getFile(), it -> typeName(it).startsWith("ATX_") && it.getFirstChild() != null);
    Assertions.assertFalse(headings.isEmpty(),
                           "A Markdown heading (element type '" + expected + "') containing '" + content + "' is expected, " +
                           "but none was parsed. All ATX nodes found: " +
                           allAtx.stream().map(it -> it.getNode().getElementType() + ":'" + it.getText() + "'").toList());
    return headings.get(0);
  }

  /**
   * The first composite (with children) element of the MDX root of the given normalized type name.
   */
  @RequiredReadAction
  protected @Nullable PsiElement firstContainer(String name) {
    List<PsiElement> elements = collect(getFile(), it -> typeName(it).equals(name) && it.getFirstChild() != null);
    return elements.isEmpty() ? null : elements.get(0);
  }

  /**
   * Asserts that {@code element} lies within the {@code <tag>...</tag>} source span.
   */
  @RequiredReadAction
  protected void assertNestedInJsxElement(PsiElement element, String tag) {
    String source = getFile().getText();
    int open = source.indexOf("<" + tag);
    int close = source.indexOf("</" + tag + ">");
    Assertions.assertTrue(open >= 0 && open < close, "Test setup: '<" + tag + "' / '</" + tag + ">' not found in source");
    int end = close + tag.length() + 3;
    int start = element.getTextRange().getStartOffset();
    Assertions.assertTrue(start >= open && start <= end && element.getTextRange().getEndOffset() <= end,
                          "'" + element.getText() + "' (" + typeName(element) + ") must be nested inside <" + tag + ">...</" +
                          tag + "> (source offsets [" + open + ", " + end + "]), but it was at " + element.getTextRange());
  }

  @RequiredReadAction
  protected static String describe(List<? extends PsiElement> elements) {
    return elements.stream()
      .map(it -> "  " + typeName(it) + " '" + it.getText() + "' @ " + it.getTextRange())
      .collect(Collectors.joining("\n"));
  }
}
