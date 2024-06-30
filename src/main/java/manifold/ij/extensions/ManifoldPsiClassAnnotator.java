/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import com.intellij.util.SmartList;
import manifold.api.fs.IFileFragment;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ManifoldPsiClassAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // No manifold jars in use for this project
      return;
    }

    if( element instanceof PsiFile )
    {
      // We don't have file-level errors here, only parse errors wrt elements.
      // Otherwise, we'll have duplicated errors.
      return;
    }

    if( element instanceof PsiFileFragment )
    {
      highlightFragment( (PsiFileFragment)element, holder );
    }
    else
    {
      Set<PsiClass> psiClasses = ResourceToManifoldUtil.findPsiClass( element.getContainingFile() );
      Set<Diagnostic<? extends JavaFileObject>> reported = new HashSet<>();
      psiClasses.forEach( psiClass -> annotate( psiClass, element, holder, reported ) );
    }
  }

  private void highlightFragment( @NotNull PsiFileFragment fragmentFile, @NotNull AnnotationHolder holder )
  {
    ManModule manMod = ManProject.getModule( fragmentFile );
    IFileFragment fragment = fragmentFile.getFragment();
    if( manMod != null && fragment != null )
    {
      String[] fqns = manMod.getTypesForFile( fragment );
      for( String fqn: fqns )
      {
        PsiClass psiClass = ManifoldPsiClassCache.getPsiClass( manMod, fqn );
        if( psiClass instanceof ManifoldPsiClass && ((ManifoldPsiClass)psiClass).isFragment() )
        {
          DiagnosticCollector<JavaFileObject> issues = ((ManifoldPsiClass)psiClass).getIssues();
          if( issues == null )
          {
            break;
          }

//          PsiFile containingFile = fragmentFile.getContainingFile();
          Set<Diagnostic<JavaFileObject>> reported = new HashSet<>();
          for( Diagnostic<? extends JavaFileObject> issue : issues.getDiagnostics() )
          {
            if( !reported.contains( issue ) )
            {
              boolean created;
//              if( containingFile instanceof PsiPlainTextFile )
//              {
                created = createIssueOnTextRange( holder, issue );
//              }
//              else
//              {
//                created = createIssueOnElement( holder, issue, element );
//              }
              if( created )
              {
                reported.add( (Diagnostic<JavaFileObject>)issue );
              }
            }
          }
          break;
        }
      }
    }
  }


  private void annotate( PsiClass psiClass, PsiElement element, AnnotationHolder holder, Set<Diagnostic<? extends JavaFileObject>> reported )
  {
    PsiFile containingFile = element.getContainingFile();
    if( PsiErrorClassUtil.isErrorClass( psiClass ) && element instanceof PsiFileSystemItem )
    {
      String message = PsiErrorClassUtil.getErrorFrom( psiClass ).getMessage();
      String tooltip = makeTooltip( message );
      holder.newAnnotation( HighlightSeverity.ERROR, message )
        .range( new TextRange( 0, containingFile.getTextLength() ) )
        .tooltip( tooltip )
        .create();
      return;
    }

    if( !(psiClass instanceof ManifoldPsiClass) )
    {
      return;
    }

    if( containingFile instanceof PsiPlainTextFile )
    {
      // IJ doesn't clear previously added annotations, so we do this bullshit

      if( ApplicationManager.getApplication().isDispatchThread() )
      {
        removeAllHighlighters( containingFile );
      }
      else
      {
        ApplicationManager.getApplication().invokeLater( () -> removeAllHighlighters( containingFile ) );
      }
    }

    DiagnosticCollector<JavaFileObject> issues = ((ManifoldPsiClass)psiClass).getIssues();
    if( issues == null )
    {
      return;
    }

    for( Diagnostic<? extends JavaFileObject> issue : issues.getDiagnostics() )
    {
      if( !reported.contains( issue ) )
      {
        boolean created;
        if( containingFile instanceof PsiPlainTextFile )
        {
          created = createIssueOnTextRange( holder, issue );
        }
        else
        {
          created = createIssueOnElement( holder, issue, element );
        }
        if( created )
        {
          reported.add( issue );
        }
      }
    }
  }

  private static void removeAllHighlighters( PsiFile containingFile )
  {
    Project project = containingFile.getProject();
    Document document = PsiDocumentManager.getInstance( project ).getDocument( containingFile );
    if( document != null )
    {
      MarkupModel markupModel = DocumentMarkupModel.forDocument( document, project, false );
      if( markupModel != null )
      {
        markupModel.removeAllHighlighters();
      }
    }
  }

  private boolean createIssueOnTextRange( AnnotationHolder holder, Diagnostic<? extends JavaFileObject> issue )
  {
    TextRange range = new TextRange( (int)issue.getStartPosition(), (int)issue.getEndPosition() );
    String message = makeMessage( issue );
    String tooltip = makeTooltip( message );
    switch( issue.getKind() )
    {
      case ERROR:
        holder.newAnnotation( HighlightSeverity.ERROR, message )
          .range( range )
          .tooltip( tooltip )
          .create();
        break;
      case WARNING:
      case MANDATORY_WARNING:
        holder.newAnnotation( HighlightSeverity.WARNING, message )
          .range( range )
          .tooltip( tooltip )
          .create();
        break;
      case NOTE:
      case OTHER:
        holder.newAnnotation( HighlightSeverity.INFORMATION, message )
          .range( range )
          .tooltip( tooltip )
          .create();
        break;
    }
    return true;
  }

  private boolean createIssueOnElement( AnnotationHolder holder, Diagnostic<? extends JavaFileObject> issue, PsiElement element )
  {
    if( element.getTextOffset() > issue.getStartPosition() ||
        element.getTextOffset() + element.getTextLength() <= issue.getStartPosition() )
    {
      return false;
    }

    PsiElement deepestElement = element.getContainingFile().findElementAt( (int)issue.getStartPosition() );
    if( deepestElement != element )
    {
      return false;
    }

    String message = makeMessage( issue );
    String tooltip = makeTooltip( message );
    if( hasAnnotation( (SmartList<Annotation>)holder, deepestElement ) )
    {
      //## todo: this is a "temporary" fix since IJ 2018.3, for some reason it calls this annotator twice with the same holder
      return false;
    }
    switch( issue.getKind() )
    {
      case ERROR:
        holder.newAnnotation( HighlightSeverity.ERROR, message )
          .range( deepestElement.getTextRange() )
          .tooltip( tooltip )
          .create();
        break;
      case WARNING:
      case MANDATORY_WARNING:
        holder.newAnnotation( HighlightSeverity.WARNING, message )
          .range( deepestElement.getTextRange() )
          .tooltip( tooltip )
          .create();
        break;
      case NOTE:
      case OTHER:
        holder.newAnnotation( HighlightSeverity.INFORMATION, message )
          .range( deepestElement.getTextRange() )
          .tooltip( tooltip )
          .create();
        break;
    }
    return true;
  }

  private boolean hasAnnotation( SmartList<Annotation> holder, PsiElement deepestElement )
  {
    TextRange textRange = deepestElement.getTextRange();
    for( Annotation annotation: holder )
    {
      if( annotation.getStartOffset() == textRange.getStartOffset() &&
          annotation.getEndOffset() == textRange.getEndOffset() )
      {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private String makeMessage( Diagnostic<? extends JavaFileObject> issue )
  {
    return issue.getMessage( Locale.getDefault() );
  }
  @NotNull
  private String makeTooltip( String message )
  {
    String manIcon = "<img src=\"" + getClass().getResource( "/manifold/ij/icons/manifold.png" ) + "\">";
    return manIcon + "&nbsp;" + message;
  }

  public static PsiClass getContainingClass( PsiElement element )
  {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType( element, PsiClass.class, false );
    if( psiClass == null )
    {
      final PsiFile containingFile = element.getContainingFile();
      if( containingFile instanceof PsiClassOwner )
      {
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if( classes.length == 1 )
        {
          return classes[0];
        }
      }
    }
    return psiClass;
  }
}
