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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Folding of the ESM imports of MDX files (by the JavaScript folding of the embedded JavaScript root).
 */
@ExtendWith(HeadlessProjectExtension.class)
@AllowLogError({
  "consulo.language.impl.internal.psi.resolve.reference.ReferenceProvidersRegistryImpl",
  "consulo.xml.impl.internal.ExternalResource",
  "consulo.component.internal.NewExtensionPointImpl"
})
public class MdxFoldingTest {
  @Test
  public void testFoldingImports(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "FoldingImports");
  }

  @Test
  public void testFoldingOneImport(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "FoldingOneImport");
  }

  @Test
  public void testFoldingMultilineImport(CodeInsightTestFixture fixture) throws Exception {
    doTest(fixture, "FoldingMultilineImport");
  }

  private static void doTest(CodeInsightTestFixture fixture, String name) throws Exception {
    // the collapsed state is not compared
    String expected = MdxItTestUtil.loadText("folding/" + name + ".mdx").replaceAll(" expand='[^']*'", "");
    String text = expected.replaceAll("<fold text='[^']*'>", "").replace("</fold>", "");
    fixture.configureByText(name + ".mdx", text);
    String actual = MdxItTestUtil.getFoldingDescription(fixture);
    if (!expected.equals(actual)) {
      Files.createDirectories(Path.of("target/folding-actual"));
      Files.writeString(Path.of("target/folding-actual/" + name + ".mdx"), actual);
    }
    assertEquals(expected, actual);
  }
}
