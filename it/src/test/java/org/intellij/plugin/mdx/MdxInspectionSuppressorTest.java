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

import com.sixrr.inspectjs.confusing.BlockStatementJSInspection;
import com.sixrr.inspectjs.validity.BadExpressionStatementJSInspection;
import consulo.it.AllowLogError;
import consulo.it.CodeInsightTestFixture;
import consulo.it.HeadlessProjectExtension;
import consulo.language.editor.rawHighlight.HighlightInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code {expression}} of MDX is a JavaScript block of the MdxJS root: the statement inspections don't apply to it.
 */
@ExtendWith(HeadlessProjectExtension.class)
@AllowLogError({
  "consulo.language.impl.internal.psi.resolve.reference.ReferenceProvidersRegistryImpl",
  "consulo.xml.impl.internal.ExternalResource",
  "consulo.component.internal.NewExtensionPointImpl"
})
public class MdxInspectionSuppressorTest {
  @Test
  public void testExpressionInProse(CodeInsightTestFixture fixture) throws Exception {
    fixture.enableInspections(BlockStatementJSInspection.class, BadExpressionStatementJSInspection.class);
    fixture.configureByText("Expression.mdx", "export const year = 2023\n\nIn {year}, the snowfall was above average.\n\n{year + 1}\n");
    List<String> warnings = new ArrayList<>();
    for (HighlightInfo info : fixture.doHighlighting()) {
      if (info.getDescription() != null) {
        warnings.add(info.getDescription() + " @ [" + info.getStartOffset() + ", " + info.getEndOffset() + ")");
      }
    }
    assertEquals(List.of(), warnings);
  }

  /**
   * A block statement inside the code of an expression is still reported.
   */
  @Test
  public void testNestedBlockReported(CodeInsightTestFixture fixture) throws Exception {
    fixture.enableInspections(BlockStatementJSInspection.class);
    fixture.configureByText("Nested.mdx", "{(() => { { let a = 1; } })()}\n");
    List<String> warnings = new ArrayList<>();
    for (HighlightInfo info : fixture.doHighlighting()) {
      if (info.getDescription() != null && info.getDescription().toString().contains("unnecessary.block")) {
        warnings.add(info.getDescription() + " @ [" + info.getStartOffset() + ", " + info.getEndOffset() + ")");
      }
    }
    assertEquals(1, warnings.size(), warnings.toString());
  }
}
