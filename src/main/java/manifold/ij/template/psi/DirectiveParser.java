package manifold.ij.template.psi;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.ReferenceParser;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;


import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.done;
import static com.intellij.lang.java.parser.JavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.expectOrError;

public class DirectiveParser
{
  private static final DirectiveParser INSTANCE = new DirectiveParser();

  public static final String PARAMS = "params";
  public static final String EXTENDS = "extends";
  public static final String IMPORT = "import";
  public static final String INCLUDE = "include";
  public static final String SECTION = "section";
  public static final String END = "end";

  private static final TokenSet TYPE_START = TokenSet.orSet(
    ElementType.PRIMITIVE_TYPE_BIT_SET, TokenSet.create( JavaTokenType.IDENTIFIER ) );

  private final JavaParser _javaParser;

  public static DirectiveParser instance()
  {
    return INSTANCE;
  }

  private DirectiveParser()
  {
    _javaParser = new JavaParser();
  }

  public void parse( PsiBuilder builder )
  {
    builder.setDebugMode(true);
    
    String directiveName = builder.getTokenText();
    if( (builder.getTokenType() != JavaTokenType.IDENTIFIER &&
         builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD &&
         builder.getTokenType() != JavaTokenType.EXTENDS_KEYWORD)
        || directiveName == null )
    {
      builder.error( JavaErrorMessages.message( "expected.identifier" ) );
      return;
    }

    switch( directiveName )
    {
      case PARAMS:
        parseParamsDirective( builder );
        break;

      case EXTENDS:
        parseExtendsDirective( builder );
        break;

      case IMPORT:
        parseImportDirective( builder );
        break;

      case INCLUDE:
        parseIncludeDirective( builder );
        break;

      case SECTION:
        parseSectionDirective( builder );
        break;

      case END:
        parseEndDirective( builder );
        break;

      default:
        builder.error( "Unknown directive: '" + directiveName + "'" );
    }
  }

  private void parseSectionDirective( PsiBuilder builder )
  {
    builder.advanceLexer();

    expectOrError( builder, JavaTokenType.IDENTIFIER, "expected.identifier" );
    expectOrError( builder, JavaTokenType.LPARENTH, "expected.lparen" );
    parseExpressionList( builder );
    expectOrError( builder, JavaTokenType.RPARENTH, "expected.rparen" );
  }
  private void parseEndDirective( PsiBuilder builder )
  {
    builder.advanceLexer();

    final IElementType tokenType = builder.getTokenType();
    if( tokenType == null )
    {
      return;
    }

    String directiveName = builder.getTokenText();
    if( tokenType != JavaTokenType.IDENTIFIER )
    {
      builder.error( JavaErrorMessages.message( "expected.identifier" ) );
    }
    else if( directiveName == null || !directiveName.equals( SECTION ) )
    {
      builder.error( "Expecting 'section' keyword" );
      builder.advanceLexer();
    }
  }

  private void parseIncludeDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
    ReferenceParser.TypeInfo type = parseType( builder );
    if( type == null )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
    }
  }

  private void parseImportDirective( PsiBuilder builder )
  {
    PsiBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    if( !_javaParser.getReferenceParser().parseImportCodeReference( builder, false ) )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
      importStatement.drop();
    }
    else
    {
      done( importStatement, JavaElementType.IMPORT_STATEMENT );
    }
  }

  private void parseExtendsDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
    ReferenceParser.TypeInfo type = parseType( builder );
    if( type == null )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
    }
  }

  private void parseParamsDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
    expectOrError( builder, JavaTokenType.LPARENTH, "expected.lparen" );
    parseExpressionList( builder );
    expectOrError( builder, JavaTokenType.RPARENTH, "expected.rparen" );
  }

  private void parseExpressionList( PsiBuilder builder )
  {
    parseParam( builder );
    while( builder.getTokenType() == JavaTokenType.COMMA )
    {
      builder.advanceLexer();
      parseParam( builder );
    }
  }

  private void parseParam( PsiBuilder builder )
  {
    final IElementType tokenType = builder.getTokenType();
    if( tokenType == null )
    {
      return;
    }

    PsiBuilder.Marker declStatement = builder.mark();
    PsiBuilder.Marker localVariableDecl = builder.mark();

    Pair<PsiBuilder.Marker, Boolean> modListInfo = _javaParser.getDeclarationParser().parseModifierList( builder );
    PsiBuilder.Marker modList = modListInfo.first;

    ReferenceParser.TypeInfo type = parseType( builder );
    if( type == null )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
      localVariableDecl.drop();
      declStatement.drop();
      return;
    }

    if( !expect( builder, JavaTokenType.IDENTIFIER ) )
    {
      if( Boolean.FALSE.equals( modListInfo.second ) ||
          (type.isPrimitive && builder.getTokenType() != JavaTokenType.DOT) )
      {
        builder.error( JavaErrorMessages.message( "expected.identifier" ) );
        localVariableDecl.drop();
        declStatement.drop();
        return;
      }
      else
      {
        localVariableDecl.drop();
        declStatement.drop();
        return;
      }
    }

    eatBrackets( builder, null );

    done( localVariableDecl, JavaElementType.LOCAL_VARIABLE );
    done( declStatement, JavaElementType.DECLARATION_STATEMENT );
  }

  @Nullable
  private ReferenceParser.TypeInfo parseType( PsiBuilder builder )
  {
    ReferenceParser.TypeInfo type = null;
    if( TYPE_START.contains( builder.getTokenType() ) )
    {
      PsiBuilder.Marker pos = builder.mark();

      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD;
      flags |= ReferenceParser.VAR_TYPE;

      type = _javaParser.getReferenceParser().parseTypeInfo( builder, flags );

      if( type == null )
      {
        pos.rollbackTo();
      }
      else
      {
        pos.drop();
      }
    }

    return type;
  }

  private boolean eatBrackets( PsiBuilder builder, @Nullable @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String errorKey )
  {
    IElementType tokenType = builder.getTokenType();
    if( tokenType != JavaTokenType.LBRACKET && tokenType != JavaTokenType.AT )
    {
      return true;
    }

    PsiBuilder.Marker marker = builder.mark();

    int count = 0;
    while( true )
    {
      //parseAnnotations( builder );
      if( !expect( builder, JavaTokenType.LBRACKET ) )
      {
        break;
      }
      ++count;
      if( !expect( builder, JavaTokenType.RBRACKET ) )
      {
        break;
      }
      ++count;
    }

    if( count == 0 )
    {
      // just annotation, most probably belongs to a next declaration
      marker.rollbackTo();
      return true;
    }

    if( errorKey != null )
    {
      marker.error( JavaErrorMessages.message( errorKey ) );
    }
    else
    {
      marker.drop();
    }

    boolean paired = count % 2 == 0;
    if( !paired )
    {
      error( builder, JavaErrorMessages.message( "expected.rbracket" ) );
    }
    return paired;
  }

}
