package org.intellij.plugin.mdx.lang.template;

import consulo.javascript.language.JavaScriptLanguage;
import consulo.language.Language;
import consulo.language.impl.file.MultiplePsiFilesPerDocumentFileViewProvider;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.parser.ParserDefinition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.template.TemplateLanguageFileViewProvider;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.language.XMLLanguage;
import consulo.xml.language.psi.XmlTag;
import org.intellij.plugin.mdx.lang.MdxLanguage;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The MDX (Markdown) root and the template data root: {@link JavaScriptLanguage} PSI of the {@code MdxJS} version (see
 * {@link MdxTemplateDataElementType}, {@link org.intellij.plugin.mdx.lang.MdxFileType}).
 */
public class MdxFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider implements TemplateLanguageFileViewProvider {
  private static final Set<Language> LANGUAGES = Set.of(MdxLanguage.INSTANCE, JavaScriptLanguage.INSTANCE);

  public MdxFileViewProvider(PsiManager manager, VirtualFile virtualFile, boolean eventSystemEnabled) {
    super(manager, virtualFile, eventSystemEnabled);
  }

  @Override
  protected @Nullable PsiFile createFile(Language lang) {
    if (lang == MdxLanguage.INSTANCE) {
      return super.createFile(lang);
    }

    ParserDefinition parserDefinition = ParserDefinition.forLanguage(lang);
    if (parserDefinition == null) {
      return null;
    }

    PsiFile psiFile = parserDefinition.createFile(this);
    if (lang == getTemplateDataLanguage() && psiFile instanceof PsiFileImpl psiFileImpl) {
      psiFileImpl.setContentElementType(MdxTemplateDataElementType.INSTANCE);
    }
    return psiFile;
  }

  @Override
  public Language getBaseLanguage() {
    return MdxLanguage.INSTANCE;
  }

  @Override
  public Set<Language> getLanguages() {
    return LANGUAGES;
  }

  @Override
  public Language getTemplateDataLanguage() {
    return JavaScriptLanguage.INSTANCE;
  }

  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(VirtualFile fileCopy) {
    return new MdxFileViewProvider(getManager(), fileCopy, false);
  }

  /**
   * XML elements are found in the JSX of the JavaScript root (the {@code MdxJS} version always has JSX).
   */
  @Override
  public @Nullable PsiElement findElementAt(int offset, Class<? extends Language> lang) {
    if (lang == XMLLanguage.class) {
      PsiFile psi = getPsi(JavaScriptLanguage.INSTANCE);
      if (psi != null) {
        PsiElement element = findElementAt(offset);
        if (element == null) {
          return null;
        }
        if (isXmlElement(element)) {
          return element;
        }
      }
    }
    return super.findElementAt(offset, lang);
  }

  private static boolean isXmlElement(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    if (element.getLanguage() instanceof XMLLanguage) {
      return true;
    }
    return element instanceof PsiWhiteSpace && element.getParent() instanceof XmlTag;
  }
}
