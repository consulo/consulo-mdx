package org.intellij.plugin.mdx;

import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.plugin.mdx.lang.parse.MdxEsmScanner;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.intellij.plugin.mdx.MdxScannerTestSupport.buildEsm;
import static org.intellij.plugin.mdx.MdxScannerTestSupport.lineEnds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ConsuloApplicationLoader.class)
public class MdxEsmScannerTest {
  @Test
  public void multilineImportTerminatesOnlyAfterModuleSpecifier() {
    List<String> prefixes = List.of(
      "import",
      "import\n{hello}",
      "import\n{hello}\nfrom"
    );
    for (String prefix : prefixes) {
      assertFalse(isTerminated(MdxEsmScanner.scanBlock(prefix, 0)), prefix);
    }

    String complete = "import\n{hello}\nfrom\n'./hello.mdx'";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(complete, 0);
    assertTrue(isTerminated(block));
    assertEquals(complete.length(), endOffset(block));
  }

  @Test
  public void incompleteImportRecoversAtBlankLine() {
    String statement = "import Widget";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement + "\n\nParagraph", 0);

    assertFalse(isTerminated(block));
    assertTrue(block != null && block.recoveryBoundary());
    assertEquals(statement.length(), endOffset(block));
  }

  @Test
  public void consecutiveSameLineImportsShareStableBlock() {
    String text = "import {unused} from 'one';import {used} from 'two';";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(text, 0);

    assertTrue(isTerminated(block));
    assertEquals(text.length(), endOffset(block));
    assertTrue(MdxEsmScanner.findMissingStatementSeparators(text, 0, text.length()).isEmpty());
  }

  @Test
  public void findsMissingStatementSeparatorAtTopLevel() {
    String text = "import First from 'first'import Second from 'second'";

    assertEquals(
      List.of(text.lastIndexOf("import")),
      MdxEsmScanner.findMissingStatementSeparators(text, 0, text.length())
    );
  }

  @Test
  public void ignoresEsmKeywordsOwnedByJavaScriptTokens() {
    List<String> statements = List.of(
      "export const value = \"import\"",
      "export const value = `export`",
      "export const value = /import/",
      "export const value = <span>export</span>",
      "export const value = () => { import('module') }"
    );

    for (String statement : statements) {
      assertTrue(MdxEsmScanner.findMissingStatementSeparators(statement, 0, statement.length()).isEmpty(), statement);
    }
  }

  @Test
  public void regularExpressionBracesDoNotAffectDelimiterDepth() {
    String statement = "export const pattern = /[{]/;";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement + "\n# Heading", 0);

    assertTrue(isTerminated(block));
    assertEquals(statement.length(), endOffset(block));
  }

  @Test
  public void lexerOwnedBracesDoNotAffectDelimiterDepth() {
    List<String> statements = List.of(
      "export const string = '}'",
      "export const template = `before } after`",
      "export const comment = /* } */ 1",
      "export const element = <div data-label=\"}\">{'{'}</div>"
    );

    for (String statement : statements) {
      MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement + "\n# Heading", 0);
      assertTrue(isTerminated(block), statement);
      assertEquals(statement.length(), endOffset(block), statement);
    }
  }

  @Test
  public void exportedFunctionBodyMayStartOnNextLine() {
    String statement = """
      export default function Layout(props)
      {
        return props.children
      }""";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement + "\n# Heading", 0);

    assertTrue(isTerminated(block));
    assertEquals(statement.length(), endOffset(block));
  }

  @Test
  public void nextLineMdxExpressionDoesNotBecomeAnEsmContinuation() {
    String statement = "export const value = 1";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement + "\n{value}", 0);

    assertTrue(isTerminated(block));
    assertEquals(statement.length(), endOffset(block));
  }

  @Test
  public void multilineJsxRemainsInsideExport() {
    String statement = """
      export const element = <div data-label="}">
        {'{'}
      </div>""";
    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement + "\n# Heading", 0);

    assertTrue(isTerminated(block));
    assertEquals(statement.length(), endOffset(block));
  }

  @Test
  public void unterminatedLexicalConstructsRemainUnterminated() {
    List<String> statements = List.of(
      "export const string = 'unfinished",
      "export const string = \"unfinished\\\"",
      "export const template = `unfinished",
      "export const comment = /* unfinished",
      "export const pattern = /unfinished"
    );

    for (String statement : statements) {
      MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(statement, 0);
      assertFalse(isTerminated(block), statement);
    }
  }

  @Test
  public void incrementalSessionMatchesFreshScansForValidAndMalformedInput() {
    List<String> blocks = List.of(
      """
        export const values = [
          'first',
          `second`,
        ]""",
      "import {\n  value,\n} from 'module'",
      "export default function value() {\n  return <Item />\n}",
      "import { value\n\n# heading",
      "export const pattern = /unterminated"
    );

    for (String text : blocks) {
      MdxEsmScanner.Session session = new MdxEsmScanner.Session(text, 0);
      for (int limit : lineEnds(text)) {
        assertEquals(MdxEsmScanner.scanBlock(text, 0, limit), session.advanceTo(limit), "ESM=" + text + ", limit=" + limit);
      }
    }
  }

  @Test
  public void validEsmMayExceedPreviousScanLimit() {
    String text = buildEsm(24_000);
    assertTrue(text.length() > 250_000);

    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(text, 0);

    assertTrue(isTerminated(block));
    assertEquals(text.length(), endOffset(block));
  }

  @Test
  public void malformedExportTerminatesAtEndOfInput() {
    String text = "export your data regularly to avoid loss.";

    MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(text, 0);

    assertTrue(isTerminated(block));
    assertEquals(text.length(), endOffset(block));
  }

  @Test
  public void incrementalSessionDoesNotReadBeyondExposedPrefix() {
    List<String> blocks = List.of(
      "export const pattern = /} /",
      "export const value = `before ${nested} after`",
      "import { value } from 'module'"
    );

    for (String text : blocks) {
      MdxScannerTestSupport.PrefixGuardCharSequence guarded = new MdxScannerTestSupport.PrefixGuardCharSequence(text);
      MdxEsmScanner.Session session = new MdxEsmScanner.Session(guarded, 0);
      for (int limit = 0; limit <= text.length(); limit++) {
        guarded.expose(limit);
        assertEquals(MdxEsmScanner.scanBlock(text, 0, limit), session.advanceTo(limit), "ESM=" + text + ", limit=" + limit);
      }
    }
  }

  private static boolean isTerminated(MdxEsmScanner.@Nullable Block block) {
    return block != null && block.terminated();
  }

  private static int endOffset(MdxEsmScanner.@Nullable Block block) {
    assertNotNull(block);
    return block.range().getEndOffset();
  }
}
