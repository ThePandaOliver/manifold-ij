package manifold.ij.extensions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import java.util.List;
import manifold.api.templ.DisableStringLiteralTemplates;
import manifold.api.templ.StringLiteralTemplateParser;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.ComputeUtil;
import manifold.internal.javac.JavacPlugin;
import org.jetbrains.annotations.NotNull;

public class ManStringLiteralTemplateInjector implements LanguageInjector
{
  private static final String PREFIX =
    "class _Muh_Class_ {\n" +
    "  Object _muhField_ = ";
  private static final String SUFFIX =
    ";\n" +
    "}\n";


  @Override
  public void getLanguagesToInject( @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar )
  {
    PsiLiteralExpressionImpl stringLiteral = getJavaStringLiteral( host );
    if( stringLiteral == null )
    {
      return;
    }

    if( isStringLiteralTemplatesDisabled( stringLiteral ) )
    {
      return;
    }

    String hostText = host.getText();
    List<StringLiteralTemplateParser.Expr> exprs =
      StringLiteralTemplateParser.parse( index -> isEscaped( hostText, index ), hostText );
    if( exprs.isEmpty() )
    {
      // Not a template
      return;
    }

    for( StringLiteralTemplateParser.Expr expr: exprs )
    {
      if( !expr.isVerbatim() )
      {
        injectionPlacesRegistrar.addPlace(
          JavaLanguage.INSTANCE,
          new TextRange( expr.getOffset(), expr.getOffset() + expr.getExpr().length() ),
          PREFIX, SUFFIX );
      }
    }
  }

  private boolean isEscaped( String hostText, int index )
  {
    if( index > 0 )
    {
      if( hostText.charAt( index-1 ) == '\\' )
      {
        if( index > 1 )
        {
          return hostText.charAt( index-2 ) != '\\';
        }
      }
    }
    return false;
  }

  private boolean isStringLiteralTemplatesDisabled( PsiElement elem )
  {
    if( elem == null )
    {
      return false;
    }

    ManModule module = ManProject.getModule( elem );
    if( module != null && !module.isPluginArgEnabled( JavacPlugin.ARG_STRINGS ))
    {
      return true;
    }

    if( elem instanceof PsiModifierListOwner )
    {
      for( PsiAnnotation anno: ((PsiModifierListOwner)elem).getAnnotations() )
      {
        if( DisableStringLiteralTemplates.class.getTypeName().equals( anno.getQualifiedName() ) )
        {
          final PsiNameValuePair[] attributes = anno.getParameterList().getAttributes();
          if( attributes.length > 0 )
          {
            Object value = ComputeUtil.computeLiteralValue( attributes[0] );
            return !(value instanceof Boolean) || (boolean)value;
          }
          return true;
        }
      }
    }

    PsiElement parent = elem.getParent();
    if( parent == null || parent instanceof PsiJavaFile )
    {
      return false;
    }

    return isStringLiteralTemplatesDisabled( elem.getParent() );
  }

  private PsiLiteralExpressionImpl getJavaStringLiteral( @NotNull PsiLanguageInjectionHost host )
  {
    // Only applies to Java string literal expression
    if( host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) &&
           host instanceof PsiLiteralExpressionImpl )
    {
      PsiLiteralExpressionImpl literalExpr = (PsiLiteralExpressionImpl)host;
      if( literalExpr.getLiteralElementType() == JavaTokenType.STRING_LITERAL )
      {
        return literalExpr;
      }
    }
    return null;
  }
}
