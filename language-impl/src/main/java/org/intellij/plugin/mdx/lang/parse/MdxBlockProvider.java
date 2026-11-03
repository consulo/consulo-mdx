package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.jspecify.annotations.Nullable;

import java.util.List;

final class MdxBlockProvider implements MarkerBlockProvider<MarkerProcessor.StateInfo> {
  private final MdxMarkdownOwnership myOwnership;

  MdxBlockProvider(MdxMarkdownOwnership ownership) {
    myOwnership = ownership;
  }

  @Override
  public List<MarkerBlock> createMarkerBlocks(LookaheadText.Position pos,
                                              ProductionHolder productionHolder,
                                              MarkerProcessor.StateInfo stateInfo) {
    StartInfo start = findStart(pos, stateInfo.getCurrentConstraints());
    if (start == null) {
      return List.of();
    }
    CharSequence lineText = pos.getCurrentLineFromPosition();
    CharSequence localText = lineText.subSequence(start.offsetInLine(), lineText.length());
    int absoluteStart = pos.getOffset() + start.offsetInLine();
    ImmediateBlock immediateBlock = switch (start.kind()) {
      case ESM -> {
        MdxEsmScanner.Block block = MdxEsmScanner.scanBlock(localText, 0);
        if (block != null && block.terminated() && isBlank(localText.subSequence(block.range().getEndOffset(), localText.length()))) {
          yield new ImmediateBlock(
            MdxMarkdownLibElementTypes.MDX_ESM_BLOCK,
            MdxBlockNodeFactory.createEsmNodes(block, absoluteStart, false)
          );
        }
        yield null;
      }
      case JSX -> null;
      case EXPRESSION -> {
        int expressionEnd = MdxExpressionBoundaryScanner.findExpressionEnd(localText, 0, localText.length());
        if (expressionEnd != -1 && isBlank(localText.subSequence(expressionEnd, localText.length()))) {
          yield new ImmediateBlock(
            MdxMarkdownLibElementTypes.MDX_EXPRESSION,
            List.of(new SequentialParser.Node(absoluteStart, absoluteStart + expressionEnd, MdxMarkdownLibTokenTypes.EMBEDDED_JS_CONTENT))
          );
        }
        yield null;
      }
    };
    if (immediateBlock != null) {
      return List.of(new ImmediateMdxBlockMarkerBlock(
        stateInfo.getCurrentConstraints(),
        productionHolder,
        immediateBlock.type(),
        immediateBlock.children()
      ));
    }
    MarkerBlock marker = switch (start.kind()) {
      case JSX -> new MdxJsxBlockMarkerBlock(
        stateInfo.getCurrentConstraints(),
        myOwnership,
        productionHolder,
        absoluteStart,
        pos.getOffsetInCurrentLine() + start.offsetInLine(),
        MdxJsxScanner.openingElementIdentity(localText, 0),
        pos.getOriginalText(),
        localText.toString()
      );
      case ESM -> MdxOpaqueBlockMarkerBlock.esm(
        stateInfo.getCurrentConstraints(),
        productionHolder,
        absoluteStart,
        pos.getOriginalText(),
        localText.toString()
      );
      case EXPRESSION -> MdxOpaqueBlockMarkerBlock.expression(
        stateInfo.getCurrentConstraints(),
        productionHolder,
        absoluteStart,
        pos.getOriginalText(),
        localText.toString()
      );
    };
    return List.of(marker);
  }

  @Override
  public boolean interruptsParagraph(LookaheadText.Position pos, MarkdownConstraints constraints) {
    StartInfo start = findStart(pos, constraints);
    if (start == null) {
      return false;
    }
    return switch (start.kind()) {
      case ESM -> false;
      case JSX, EXPRESSION -> true;
    };
  }

  private static @Nullable StartInfo findStart(LookaheadText.Position pos, MarkdownConstraints constraints) {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, constraints)) {
      return null;
    }
    CharSequence text = pos.getCurrentLineFromPosition();
    int offset = MarkerBlockProvider.passSmallIndent(text);
    if (offset >= text.length()) {
      return null;
    }
    if (constraints.getTypes().length == 0 && MdxEsmScanner.isLineStart(text, offset)) {
      return new StartInfo(offset, MdxBlockKind.ESM);
    }
    if (text.charAt(offset) == '<' && isLineStartJsxBlock(text, offset)) {
      return new StartInfo(offset, MdxBlockKind.JSX);
    }
    if (text.charAt(offset) == '{') {
      return new StartInfo(offset, MdxBlockKind.EXPRESSION);
    }
    return null;
  }

  private static boolean isLineStartJsxBlock(CharSequence text, int offset) {
    if (!MdxJsxScanner.isLineStartJsx(text, offset)) {
      return false;
    }

    CharSequence localText = text.subSequence(offset, text.length());
    MdxJsxScanner.Element element = MdxJsxScanner.scanJsxElement(localText, 0);
    return element == null ||
           element.termination() == MdxJsxScanner.Termination.UNTERMINATED ||
           isBlank(localText.subSequence(element.range().getEndOffset(), localText.length()));
  }

  static boolean isBlank(CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private record StartInfo(int offsetInLine, MdxBlockKind kind) {
  }

  private record ImmediateBlock(IElementType type, List<SequentialParser.Node> children) {
  }

  private enum MdxBlockKind {
    JSX,
    ESM,
    EXPRESSION
  }
}
