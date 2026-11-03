package org.intellij.plugin.mdx.lang;

import consulo.language.template.TemplateLanguage;
import consulo.xml.language.XMLLanguage;
import org.intellij.markdown.MarkdownLanguage;

/**
 * MDX: Markdown with JSX and ESM. An {@link XMLLanguage} whose base language is Markdown.
 */
public final class MdxLanguage extends XMLLanguage implements TemplateLanguage {
  public static final MdxLanguage INSTANCE = new MdxLanguage();

  private MdxLanguage() {
    super(MarkdownLanguage.INSTANCE, "MDX");
  }
}
