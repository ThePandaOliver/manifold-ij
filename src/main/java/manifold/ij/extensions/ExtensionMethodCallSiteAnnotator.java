package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import manifold.ij.core.ManModule;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

/**
 *  Filters extension methods where the method is from a module not accessible from the call-site
 */
public class ExtensionMethodCallSiteAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( element instanceof PsiMethodCallExpression )
    {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      PsiElement member = methodExpression.resolve();
      if( member instanceof ManLightMethodBuilder )
      {
        Module callSiteModule = ModuleUtil.findModuleForPsiElement( element );
        if( callSiteModule != null )
        {
          if( ((ManLightMethodBuilder)member).getModules().stream()
            .map( ManModule::getIjModule )
            .noneMatch( extensionModule -> isAccessible( callSiteModule, extensionModule, methodExpression ) ) )
          {
            // The extension method is from a module not accessible from the call-site
            PsiElement methodElem = methodExpression.getReferenceNameElement();
            if( methodElem != null )
            {
              TextRange textRange = methodElem.getTextRange();
              TextRange range = new TextRange( textRange.getStartOffset(), textRange.getEndOffset() );
              holder.createErrorAnnotation( range, JavaErrorMessages.message( "cannot.resolve.method", methodExpression.getReferenceName() ) );
            }
          }
        }
      }
    }
  }

  private boolean isAccessible( Module callSiteModule, Module extensionModule, PsiJavaCodeReferenceElement methodExpression )
  {
    // Is the extension method from a module accessible from the call-site?
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( callSiteModule );
    return scope.isSearchInModuleContent( extensionModule ) || methodExpression.getReferenceNameElement() == null;
  }
}
