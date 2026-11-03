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

import org.jspecify.annotations.NullMarked;

@NullMarked
module consulo.mdx.language.impl {
    requires consulo.application.api;
    requires consulo.application.content.api;
    requires consulo.base.icon.library;
    requires consulo.base.localize.library;
    requires consulo.code.editor.api;
    requires consulo.color.scheme.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.document.api;
    requires consulo.execution.api;
    requires consulo.file.editor.api;
    requires consulo.ide.api;
    requires consulo.index.io;
    requires consulo.language.api;
    requires consulo.language.code.style.api;
    requires consulo.language.editor.api;
    requires consulo.language.editor.refactoring.api;
    requires consulo.language.impl;
    requires consulo.language.inject.advanced.api;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.module.api;
    requires consulo.navigation.api;
    requires consulo.project.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.util.collection;
    requires consulo.util.dataholder;
    requires consulo.util.io;
    requires consulo.util.lang;
    requires consulo.virtual.file.system.api;

    requires consulo.markdown.engine;
    requires consulo.markdown;
    requires com.intellij.xml.api;
    requires com.intellij.xml.editor.api;
    requires com.intellij.xml.html.api;
    requires com.intellij.xml;
    requires consulo.javascript.base.api;
    requires consulo.javascript.base.impl;
    requires consulo.javascript.ecmascript.impl;

    exports consulo.mdx.icon;
    exports consulo.mdx.localize;
    exports com.intellij.mdx.backend.codeInspection;
    exports com.intellij.mdx.backend.completion;
    exports com.intellij.mdx.backend.format;
    exports com.intellij.mdx.backend.highlighting;
    exports com.intellij.mdx.backend.js;
    exports org.intellij.plugin.mdx.editor;
    exports org.intellij.plugin.mdx.highlighting;
    exports org.intellij.plugin.mdx.js;
    exports org.intellij.plugin.mdx.lang;
    exports org.intellij.plugin.mdx.lang.parse;
    exports org.intellij.plugin.mdx.lang.psi;
    exports org.intellij.plugin.mdx.lang.template;
    exports org.intellij.plugin.mdx.markdown;
}
