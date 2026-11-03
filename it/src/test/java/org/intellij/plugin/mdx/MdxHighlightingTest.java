/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugin.mdx;

import consulo.it.AllowLogError;
import consulo.it.CodeInsightTestFixture;
import consulo.it.HeadlessProjectExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valid MDX produces no PSI error and no ERROR highlight.
 */
@ExtendWith(HeadlessProjectExtension.class)
@AllowLogError({
  "consulo.language.impl.internal.psi.resolve.reference.ReferenceProvidersRegistryImpl",
  "consulo.xml.impl.internal.ExternalResource",
  "consulo.component.internal.NewExtensionPointImpl"
})
public class MdxHighlightingTest {
  /**
   * Plain Markdown prose is valid MDX.
   */
  @Test
  public void testPlainProse(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "PlainProse");
  }

  /**
   * A self-closing JSX flow element is valid MDX.
   */
  @Test
  public void testSelfClosingJsx(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "SelfClosingJsx");
  }

  /**
   * An ESM export and an inline {expression} that references it are valid MDX.
   */
  @Test
  public void testExportAndExpression(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "ExportAndExpression");
  }

  /**
   * A JSX flow element with a string attribute and inline children is valid MDX.
   */
  @Test
  public void testJsxWithAttribute(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "JsxWithAttribute");
  }

  /**
   * A fenced code block with plain JavaScript is valid MDX (opaque code).
   */
  @Test
  public void testFencedJsCode(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "FencedJsCode");
  }

  /**
   * A GFM table is valid MDX.
   */
  @Test
  public void testGfmTable(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "GfmTable");
  }

  /**
   * An MDX comment holding JSX with a template literal is valid MDX.
   */
  @Test
  public void testComment(CodeInsightTestFixture fixture) throws Exception {
    doAssertNoErrors(fixture, "Comment");
  }

  @Test
  @Disabled("expects the weak warning of an unresolved React JSX component; Consulo has no React support")
  public void testJsxSimple(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the weak warnings of unresolved React JSX components; Consulo has no React support")
  public void testParsingError(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the weak warnings of unresolved React JSX components; Consulo has no React support")
  public void testParsingErrorOneLine(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the weak warnings of unresolved React JSX components; Consulo has no React support")
  public void testOpenTagInAttribute(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the weak warnings of unresolved React JSX components; Consulo has no React support")
  public void testOpenCloseTagInAttribute(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the weak warnings of unresolved React JSX components; Consulo has no React support")
  public void testOpenCloseTagIn2Attribute(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the weak warnings of unresolved React JSX components; Consulo has no React support")
  public void testOpenCloseTagIn2AttributeSameLine(CodeInsightTestFixture fixture) {
  }

  @Test
  @Disabled("expects the unresolved-variable weak warning of the JavaScript inspection pipeline of the original; consulo-javascript reports unresolved references differently (other inspection, message and severity)")
  public void testUnresolvedVariable(CodeInsightTestFixture fixture) {
  }

  private static void doAssertNoErrors(CodeInsightTestFixture fixture, String name) throws Exception {
    fixture.configureByText(name + ".mdx", MdxItTestUtil.loadText("highlight/" + name + ".mdx"));
    assertEquals(List.of(), MdxItTestUtil.psiErrors(fixture), "PSI errors");
    assertEquals(List.of(), MdxItTestUtil.errorHighlights(fixture), "ERROR highlights");
  }
}
