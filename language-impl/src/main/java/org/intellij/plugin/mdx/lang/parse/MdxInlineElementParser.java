package org.intellij.plugin.mdx.lang.parse;

import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import org.intellij.markdown.parser.sequentialparsers.IntRange;
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.TokensCache;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Token ranges are the engine's {@link IntRange}s: {@code new Node(range.getFirst(), range.getLast(), type)} is a half-open
 * token index range, as in the engine's own parsers.
 */
final class MdxInlineElementParser implements SequentialParser {
  @Override
  public ParsingResult parse(TokensCache tokens, List<IntRange> rangesToGlue) {
    ParsingResultBuilder result = new ParsingResultBuilder();
    if (rangesToGlue.isEmpty()) return result.withFurtherProcessing(List.of());

    TokenPartition tokenPartition = new TokenPartition(tokens, rangesToGlue);
    List<TextRange> outerExcludedRanges = new ArrayList<>();
    List<List<IntRange>> innerParsingSpaces = new ArrayList<>();
    CharSequence text = tokens.getOriginalText();
    MdxTextRangeSet opaqueRanges = tokenPartition.opaqueRanges();

    int offset = tokens.new Iterator(rangesToGlue.get(0).getFirst()).getStart();
    TokensCache.Iterator limitIterator = tokens.new Iterator(rangesToGlue.get(rangesToGlue.size() - 1).getLast());
    int limit = limitIterator.getType() == null ? limitIterator.getStart() : limitIterator.getEnd();
    while (offset < limit) {
      Integer opaqueEnd = opaqueRanges.endOffsetContaining(offset);
      if (opaqueEnd != null) {
        offset = Math.min(opaqueEnd, limit);
        continue;
      }
      char c = text.charAt(offset);
      if (c == '<') {
        MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(text, offset, limit, opaqueRanges);
        if (element != null && element.termination() != MdxJsxScanner.Termination.UNTERMINATED) {
          IntRange rootRange = addNode(result, tokenPartition, element.range(), MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
          MdxJsxScanner.ElementRecord[] owners = new MdxJsxScanner.ElementRecord[element.elements().size()];
          for (MdxJsxScanner.ElementRecord owner : element.elements()) {
            owners[owner.index()] = owner;
            if (owner.parentIndex() != null) {
              addNode(result, tokenPartition, owner.range(), MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
            }
          }
          List<TextRange> innerExcludedRanges = new ArrayList<>();
          for (MdxJsxScanner.Tag tag : element.tags()) {
            IElementType tagType = switch (tag.kind()) {
              case OPENING -> MdxMarkdownLibElementTypes.MDX_JSX_OPENING_ELEMENT;
              case CLOSING -> MdxMarkdownLibElementTypes.MDX_JSX_CLOSING_ELEMENT;
              case SELF_CLOSING -> MdxMarkdownLibElementTypes.MDX_JSX_SELF_CLOSING_ELEMENT;
            };
            IntRange tagTokenRange = addNode(result, tokenPartition, tag.range(), tagType);
            for (TextRange attribute : tag.attributes()) {
              IntRange attributeRange = tokenPartition.toTokenRange(attribute);
              if (attributeRange != null && tagTokenRange != null && isStrictlyInside(attributeRange, tagTokenRange)) {
                result.withNode(new Node(attributeRange.getFirst(), attributeRange.getLast(), MdxMarkdownLibElementTypes.MDX_JSX_ATTRIBUTE));
              }
            }
            innerExcludedRanges.add(tag.range());
          }
          for (TextRange expression : element.expressions()) {
            IntRange expressionRange = tokenPartition.toTokenRange(expression);
            if (expressionRange != null && rootRange != null && isStrictlyInside(expressionRange, rootRange)) {
              result.withNode(new Node(expressionRange.getFirst(), expressionRange.getLast(), MdxMarkdownLibElementTypes.MDX_EXPRESSION));
            }
            innerExcludedRanges.add(expression);
          }
          outerExcludedRanges.add(element.range());
          List<MdxJsxScanner.ElementRecord> ownerList = new ArrayList<>(owners.length);
          for (MdxJsxScanner.ElementRecord owner : owners) {
            if (owner == null) throw new IllegalStateException("Element record is missing");
            ownerList.add(owner);
          }
          innerParsingSpaces.addAll(tokenPartition.bodyParsingSpaces(ownerList, innerExcludedRanges));
          offset = element.range().getEndOffset();
          continue;
        }
        TextRange openingPrefix = MdxJsxScanner.incompleteOpeningTagRange(text, offset, limit);
        if (openingPrefix != null) {
          addNode(result, tokenPartition, openingPrefix, MdxMarkdownLibElementTypes.MDX_JSX_TEXT_ELEMENT);
          addNode(result, tokenPartition, openingPrefix, MdxMarkdownLibElementTypes.MDX_JSX_OPENING_ELEMENT);
          outerExcludedRanges.add(openingPrefix);
          offset = openingPrefix.getEndOffset();
          continue;
        }
      }
      else if (c == '{') {
        int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(text, offset, limit);
        if (expressionEnd != -1) {
          TextRange expressionRange = new TextRange(offset, expressionEnd);
          addNode(result, tokenPartition, expressionRange, MdxMarkdownLibElementTypes.MDX_EXPRESSION);
          outerExcludedRanges.add(expressionRange);
          offset = expressionEnd;
          continue;
        }
      }
      offset++;
    }

    result.withFurtherProcessing(tokenPartition.parsingSpace(outerExcludedRanges));
    for (List<IntRange> innerParsingSpace : innerParsingSpaces) {
      result.withFurtherProcessing(innerParsingSpace);
    }
    return result;
  }

  private static @Nullable IntRange addNode(ParsingResultBuilder result,
                                            TokenPartition tokenPartition,
                                            TextRange charRange,
                                            IElementType type) {
    IntRange tokenRange = tokenPartition.toTokenRange(charRange);
    if (tokenRange == null) return null;
    result.withNode(new Node(tokenRange.getFirst(), tokenRange.getLast(), type));
    return tokenRange;
  }

  private static boolean isStrictlyInside(IntRange range, IntRange parent) {
    return parent.getFirst() <= range.getFirst() &&
           range.getLast() <= parent.getLast() &&
           (parent.getFirst() != range.getFirst() || parent.getLast() != range.getLast());
  }

  private static final class TokenPartition {
    private final List<IndexedToken> allTokens;
    private final boolean[] parsingTokenFlags;
    private final List<IndexedToken> parsingTokens;

    TokenPartition(TokensCache tokens, List<IntRange> parsingRanges) {
      allTokens = collectTokens(tokens.new Iterator(0));
      parsingTokenFlags = new boolean[tokens.getFilteredTokens().size()];
      for (IntRange range : parsingRanges) {
        int first = Math.max(range.getFirst(), 0);
        int last = Math.min(range.getLast(), parsingTokenFlags.length - 1);
        for (int index = first; index <= last; index++) {
          parsingTokenFlags[index] = true;
        }
      }
      parsingTokens = new ArrayList<>();
      for (IndexedToken token : allTokens) {
        if (isParsingToken(token)) parsingTokens.add(token);
      }
    }

    MdxTextRangeSet opaqueRanges() {
      List<TextRange> ranges = new ArrayList<>();
      int position = 0;
      while (position < allTokens.size()) {
        if (isParsingToken(allTokens.get(position))) {
          position++;
          continue;
        }
        int start = allTokens.get(position).start();
        int end = allTokens.get(position).end();
        position++;
        while (position < allTokens.size() && !isParsingToken(allTokens.get(position))) {
          end = allTokens.get(position).end();
          position++;
        }
        if (start < end) ranges.add(new TextRange(start, end));
      }
      return MdxTextRangeSet.of(ranges);
    }

    @Nullable
    IntRange toTokenRange(TextRange charRange) {
      int first = firstEndingAfter(allTokens, charRange.getStartOffset());
      int afterLast = firstStartingAtOrAfter(allTokens, charRange.getEndOffset());
      if (first >= afterLast) return null;
      return new IntRange(allTokens.get(first).index(), allTokens.get(afterLast - 1).index() + 1);
    }

    List<IntRange> parsingSpace(Collection<TextRange> excludedRanges) {
      MdxTextRangeSet exclusions = MdxTextRangeSet.of(excludedRanges);
      int exclusionIndex = 0;
      RangesListBuilder result = new RangesListBuilder();
      for (IndexedToken token : parsingTokens) {
        int start = token.start();
        int end = token.end();
        while (exclusionIndex < exclusions.size() && exclusions.get(exclusionIndex).getEndOffset() <= start) {
          exclusionIndex++;
        }
        boolean excluded = exclusionIndex < exclusions.size() &&
                           exclusions.get(exclusionIndex).getStartOffset() < end &&
                           start < exclusions.get(exclusionIndex).getEndOffset();
        if (!excluded) {
          result.put(token.index());
        }
      }
      return result.get();
    }

    List<List<IntRange>> bodyParsingSpaces(List<MdxJsxScanner.ElementRecord> owners, Collection<TextRange> excludedRanges) {
      if (owners.isEmpty()) return List.of();
      MdxJsxScanner.ElementRecord root = owners.get(0);
      List<RangesListBuilder> builders = new ArrayList<>(owners.size());
      for (int i = 0; i < owners.size(); i++) {
        builders.add(new RangesListBuilder());
      }
      MdxTextRangeSet exclusions = MdxTextRangeSet.of(excludedRanges);
      int exclusionIndex = 0;
      int nextOwner = 0;
      MdxJsxScanner.ElementRecord owner = null;
      int first = firstEndingAfter(parsingTokens, root.range().getStartOffset());
      int afterLast = firstStartingAtOrAfter(parsingTokens, root.range().getEndOffset());
      for (int position = first; position < afterLast; position++) {
        IndexedToken token = parsingTokens.get(position);
        while (nextOwner < owners.size() && owners.get(nextOwner).range().getStartOffset() < token.end()) {
          owner = owners.get(nextOwner++);
        }
        while (owner != null && owner.range().getEndOffset() <= token.start()) {
          Integer parentIndex = owner.parentIndex();
          owner = parentIndex == null ? null : owners.get(parentIndex);
        }
        while (exclusionIndex < exclusions.size() && exclusions.get(exclusionIndex).getEndOffset() <= token.start()) {
          exclusionIndex++;
        }
        if (exclusionIndex < exclusions.size() && exclusions.get(exclusionIndex).getStartOffset() < token.end()) continue;
        if (owner == null) continue;
        TextRange body = owner.bodyRange();
        if (body == null) continue;
        if (body.getStartOffset() <= token.start() && token.end() <= body.getEndOffset()) {
          builders.get(owner.index()).put(token.index());
        }
      }
      List<List<IntRange>> result = new ArrayList<>();
      for (RangesListBuilder builder : builders) {
        List<IntRange> ranges = builder.get();
        if (!ranges.isEmpty()) result.add(ranges);
      }
      return result;
    }

    private static List<IndexedToken> collectTokens(TokensCache.Iterator initialIterator) {
      List<IndexedToken> result = new ArrayList<>();
      TokensCache.Iterator iterator = initialIterator;
      while (iterator.getType() != null) {
        result.add(new IndexedToken(iterator.getIndex(), iterator.getStart(), iterator.getEnd()));
        iterator = iterator.advance();
      }
      return result;
    }

    private boolean isParsingToken(IndexedToken token) {
      return token.index() >= 0 && token.index() < parsingTokenFlags.length && parsingTokenFlags[token.index()];
    }

    private static int firstEndingAfter(List<IndexedToken> tokens, int offset) {
      int low = 0;
      int high = tokens.size();
      while (low < high) {
        int middle = (low + high) >>> 1;
        if (tokens.get(middle).end() <= offset) {
          low = middle + 1;
        }
        else {
          high = middle;
        }
      }
      return low;
    }

    private static int firstStartingAtOrAfter(List<IndexedToken> tokens, int offset) {
      int low = 0;
      int high = tokens.size();
      while (low < high) {
        int middle = (low + high) >>> 1;
        if (tokens.get(middle).start() < offset) {
          low = middle + 1;
        }
        else {
          high = middle;
        }
      }
      return low;
    }

    private record IndexedToken(int index, int start, int end) {
    }
  }
}
