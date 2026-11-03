// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugin.mdx.lang;

import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.impl.JSEmbeddedContentImpl;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.xml.language.psi.XmlTag;
import org.intellij.plugin.mdx.lang.parse.MdxElementTypes;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The comment syntax of a place in an MDX file.
 */
public enum MdxCommentContext {
  MDX(null, "{/*", "*/}"),
  JAVASCRIPT("//", "/*", "*/");

  private static final Set<IElementType> TAG_ELEMENTS = Set.of(
    MdxElementTypes.MDX_JSX_OPENING_ELEMENT,
    MdxElementTypes.MDX_JSX_CLOSING_ELEMENT,
    MdxElementTypes.MDX_JSX_SELF_CLOSING_ELEMENT,
    MdxElementTypes.MDX_JSX_ATTRIBUTE
  );

  final @Nullable String linePrefix;
  final String blockPrefix;
  final String blockSuffix;

  MdxCommentContext(@Nullable String linePrefix, String blockPrefix, String blockSuffix) {
    this.linePrefix = linePrefix;
    this.blockPrefix = blockPrefix;
    this.blockSuffix = blockSuffix;
  }

  /**
   * Selects one comment context for a range in the committed MDX document.
   */
  @RequiredReadAction
  static MdxCommentContext mdxCommentContext(PsiFile file, TextRange range, MdxCommentContext tagContext) {
    PsiFile mdx = file.getViewProvider().getPsi(MdxLanguage.INSTANCE);
    if (mdx == null) {
      return MDX;
    }
    PsiElement element = commonElement(mdx, range);
    if (element == null) {
      return MDX;
    }
    PsiElement construct = null;
    for (PsiElement e = element; e != null; e = e.getParent()) {
      IElementType type = PsiUtilCore.getElementType(e);
      if (type == MdxElementTypes.MDX_ESM_BLOCK || type == MdxElementTypes.MDX_EXPRESSION) {
        construct = e;
        break;
      }
    }
    if (construct == null) {
      for (PsiElement e = element; e != null; e = e.getParent()) {
        if (TAG_ELEMENTS.contains(PsiUtilCore.getElementType(e))) {
          return tagContext;
        }
      }
      return MDX;
    }

    TextRange constructRange = construct.getTextRange();
    if (range.getStartOffset() <= constructRange.getStartOffset()) {
      return MDX;
    }
    if (PsiUtilCore.getElementType(construct) == MdxElementTypes.MDX_EXPRESSION &&
        mdx.getViewProvider().getContents().charAt(constructRange.getEndOffset() - 1) == '}' &&
        range.getEndOffset() >= constructRange.getEndOffset()) {
      return MDX;
    }

    PsiFile javascript = file.getViewProvider().getPsi(JavaScriptLanguage.INSTANCE);
    if (javascript == null) {
      return MDX;
    }
    PsiElement jsElement = commonElement(javascript, range);
    if (jsElement == null) {
      return MDX;
    }
    for (PsiElement parent = jsElement; parent != null; parent = parent.getParent()) {
      // JSX {expressions} are JSEmbeddedContentImpl
      if (parent instanceof JSEmbeddedContentImpl && insideBraces(parent, range)) {
        return JAVASCRIPT;
      }
      if (parent instanceof XmlTag tag) {
        TextRange body = jsxBodyRange(tag);
        if (body.contains(range)) {
          return MDX;
        }
        if (range.getEndOffset() <= body.getStartOffset() || range.getStartOffset() >= body.getEndOffset()) {
          return tagContext;
        }
      }
    }
    return JAVASCRIPT;
  }

  @RequiredReadAction
  private static TextRange jsxBodyRange(XmlTag tag) {
    PsiElement first = tag.getFirstChild();
    if (first == null || PsiUtilCore.getElementType(first) != JSTokenTypes.XML_START_TAG_LIST) {
      return tag.getValue().getTextRange();
    }
    PsiElement last = tag.getLastChild();
    int end = last != null && PsiUtilCore.getElementType(last) == JSTokenTypes.XML_END_TAG_LIST ? last.getTextRange().getStartOffset() : tag.getTextRange().getEndOffset();
    return new TextRange(first.getTextRange().getEndOffset(), end);
  }

  @RequiredReadAction
  private static boolean insideBraces(PsiElement element, TextRange range) {
    PsiElement first = element.getFirstChild();
    PsiElement last = element.getLastChild();
    if (first == null || last == null) {
      return false;
    }
    return first.getText().equals("{") && range.getStartOffset() >= first.getTextRange().getEndOffset() &&
           (!last.getText().equals("}") || range.getEndOffset() <= last.getTextRange().getStartOffset());
  }

  /**
   * Finds the common element in the specified PSI root.
   */
  @RequiredReadAction
  static @Nullable PsiElement commonElement(PsiFile file, TextRange range) {
    if (file.getTextLength() == 0) {
      return null;
    }
    ASTNode startNode = file.getNode().findLeafElementAt(Math.min(range.getStartOffset(), file.getTextLength() - 1));
    if (startNode == null) {
      return null;
    }
    PsiElement start = startNode.getPsi();
    if (start == null || range.isEmpty()) {
      return start;
    }
    ASTNode endNode = file.getNode().findLeafElementAt(range.getEndOffset() - 1);
    PsiElement end = endNode == null ? null : endNode.getPsi();
    if (end == null) {
      return null;
    }
    return PsiTreeUtil.findCommonParent(start, end);
  }
}
