package org.intellij.plugin.mdx;

import org.intellij.plugin.mdx.lang.parse.MdxFlavourDescriptor;
import org.intellij.plugin.mdx.lang.template.MdxTemplateDataElementType;
import org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.document.util.TextRange;
import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import consulo.test.light.impl.LightProgressManager;
import org.intellij.plugin.mdx.MdxScannerTestSupport.CancellingCharSequence;
import org.intellij.plugin.mdx.MdxScannerTestSupport.CountingCharSequence;
import org.intellij.plugin.mdx.lang.parse.MdxEsmScanner;
import org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner;
import org.intellij.plugin.mdx.lang.parse.MdxJsxScanner;
import org.intellij.plugin.mdx.lang.parse.MdxMarkdownCodeSpanScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntToLongFunction;

import static org.intellij.plugin.mdx.MdxScannerTestSupport.buildEsm;
import static org.intellij.plugin.mdx.MdxScannerTestSupport.buildJsx;
import static org.intellij.plugin.mdx.MdxScannerTestSupport.lineEnds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scanners and the parser scale linearly (character access counts, no wall-clock benchmark) and stop on
 * cancellation.
 * <p>
 * The light test application's {@link LightProgressManager} never cancels, so the cancellation tests run the scan with an
 * application proxy whose progress manager throws {@link ProcessCanceledException} once the test's
 * {@link CancellableProgress} is canceled.
 */
@ExtendWith(ConsuloApplicationLoader.class)
public class MdxScannerPerformanceTest {
  private static final long CANCELLATION_THRESHOLD = 2_048L;
  private static final long MAX_CANCELLATION_OVERHEAD = 4_096L;

  @Test
  public void scannerLoopsStopAfterCancellation() {
    assertCancelsDuringScan("{[" + "value,".repeat(10_000) + "]}", text ->
      MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, text.length()));
    assertCancelsDuringScan(buildEsm(10_000), text -> MdxEsmScanner.scanBlock(text, 0));
    assertCancelsDuringScan(buildJsx(10_000), text -> MdxJsxScanner.scanJsxElement(text, 0));
    assertCancelsDuringScan("before ```code``` ".repeat(10_000), text ->
      new MdxMarkdownCodeSpanScanner.Session(text, 0).advanceTo(text.length()));
  }

  @Test
  public void javaScriptScansCheckCancellationBeforeReadingInput() {
    CancellableProgress indicator = new CancellableProgress();
    indicator.cancel();
    CountingCharSequence text = new CountingCharSequence("{value}");

    assertThrows(ProcessCanceledException.class, () ->
      runProcess(() -> MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, text.length()), indicator));
    assertEquals(0, text.getAccesses());
  }

  @Test
  public void lexerTokenScanStopsPromptlyAfterCancellation() {
    String source = "{\"" + "value".repeat(100_000) + "\"}";
    CancellableProgress indicator = new CancellableProgress();
    CancellingCharSequence text = new CancellingCharSequence(source, CANCELLATION_THRESHOLD, indicator::cancel);

    assertThrows(ProcessCanceledException.class, () ->
      runProcess(() -> MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, text.length()), indicator));
    assertTrue(
      text.getAccesses() <= CANCELLATION_THRESHOLD + MAX_CANCELLATION_OVERHEAD,
      "Scanner read " + text.getAccesses() + " characters after cancellation threshold " + CANCELLATION_THRESHOLD
    );
  }

  @Test
  public void incrementalSessionsScaleLinearly() {
    assertLinearGrowth("expression", MdxScannerPerformanceTest::expressionAccesses);
    assertLinearGrowth("ESM", MdxScannerPerformanceTest::esmAccesses);
    assertLinearGrowth("JSX", MdxScannerPerformanceTest::jsxAccesses);
  }

  @Test
  public void flowJsxBlockParsingScalesLinearly() {
    assertLinearGrowth("flow JSX parsing", elements -> markdownParsingAccesses(buildJsx(elements)));
  }

  @Test
  public void wrappedFlowParagraphParsingScalesLinearly() {
    for (String openingBreak : List.of("", "\n")) {
      assertLinearGrowth("wrapped flow paragraph", lines ->
        markdownParsingAccesses("<div>\n<p>" + openingBreak + "text\n".repeat(lines) + "last</p>\n</div>"));
    }
  }

  @Test
  public void singleLineFlowParagraphParsingScalesLinearly() {
    for (String body : List.of("text", "text\ncontinued")) {
      assertLinearGrowth("adjacent flow paragraphs", lines -> markdownParsingAccesses(("<p>" + body + "</p>\n").repeat(lines)));
    }
  }

  @Test
  public void inlineJsxParsingScalesLinearly() {
    assertLinearGrowth("inline JSX parsing", MdxScannerPerformanceTest::inlineJsxParsingAccesses);
    assertLinearGrowth("mixed inline parsing", MdxScannerPerformanceTest::mixedInlineParsingAccesses);
  }

  @Test
  public void templateProjectionScalesLinearly() {
    assertLinearGrowth("template projection", MdxScannerPerformanceTest::templateProjectionAccesses);
  }

  @Test
  public void templateProjectionStopsAfterCancellation() {
    assertCancelsDuringScan(buildJsx(10_000), MdxScannerPerformanceTest::projectTemplate);
  }

  @Test
  public void largeJsxElementHasNoArbitraryScanLimit() {
    String text = buildJsx(18_000);
    assertTrue(text.length() > 250_000);

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertNotNull(element);
    assertEquals(MdxJsxScanner.Termination.MATCHED, element.termination());
    assertEquals(text.length(), element.range().getEndOffset());
  }

  @Test
  public void denseCodeSpansAreIndexedLinearly() {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < 2_000; index++) {
      builder.append("text ```code-").append(index).append("``` ");
    }
    String source = builder.toString();
    CountingCharSequence text = new CountingCharSequence(source);

    List<TextRange> spans = new MdxMarkdownCodeSpanScanner.Session(text, 0).advanceTo(text.length());

    assertEquals(2_000, spans.size());
    assertTrue(text.getAccesses() <= source.length() * 12L, text.getAccesses() + " accesses for " + source.length() + " characters");
  }

  @Test
  public void unmatchedCodeSpanDelimitersAreIndexedLinearly() {
    StringBuilder builder = new StringBuilder();
    for (int length = 1; length <= 300; length++) {
      builder.append('x');
      builder.append("`".repeat(length));
      builder.append(' ');
    }
    String source = builder.toString();
    CountingCharSequence text = new CountingCharSequence(source);

    List<TextRange> spans = new MdxMarkdownCodeSpanScanner.Session(text, 0).advanceTo(text.length());

    assertEquals(List.<TextRange>of(), spans);
    assertTrue(text.getAccesses() <= source.length() * 12L, text.getAccesses() + " accesses for " + source.length() + " characters");
  }

  private static void assertLinearGrowth(String name, IntToLongFunction accessCounter) {
    long small = accessCounter.applyAsLong(600);
    long large = accessCounter.applyAsLong(1_200);
    assertTrue(large <= small * 5 / 2, name + " character accesses grew from " + small + " to " + large);
  }

  private static void assertCancelsDuringScan(String source, Consumer<CharSequence> scan) {
    CancellableProgress indicator = new CancellableProgress();
    CancellingCharSequence text = new CancellingCharSequence(source, CANCELLATION_THRESHOLD, indicator::cancel);

    assertThrows(ProcessCanceledException.class, () -> runProcess(() -> scan.accept(text), indicator));
    assertTrue(text.getAccesses() >= CANCELLATION_THRESHOLD);
  }

  private static long expressionAccesses(int lines) {
    StringBuilder builder = new StringBuilder("{[\n");
    for (int i = 0; i < lines; i++) {
      builder.append("  { value: ").append(i).append(" },\n");
    }
    builder.append("]\n}");
    String text = builder.toString();
    CountingCharSequence countingText = new CountingCharSequence(text);
    MdxExpressionBoundaryScanner.Session session = new MdxExpressionBoundaryScanner.Session(countingText, 0);
    for (int limit : lineEnds(text)) {
      session.advanceTo(limit);
    }
    return countingText.getAccesses();
  }

  private static long esmAccesses(int lines) {
    String text = buildEsm(lines);
    CountingCharSequence countingText = new CountingCharSequence(text);
    MdxEsmScanner.Session session = new MdxEsmScanner.Session(countingText, 0);
    for (int limit : lineEnds(text)) {
      session.advanceTo(limit);
    }
    return countingText.getAccesses();
  }

  private static long jsxAccesses(int elements) {
    String text = buildJsx(elements);
    CountingCharSequence countingText = new CountingCharSequence(text);
    MdxJsxScanner.Session session = new MdxJsxScanner.Session(countingText, 0);
    for (int limit : lineEnds(text)) {
      session.advanceTo(limit);
    }
    return countingText.getAccesses();
  }

  private static long inlineJsxParsingAccesses(int elements) {
    StringBuilder builder = new StringBuilder("before ");
    for (int i = 0; i < elements; i++) {
      builder.append("<Item value={").append(i).append("}>text</Item> ");
    }
    builder.append("after");
    return markdownParsingAccesses(builder.toString());
  }

  private static long mixedInlineParsingAccesses(int elements) {
    StringBuilder builder = new StringBuilder("prefix ");
    for (int i = 0; i < elements; i++) {
      builder.append("*before <Item data={{ value: ").append(i).append(" }}>`code-").append(i).append("` {").append(i)
        .append("}</Item> after* ");
    }
    builder.append("suffix");
    return markdownParsingAccesses(builder.toString());
  }

  private static long markdownParsingAccesses(String text) {
    CountingCharSequence countingText = new CountingCharSequence(text);
    MdxParserTestUtil.parseMdxNodes(countingText);
    return countingText.getAccesses();
  }

  private static long templateProjectionAccesses(int elements) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < elements; i++) {
      builder.append("<Item value={").append(i).append("} /> ");
    }
    builder.append('\n');
    for (int i = 0; i < elements; i++) {
      builder.append("`code-").append(i).append("` ");
    }
    builder.append('\n');
    for (int i = 0; i < elements; i++) {
      builder.append("{/* comment-").append(i).append(" */}\n");
    }
    CountingCharSequence countingText = new CountingCharSequence(builder.toString());
    projectTemplate(countingText);
    return countingText.getAccesses();
  }

  /**
   * Projects the template data (JavaScript) of {@code sourceCode}.
   */
  private static void projectTemplate(CharSequence sourceCode) {
    TemplateProjection.INSTANCE.project(sourceCode);
  }

  /**
   * Gives access to the template modifications of {@link MdxTemplateDataElementType}.
   */
  private static final class TemplateProjection extends MdxTemplateDataElementType {
    static final TemplateProjection INSTANCE = new TemplateProjection();

    void project(CharSequence sourceCode) {
      collectTemplateModifications(sourceCode, new MarkdownToplevelLexer(MdxFlavourDescriptor.INSTANCE));
    }
  }

  private static final class CancellableProgress {
    private volatile boolean canceled;

    void cancel() {
      canceled = true;
    }

    boolean isCanceled() {
      return canceled;
    }
  }

  private static void runProcess(Runnable process, CancellableProgress indicator) {
    Application original = Objects.requireNonNull(ApplicationManager.getApplication());
    ProgressManager progressManager = new LightProgressManager() {
      @Override
      protected void doCheckCanceled() throws ProcessCanceledException {
        if (indicator.isCanceled()) throw new ProcessCanceledException();
      }
    };
    Application application = (Application) Proxy.newProxyInstance(
      Application.class.getClassLoader(),
      new Class<?>[]{Application.class},
      (proxy, method, args) -> {
        if (method.getName().equals("getProgressManager") && method.getParameterCount() == 0) return progressManager;
        try {
          return method.invoke(original, args);
        }
        catch (InvocationTargetException e) {
          throw e.getCause();
        }
      }
    );
    ApplicationManager.setApplication(application);
    try {
      process.run();
    }
    finally {
      ApplicationManager.setApplication(original);
    }
  }
}
