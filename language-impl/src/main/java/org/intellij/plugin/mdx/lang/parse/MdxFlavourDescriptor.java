package org.intellij.plugin.mdx.lang.parse;

import consulo.language.ast.IElementType;
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.html.GeneratingProvider;
import org.intellij.markdown.lexer.MarkdownLexer;
import org.intellij.markdown.parser.LinkMap;
import org.intellij.markdown.parser.LookaheadText;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.MarkerProcessorFactory;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.MarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlock;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.markerblocks.impl.ParagraphMarkerBlock;
import org.intellij.markdown.parser.markerblocks.providers.CodeBlockProvider;
import org.intellij.markdown.parser.markerblocks.providers.HtmlBlockProvider;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager;
import org.intellij.markdown.parser.sequentialparsers.impl.InlineLinkParser;
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultMarkerProcessor;
import org.intellij.plugins.markdown.lang.parser.blocks.CodeFenceMarkerProvider;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The markdown flavour of MDX: GFM inlines plus inline JSX and expressions; the block parser of the Markdown plugin
 * plus ESM, JSX and expression blocks, without HTML blocks and indented code.

 */
public final class MdxFlavourDescriptor extends CommonMarkFlavourDescriptor {
  public static final MdxFlavourDescriptor INSTANCE = new MdxFlavourDescriptor();

  private final GFMFlavourDescriptor myGfmFlavourDescriptor = new GFMFlavourDescriptor();

  private final SequentialParserManager mySequentialParserManager = new SequentialParserManager() {
    @Override
    public List<SequentialParser> getParserSequence() {
      List<SequentialParser> parsers = myGfmFlavourDescriptor.getSequentialParserManager().getParserSequence();
      int inlineLinkIndex = -1;
      for (int i = 0; i < parsers.size(); i++) {
        if (parsers.get(i) instanceof InlineLinkParser) {
          inlineLinkIndex = i;
          break;
        }
      }
      int insertionIndex = inlineLinkIndex == -1 ? parsers.size() : inlineLinkIndex;
      List<SequentialParser> result = new ArrayList<>(parsers.size() + 1);
      result.addAll(parsers.subList(0, insertionIndex));
      result.add(new MdxInlineElementParser());
      result.addAll(parsers.subList(insertionIndex, parsers.size()));
      return result;
    }
  };

  private MdxFlavourDescriptor() {
  }

  @Override
  public MarkerProcessorFactory getMarkerProcessorFactory() {
    return MdxProcessFactory.INSTANCE;
  }

  @Override
  public SequentialParserManager getSequentialParserManager() {
    return mySequentialParserManager;
  }

  @Override
  public Map<IElementType, GeneratingProvider> createHtmlGeneratingProviders(LinkMap linkMap, URI baseURI) {
    return myGfmFlavourDescriptor.createHtmlGeneratingProviders(linkMap, baseURI);
  }

  @Override
  public MarkdownLexer createInlinesLexer() {
    return myGfmFlavourDescriptor.createInlinesLexer();
  }

  private static final class MdxProcessFactory implements MarkerProcessorFactory {
    static final MdxProcessFactory INSTANCE = new MdxProcessFactory();

    @Override
    public MarkerProcessor<?> createMarkerProcessor(ProductionHolder productionHolder) {
      return new MdxMarkerProcessor(productionHolder, MdxMarkdownConstraints.BASE);
    }
  }

  private static final class MdxMarkerProcessor extends MarkdownDefaultMarkerProcessor {
    private final MdxMarkdownOwnership myOwnership = new MdxMarkdownOwnership(this::getMarkerBlockProviders);
    private @Nullable List<MarkerBlockProvider<StateInfo>> myProviders;

    MdxMarkerProcessor(ProductionHolder productionHolder, MarkdownConstraints constraints) {
      super(productionHolder, constraints);
    }

    @Override
    protected void updateStateInfo(LookaheadText.Position pos) {
      super.updateStateInfo(pos);
      if (pos.getOffsetInCurrentLine() > 0) {
        return;
      }
      MarkdownConstraints constraints = pos.getOffsetInCurrentLine() == -1
                                        ? getStateInfo().getNextConstraints()
                                        : getStateInfo().getCurrentConstraints();
      var fenceOpener = myOwnership.observeLine(pos, constraints);
      if (fenceOpener == null) {
        return;
      }
      for (MarkerBlock marker : myMarkersStack) {
        if (marker instanceof MdxJsxBlockMarkerBlock jsxMarker) {
          jsxMarker.addMarkdownOpacity(fenceOpener);
        }
      }
    }

    @Override
    public List<MarkerBlock> createNewMarkerBlocks(LookaheadText.Position pos, ProductionHolder productionHolder) {
      MdxJsxBlockMarkerBlock owner = lastJsxOwnerOnTop();
      Integer openingStart = owner == null ? null : owner.takeOpeningParagraphStart();
      if (owner != null && openingStart != null) {
        return List.of(createOpeningParagraph(owner, openingStart, pos));
      }
      List<MarkerBlock> blocks = super.createNewMarkerBlocks(pos, productionHolder);
      MarkerBlock block = blocks.size() == 1 ? blocks.get(0) : null;
      if (block instanceof MdxJsxBlockMarkerBlock jsxBlock) {
        Integer start = jsxBlock.takeOpeningParagraphStart();
        if (start == null) {
          return blocks;
        }
        List<MarkerBlock> result = new ArrayList<>(blocks);
        result.add(createOpeningParagraph(jsxBlock, start, pos));
        return result;
      }
      if (block instanceof ParagraphMarkerBlock paragraph) {
        MdxJsxBlockMarkerBlock paragraphOwner = lastJsxOwner();
        if (paragraphOwner == null) {
          return blocks;
        }
        return List.of(paragraphOwner.createParagraph(
          pos.getOffset(), pos.getNextLineOrEofOffset(), paragraph.getBlockConstraints(), paragraph.getInterruptsParagraph(),
          paragraphObserver(paragraphOwner)
        ));
      }
      return blocks;
    }

    private @Nullable MdxJsxBlockMarkerBlock lastJsxOwnerOnTop() {
      if (myMarkersStack.isEmpty()) {
        return null;
      }
      return myMarkersStack.get(myMarkersStack.size() - 1) instanceof MdxJsxBlockMarkerBlock jsx ? jsx : null;
    }

    private @Nullable MdxJsxBlockMarkerBlock lastJsxOwner() {
      for (int i = myMarkersStack.size() - 1; i >= 0; i--) {
        if (myMarkersStack.get(i) instanceof MdxJsxBlockMarkerBlock jsx) {
          return jsx;
        }
      }
      return null;
    }

    private MarkerBlock createOpeningParagraph(MdxJsxBlockMarkerBlock owner, int start, LookaheadText.Position pos) {
      MarkdownConstraints constraints = owner.getBlockConstraints();
      BiFunction<LookaheadText.Position, MarkdownConstraints, Boolean> interrupts = (next, nextConstraints) -> {
        if (nextConstraints.getTypes().length <= lastIndexOf(constraints.getTypes(), '>')) {
          return true;
        }
        for (MarkerBlockProvider<StateInfo> provider : getProviders()) {
          if (provider.interruptsParagraph(next, nextConstraints)) {
            return true;
          }
        }
        return false;
      };
      return owner.createParagraph(start, pos.getNextLineOrEofOffset(), constraints, interrupts, paragraphObserver(owner));
    }

    private Consumer<LookaheadText.Position> paragraphObserver(MdxJsxBlockMarkerBlock owner) {
      List<MdxJsxBlockMarkerBlock> owners = new ArrayList<>();
      for (MarkerBlock marker : myMarkersStack) {
        if (marker instanceof MdxJsxBlockMarkerBlock jsx) {
          owners.add(jsx);
        }
      }
      if (owners.isEmpty() || owners.get(owners.size() - 1) != owner) {
        owners.add(owner);
      }
      return pos -> {
        for (int index = owners.size() - 1; index >= 0; index--) {
          owners.get(index).observeParagraphLine(pos);
        }
      };
    }

    @Override
    protected void populateConstraintsTokens(LookaheadText.Position pos, MarkdownConstraints constraints, ProductionHolder productionHolder) {
      super.populateConstraintsTokens(pos, toGfmConstraints(constraints), productionHolder);
    }

    private static MarkdownConstraints toGfmConstraints(MarkdownConstraints constraints) {
      MarkdownConstraints markdown = constraints instanceof MdxJsxMarkdownConstraints jsx ? jsx.markdownConstraints() : constraints;
      return markdown instanceof MdxMarkdownConstraints mdx ? mdx.asGfmConstraints() : markdown;
    }

    @Override
    protected List<MarkerBlockProvider<StateInfo>> getMarkerBlockProviders() {
      return getProviders();
    }

    private List<MarkerBlockProvider<StateInfo>> getProviders() {
      List<MarkerBlockProvider<StateInfo>> providers = myProviders;
      if (providers == null) {
        providers = new ArrayList<>();
        providers.add(new MdxBlockProvider(myOwnership));
        providers.add(new MdxCodeFenceProvider());
        providers.addAll(super.getMarkerBlockProviders());
        providers.removeIf(it -> it instanceof HtmlBlockProvider);
        providers.removeIf(it -> it instanceof CodeBlockProvider);
        providers.removeIf(it -> it instanceof CodeFenceMarkerProvider && !(it instanceof MdxCodeFenceProvider));
        myProviders = providers;
      }
      return providers;
    }

    private static int lastIndexOf(char[] array, char c) {
      for (int i = array.length - 1; i >= 0; i--) {
        if (array[i] == c) {
          return i;
        }
      }
      return -1;
    }
  }
}
