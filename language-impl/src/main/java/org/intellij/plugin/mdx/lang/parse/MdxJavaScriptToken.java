package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;

record MdxJavaScriptToken(IElementType type, int start, int end) {
}
