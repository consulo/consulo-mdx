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

import consulo.application.ReadAction;
import consulo.it.AllowLogError;
import consulo.it.CodeInsightTestFixture;
import consulo.it.HeadlessProjectExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * References of the embedded JavaScript of MDX files.
 */
@ExtendWith(HeadlessProjectExtension.class)
@AllowLogError({
  "consulo.language.impl.internal.psi.resolve.reference.ReferenceProvidersRegistryImpl",
  "consulo.xml.impl.internal.ExternalResource",
  "consulo.component.internal.NewExtensionPointImpl"
})
public class MdxResolveTest {
  @Test
  public void testResolve(CodeInsightTestFixture fixture) throws Exception {
    fixture.addFileToProject("src/my.mdx", "export const hello = \"hello\"");
    fixture.configureByText("test.mdx", "import {hello} from 'my.mdx'\n<div>{h<caret>ello}</div>");
    PsiReference reference = fixture.getReferenceAtCaretPositionWithAssertion();
    assertNotNull(ReadAction.compute(() -> resolve(reference)));
  }

  /**
   * An expression in the Markdown resolves to an ESM export of the same file: the ESM blocks and the expressions are one
   * JavaScript module.
   */
  @Test
  public void testExpressionResolvesToExportOfTheSameFile(CodeInsightTestFixture fixture) throws Exception {
    fixture.configureByText("test.mdx", "export const year = 2026\n\n# Title\n\nThe year {ye<caret>ar}.");
    PsiReference reference = fixture.getReferenceAtCaretPositionWithAssertion();
    PsiElement target = ReadAction.compute(() -> resolve(reference));
    assertNotNull(target);
    assertEquals("year = 2026", ReadAction.compute(target::getText));
  }

  private static @Nullable PsiElement resolve(PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference polyVariantReference) {
      ResolveResult[] results = polyVariantReference.multiResolve(false);
      return results.length == 0 ? null : results[0].getElement();
    }
    return reference.resolve();
  }
}
