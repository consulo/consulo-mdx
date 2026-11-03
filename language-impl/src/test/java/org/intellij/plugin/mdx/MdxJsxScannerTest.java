package org.intellij.plugin.mdx;

import consulo.document.util.TextRange;
import consulo.test.junit.impl.extension.ConsuloApplicationLoader;
import org.intellij.plugin.mdx.lang.parse.MdxJsxScanner;
import org.intellij.plugin.mdx.lang.parse.MdxTextRangeSet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static org.intellij.plugin.mdx.MdxScannerTestSupport.lineEnds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// mapping to nullable tag names / parent indexes / body ranges, as the scanner records them
@SuppressWarnings("NullAway")
@ExtendWith(ConsuloApplicationLoader.class)
public class MdxJsxScannerTest {
  @Test
  public void pendingJavaScriptEndsAfterItsClosingBoundary() {
    for (String text : List.of("<B>{`a\nb\nc`}\nbody\n</B>", "<B value={`a\nb\nc`}>\nbody\n</B>")) {
      MdxJsxScanner.Session session = new MdxJsxScanner.Session(text, 0);
      int firstLineEnd = text.indexOf('\n') + 1;
      int secondLineEnd = text.indexOf('\n', firstLineEnd) + 1;
      int closingLineEnd = text.indexOf('\n', secondLineEnd) + 1;

      session.advanceTo(firstLineEnd);
      assertTrue(session.isInJavaScript(), text);
      session.advanceTo(secondLineEnd);
      assertTrue(session.isInJavaScript(), text);
      session.advanceTo(closingLineEnd - 1);
      assertEquals(false, session.isInJavaScript(), text);
      session.advanceTo(text.length());
      assertEquals(false, session.isInJavaScript(), text);
    }
  }

  @Test
  public void scansMarkdownLikeTextInExpressionAttribute() {
    String text = "<Alert value={[Target](./target.mdx)} />";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
  }

  @Test
  public void validRegularExpressionKeepsItsBrace() {
    String text = "<Alert value={/} /} />";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
  }

  @Test
  public void malformedRegularExpressionRecoversAtHostBrace() {
    String text = "<Alert value={/} />";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
  }

  @Test
  public void scansTemplateLiteralExpressionAttribute() {
    String text = """
      <Source language="tsx" code={`
        const [state, setState] = useState<SomeType>()
      `}/>""";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
  }

  @Test
  public void recordsEveryOpeningTagAttributeAndExpression() {
    String text = "<Card disabled title = \"a > b\" count=42 {...props} value={{ nested: true }} />";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);
    assertNotNull(element);
    assertEquals(1, element.tags().size());
    MdxJsxScanner.Tag tag = element.tags().get(0);

    assertEquals(
      List.of("disabled ", "title = \"a > b\"", "count=42", "value={{ nested: true }}"),
      map(tag.attributes(), it -> it.substring(text))
    );
    assertEquals(List.of("{...props}", "{{ nested: true }}"), map(tag.expressions(), it -> it.substring(text)));
  }

  @Test
  public void recordsElementBodiesAndParentsDuringTagMatching() {
    String text = "<A><B>one</B><><C /></></A>";
    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);
    assertNotNull(element);
    List<MdxJsxScanner.ElementRecord> records = new ArrayList<>(element.elements());
    records.sort(Comparator.comparingInt(MdxJsxScanner.ElementRecord::index));
    assertEquals(List.of(0, 1, 2, 3), map(records, MdxJsxScanner.ElementRecord::index));
    assertEquals(Arrays.asList(null, 0, 0, 2), map(records, MdxJsxScanner.ElementRecord::parentIndex));
    assertEquals(List.of(text, "<B>one</B>", "<><C /></>", "<C />"), map(records, it -> it.range().substring(text)));
    assertEquals(
      Arrays.asList("<B>one</B><><C /></>", "one", "<C />", null),
      map(records, it -> {
        TextRange body = it.bodyRange();
        return body == null ? null : body.substring(text);
      })
    );
  }

  @Test
  public void suppliedOpaqueRangeHidesMatchingClosingTag() {
    String text = "<span>`</span>` body</span>";
    int opaqueStart = text.indexOf('`');
    int opaqueEnd = text.indexOf('`', opaqueStart + 1) + 1;

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(
      text,
      0,
      text.length(),
      MdxTextRangeSet.of(List.of(new TextRange(opaqueStart, opaqueEnd)))
    );

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
    assertNotNull(element);
    assertEquals(List.of("span", "span"), map(element.tags(), MdxJsxScanner.Tag::name));
  }

  @Test
  public void recognizesMultilineExpressionAttributeAsJsxBlockStart() {
    assertTrue(MdxJsxScanner.isLineStartJsx("<Source code={`", 0));
    assertTrue(MdxJsxScanner.isLineStartJsx("<div onClick={(e) => {", 0));
  }

  @Test
  public void scansMultilineArrowFunctionAttribute() {
    String text = """
      <div onClick={(e) => {
          console.log(e)
      }}>
          Hello
      </div>""";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
  }

  @Test
  public void unrelatedCloserRecoversElementImmediately() {
    String text = "<Outer><Inner></Other>after</Inner></Outer>";
    int recoveryEnd = text.indexOf("</Other>") + "</Other>".length();

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.RECOVERED, termination(element));
    assertEquals(recoveryEnd, endOffset(element));
    assertTrue(element != null && element.incomplete());
  }

  @Test
  public void skippedNamedDescendantKeepsMatchedElementIncomplete() {
    String text = "<Outer><Inner></Outer>";

    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
    assertTrue(element != null && element.incomplete());
  }

  @Test
  public void namedTagsAndFragmentsOnlyMatchIdenticalClosers() {
    List<String> cases = List.of(
      "<Outer><></Outer>",
      "<><Inner></>"
    );

    for (String text : cases) {
      MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, 0);

      assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element), text);
      assertEquals(text.length(), endOffset(element), text);
      assertTrue(element != null && element.incomplete(), text);
    }
  }

  @Test
  public void incrementalSessionMatchesOneShotPrefixes() {
    String text = """
      <Outer
        title=">">
        <Inner value={{ nested: true }} />
        {items.map(item => <Item value={item} />)}
      </Outer>""";
    MdxJsxScanner.Session session = new MdxJsxScanner.Session(text, 0);

    for (int limit : lineEnds(text)) {
      assertEquals(
        MdxJsxScanner.scanJsxElement(text, 0, limit),
        session.advanceTo(limit),
        "limit=" + limit
      );
    }
  }

  @Test
  public void incrementalSessionMatchesOneShotRecoveryAtEveryPrefix() {
    List<String> cases = List.of(
      "<Alert value={/} />",
      "<Outer><Inner></Outer>",
      "<Outer><Inner></Other>after</Inner></Outer>",
      "<><Inner></>",
      "<Outer><></Outer>",
      "<Outer>{/}</Outer>"
    );

    for (String text : cases) {
      MdxJsxScanner.Session session = new MdxJsxScanner.Session(text, 0);
      for (int limit = 1; limit <= text.length(); limit++) {
        assertEquals(
          MdxJsxScanner.scanJsxElement(text, 0, limit),
          session.advanceTo(limit),
          "text=" + text + ", limit=" + limit
        );
      }
    }
  }

  @Test
  public void incrementalSessionDoesNotReadBeyondExposedPrefix() {
    List<String> tags = List.of(
      "<Alert value={/} /} />",
      "<Card disabled title=\"a > b\" count=42 {...props} value={{ nested: true }} />",
      "<Outer>{/}</Outer>"
    );

    for (String text : tags) {
      MdxScannerTestSupport.PrefixGuardCharSequence guarded = new MdxScannerTestSupport.PrefixGuardCharSequence(text);
      guarded.expose(1);
      MdxJsxScanner.Session session = new MdxJsxScanner.Session(guarded, 0);
      for (int limit = 1; limit <= text.length(); limit++) {
        guarded.expose(limit);
        assertEquals(MdxJsxScanner.scanJsxElement(text, 0, limit), session.advanceTo(limit), "tag=" + text + ", limit=" + limit);
      }
    }
  }

  @Test
  public void lateOpacityInMixedBatchRollsBackAccumulatedSyntax() {
    String text = "<Box>\n`{value}</Box>`\n</Box>";
    int firstLineEnd = text.indexOf('\n') + 1;
    int codeLineEnd = text.indexOf('\n', firstLineEnd) + 1;
    int codeStart = text.indexOf('`');
    int codeEnd = text.indexOf('`', codeStart + 1) + 1;
    int firstCloserEnd = text.indexOf("</Box>") + "</Box>".length();
    MdxJsxScanner.Session session = new MdxJsxScanner.Session(text, 0);

    session.advanceTo(firstLineEnd);
    MdxJsxScanner.Element premature = session.advanceTo(codeLineEnd);
    assertNotNull(premature);
    assertEquals(firstCloserEnd, premature.range().getEndOffset());
    assertEquals(1, premature.expressions().size());
    List<MdxJsxScanner.Tag> retainedTags = List.copyOf(premature.tags());
    List<TextRange> retainedExpressions = List.copyOf(premature.expressions());

    session.addOpaqueRanges(List.of(new TextRange(0, 1), new TextRange(codeStart, codeEnd)));
    MdxJsxScanner.Element repaired = session.advanceTo(text.length());

    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(repaired));
    assertEquals(text.length(), endOffset(repaired));
    assertNotNull(repaired);
    assertEquals(List.of("Box", "Box"), map(repaired.tags(), MdxJsxScanner.Tag::name));
    assertEquals(List.<TextRange>of(), repaired.expressions());
    assertEquals(retainedTags, premature.tags());
    assertEquals(retainedExpressions, premature.expressions());
  }

  @Test
  public void repeatedLimitsPreserveLateOpacityRollback() {
    String text = "<Box>\n`{value}</Box>`\n</Box>";
    int openingEnd = text.indexOf('\n') + 1;
    int codeLineEnd = text.indexOf('\n', openingEnd) + 1;
    int codeStart = text.indexOf('`');
    int codeEnd = text.indexOf('`', codeStart + 1) + 1;

    for (boolean repairAtSameLimit : List.of(false, true)) {
      MdxJsxScanner.Session session = new MdxJsxScanner.Session(text, 0);
      MdxJsxScanner.Element opening = session.advanceTo(openingEnd);
      assertSame(opening, session.advanceTo(openingEnd));
      MdxJsxScanner.Element premature = session.advanceTo(codeLineEnd);
      assertNotNull(premature);
      List<MdxJsxScanner.Tag> retainedTags = List.copyOf(premature.tags());
      List<TextRange> retainedExpressions = List.copyOf(premature.expressions());
      List<MdxJsxScanner.ElementRecord> retainedElements = List.copyOf(premature.elements());
      for (int i = 0; i < 3; i++) {
        assertSame(premature, session.advanceTo(codeLineEnd));
      }

      session.addOpaqueRanges(List.of(new TextRange(codeStart, codeEnd)));
      if (repairAtSameLimit) {
        MdxJsxScanner.Element repaired = session.advanceTo(codeLineEnd);
        assertEquals(MdxJsxScanner.Termination.UNTERMINATED, termination(repaired));
        assertNotNull(repaired);
        assertEquals(List.of("Box"), map(repaired.tags(), MdxJsxScanner.Tag::name));
        assertSame(repaired, session.advanceTo(codeLineEnd));
      }
      MdxJsxScanner.Element complete = session.advanceTo(text.length());

      assertEquals(MdxJsxScanner.Termination.MATCHED, termination(complete));
      assertEquals(text.length(), endOffset(complete));
      assertNotNull(complete);
      assertEquals(List.of("Box", "Box"), map(complete.tags(), MdxJsxScanner.Tag::name));
      assertEquals(List.<TextRange>of(), complete.expressions());
      assertEquals(retainedTags, premature.tags());
      assertEquals(retainedExpressions, premature.expressions());
      assertEquals(retainedElements, premature.elements());
      assertEquals(List.of(new TextRange(0, text.length())), map(complete.elements(), MdxJsxScanner.ElementRecord::range));
      assertSame(complete, session.advanceTo(text.length()));
    }
  }

  @Test
  public void incrementallyScansDeepNesting() {
    int depth = 5_000;
    String openingPrefix = "<E>".repeat(depth);
    String text = openingPrefix + "</E>".repeat(depth);
    MdxJsxScanner.Session session = new MdxJsxScanner.Session(text, 0);

    MdxJsxScanner.Element prefix = session.advanceTo(openingPrefix.length());
    MdxJsxScanner.Element element = session.advanceTo(text.length());

    assertEquals(MdxJsxScanner.Termination.UNTERMINATED, termination(prefix));
    assertNotNull(prefix);
    assertEquals(depth, prefix.tags().size());
    assertEquals(MdxJsxScanner.Termination.MATCHED, termination(element));
    assertEquals(text.length(), endOffset(element));
    assertNotNull(element);
    assertEquals(depth * 2, element.tags().size());
  }

  private static MdxJsxScanner.@Nullable Termination termination(MdxJsxScanner.@Nullable Element element) {
    return element == null ? null : element.termination();
  }

  private static @Nullable Integer endOffset(MdxJsxScanner.@Nullable Element element) {
    return element == null ? null : element.range().getEndOffset();
  }

  private static <T, R> List<R> map(List<T> list, Function<T, R> mapper) {
    List<R> result = new ArrayList<>(list.size());
    for (T item : list) {
      result.add(mapper.apply(item));
    }
    return result;
  }
}
