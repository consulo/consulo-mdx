package org.intellij.plugin.mdx;

import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.plugin.mdx.lang.parse.MdxExpressionBoundaryScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.intellij.plugin.mdx.MdxScannerTestSupport.lineEnds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ConsuloApplicationLoader.class)
public class MdxExpressionBoundaryScannerTest {
  @Test
  public void stringAndTemplateBracesDoNotEndExpression() {
    assertExpressionEnd("{'}'}");
    assertExpressionEnd("{\"}\"}");
    assertExpressionEnd("{`a" + '$' + "{{ value: '}' }}b`}");
  }

  @Test
  public void commentsAndRegularExpressionsDoNotEndExpression() {
    assertExpressionEnd("{value.replace(/[/}]/g, '')}");
    assertExpressionEnd("{/a{1,2}/.test(value)}");
    assertExpressionEnd("{/* } */ value}");
    assertExpressionEnd("{// }\nvalue}");
  }

  @Test
  public void nestedJavaScriptAndJsxAreBalancedByPlatformLexer() {
    assertExpressionEnd("{{ value: { nested: true } }}");
    assertExpressionEnd("{() => { return <Item value={{ nested: true }} /> }}");
  }

  @Test
  public void unterminatedExpressionHasNoBoundary() {
    String text = "{`unterminated";
    assertEquals(-1, MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, text.length()));
    String regularExpression = "{/unterminated";
    assertEquals(-1, MdxExpressionBoundaryScanner.findExpressionEnd(regularExpression, 0, regularExpression.length()));
  }

  @Test
  public void malformedMarkdownLikeJavaScriptStillEndsAtHostBrace() {
    assertExpressionEnd("{[Target](./target.mdx)}");
  }

  @Test
  public void malformedRecoveryAttemptsAreBoundedAcrossSearches() {
    MdxExpressionBoundaryScanner.RecoveryBudget budget = new MdxExpressionBoundaryScanner.RecoveryBudget();
    String adversarialExpression = "{`" + "}".repeat(MdxExpressionBoundaryScanner.MALFORMED_RECOVERY_ATTEMPT_LIMIT * 64);

    assertEquals(
      List.<Integer>of(),
      MdxExpressionBoundaryScanner.findExpressionEndCandidates(adversarialExpression, 0, adversarialExpression.length(), budget)
    );
    assertTrue(budget.isExhausted());

    String anotherExpression = "{`}";
    assertEquals(
      List.<Integer>of(),
      MdxExpressionBoundaryScanner.findExpressionEndCandidates(anotherExpression, 0, anotherExpression.length(), budget)
    );
  }

  @Test
  public void incrementalSessionMatchesFreshScansForValidAndMalformedInput() {
    List<String> expressions = List.of(
      """
        {{
          nested: {
            value: `template value`,
          },
        }}""",
      "{value.replace(/[/}]/g, '')}",
      "{() => <Item value={{ nested: true }} />}",
      "{[Target](./target.mdx)}",
      "{/unterminated",
      "{\"unterminated"
    );

    for (String text : expressions) {
      MdxExpressionBoundaryScanner.Session session = new MdxExpressionBoundaryScanner.Session(text, 0);
      for (int limit : lineEnds(text)) {
        assertEquals(
          MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, limit),
          session.advanceTo(limit),
          "expression=" + text + ", limit=" + limit
        );
      }
    }
  }

  @Test
  public void incrementalSessionDoesNotReadBeyondExposedPrefix() {
    List<String> expressions = List.of(
      "{/} /}",
      "{`before ${value} after`}",
      "{/* comment } */ value}"
    );

    for (String text : expressions) {
      MdxScannerTestSupport.PrefixGuardCharSequence guarded = new MdxScannerTestSupport.PrefixGuardCharSequence(text);
      guarded.expose(1);
      MdxExpressionBoundaryScanner.Session session = new MdxExpressionBoundaryScanner.Session(guarded, 0);
      for (int limit = 1; limit <= text.length(); limit++) {
        guarded.expose(limit);
        assertEquals(
          MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, limit),
          session.advanceTo(limit),
          "expression=" + text + ", limit=" + limit
        );
      }
    }
  }

  private static void assertExpressionEnd(String text) {
    assertEquals(text.length(), MdxExpressionBoundaryScanner.findExpressionEnd(text, 0, text.length()), text);
  }
}
