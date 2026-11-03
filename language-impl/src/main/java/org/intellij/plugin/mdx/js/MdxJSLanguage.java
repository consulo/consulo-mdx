package org.intellij.plugin.mdx.js;

import consulo.annotation.component.ExtensionImpl;
import consulo.javascript.ecmascript.lang.BaseEcmaScriptJavaScriptVersion;
import consulo.javascript.ecmascript.lang.EcmaScript12JavaScriptVersion;
import consulo.javascript.ecmascript.psi.impl.resolve.EcmaScript6ResolveHelper;
import consulo.javascript.ide.hightlight.JavaScriptHighlighter;
import consulo.javascript.lang.BaseJavaScriptLanguageVersion;
import consulo.javascript.lang.psi.impl.resolve.JavaScriptVersionWithHelper;
import consulo.javascript.lang.psi.impl.resolve.ResolveHelper;
import consulo.javascript.language.JavaScriptFeature;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiParser;

import java.util.Objects;
import java.util.Set;

/**
 * JavaScript with JSX of MDX files: the ESM blocks, JSX elements and expressions, joined into one module.
 * <p>
 * A version of {@link JavaScriptLanguage} (ECMAScript 6+ with JSX), like the other JavaScript dialects. Its lexer is the
 * ECMAScript 6+ lexer (with JSX), its parser {@link MdxJSLanguageParser}. Not offered as the JavaScript version of a module.
 */
@ExtensionImpl
public class MdxJSLanguage extends BaseJavaScriptLanguageVersion implements JavaScriptVersionWithHelper {
  public static final String ID = "MdxJS";

  public MdxJSLanguage() {
    super(ID, JavaScriptLanguage.INSTANCE);
  }

  public static MdxJSLanguage getInstance() {
    return Objects.requireNonNull(JavaScriptLanguage.INSTANCE.findVersionByClass(MdxJSLanguage.class));
  }

  /**
   * ECMAScript 6 and newer, with JSX.
   */
  @Override
  public Set<JavaScriptFeature> getFeatures() {
    return EcmaScript12JavaScriptVersion.getInstance().getFeatures();
  }

  @Override
  public Lexer createLexer() {
    return BaseEcmaScriptJavaScriptVersion.createEcmaScriptLexer(false);
  }

  @Override
  public SyntaxHighlighter getSyntaxHighlighter() {
    return new JavaScriptHighlighter(() -> BaseEcmaScriptJavaScriptVersion.createEcmaScriptLexer(true));
  }

  @Override
  public PsiParser createParser() {
    return new MdxJSLanguageParser();
  }

  @Override
  public ResolveHelper getHelper() {
    return EcmaScript6ResolveHelper.INSTANCE;
  }

  @Override
  public String getPresentableName() {
    return "JavaScript in MDX";
  }
}
