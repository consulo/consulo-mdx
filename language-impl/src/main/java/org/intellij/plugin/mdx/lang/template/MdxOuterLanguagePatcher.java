package org.intellij.plugin.mdx.lang.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.Language;
import consulo.language.impl.psi.template.OuterLanguageRangePatcher;
import consulo.language.impl.psi.template.TemplateDataElementType;
import org.jspecify.annotations.Nullable;

/**
 * Separates the JavaScript pieces of an MDX file with {@code \n;}. Registered for {@link JavaScriptLanguage}, the template
 * data language of MDX files; answers only for {@link MdxTemplateDataElementType}.
 */
@ExtensionImpl
public class MdxOuterLanguagePatcher implements OuterLanguageRangePatcher {
  @Override
  public @Nullable String getTextForOuterLanguageInsertionRange(TemplateDataElementType templateDataElementType,
                                                                CharSequence outerElementText) {
    if (templateDataElementType == MdxTemplateDataElementType.INSTANCE) {
      return "\n;";
    }
    return null;
  }

  @Override
  public Language getLanguage() {
    return JavaScriptLanguage.INSTANCE;
  }
}
