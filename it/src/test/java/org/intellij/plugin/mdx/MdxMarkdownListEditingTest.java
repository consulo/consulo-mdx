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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("needs an editor and the Markdown list editing handlers, which Consulo's Markdown plugin does not have")
public class MdxMarkdownListEditingTest {
  @Test
  public void testEnterContinuesOrderedListInMdx() {
  }

  @Test
  public void testTypingSpaceRenumbersNewOrderedListItemInMdx() {
  }

  @Test
  public void testTabCreatesNestedListItemInMdx() {
  }

  @Test
  public void testBackspaceRemovesListMarkerInMdx() {
  }

  @Test
  public void testEnterContinuesBlockquoteListInMdx() {
  }

  @Test
  public void testMarkdownListSmartKeysAreDisabledInMdxNonMarkdownContexts() {
  }
}
