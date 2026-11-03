package org.intellij.plugin.mdx.lang.parse;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface MdxOpaqueRangeLookup {
  @Nullable
  Integer endOffsetContaining(int offset);
}
