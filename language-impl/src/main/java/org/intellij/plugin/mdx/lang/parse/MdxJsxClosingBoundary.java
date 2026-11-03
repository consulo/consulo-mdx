package org.intellij.plugin.mdx.lang.parse;

/**
 * Where a closing tag on a line ends: the current JSX element, an ancestor, a mismatched element, or no closing tag.
 */
enum MdxJsxClosingBoundary {
  NONE,
  CURRENT,
  ANCESTOR,
  MISMATCHED,
}
