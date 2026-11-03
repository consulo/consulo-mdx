package org.intellij.plugin.mdx.js;

import com.intellij.lang.javascript.JSElementTypes;
import com.intellij.lang.javascript.JSTokenTypes;
import consulo.javascript.ecmascript.lang.parsing.EcmaScriptParser;
import consulo.javascript.ecmascript.lang.parsing.EcmaScriptParsingContext;
import consulo.javascript.ecmascript.lang.parsing.EcmaScriptStatementParsing;
import consulo.javascript.lang.parsing.JavaScriptParsingContext;
import consulo.javascript.lang.parsing.StatementParsing;
import consulo.language.ast.IElementType;
import consulo.language.parser.PsiBuilder;

/**
 * The parser of {@link MdxJSLanguage}: ECMAScript 6+, where a JSX element at the start of a statement is an expression
 * statement which needs no semicolon (the MDX template text separates the pieces itself).
 * <p>
 * The file element type is the one of JavaScript: the language version decides the parser.
 */
public class MdxJSLanguageParser extends EcmaScriptParser {
  @Override
  public JavaScriptParsingContext createParsingContext() {
    return new EcmaScriptParsingContext() {
      @Override
      protected StatementParsing createStatementParsing() {
        return new MdxStatementParsing(this);
      }
    };
  }

  private static final class MdxStatementParsing extends EcmaScriptStatementParsing {
    MdxStatementParsing(EcmaScriptParsingContext context) {
      super(context);
    }

    @Override
    protected void doParseStatement(PsiBuilder builder, boolean canHaveClasses) {
      IElementType tokenType = builder.getTokenType();
      // a fragment <> is one XML_START_TAG_LIST token
      if (tokenType == JSTokenTypes.XML_START_TAG_START || tokenType == JSTokenTypes.XML_START_TAG_LIST) {
        PsiBuilder.Marker exprStatement = builder.mark();
        if (getExpressionParsing().parseExpressionOptional(builder)) {
          exprStatement.done(JSElementTypes.EXPRESSION_STATEMENT);
        }
        else {
          exprStatement.drop();
        }
      }
      else {
        super.doParseStatement(builder, canHaveClasses);
      }
    }
  }
}
