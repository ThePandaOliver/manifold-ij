package manifold.ij.ext;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class SelfTypeSimpleTest extends AbstractManifoldCodeInsightTest
{
  public void testPositiveUsage()
  {
    myFixture.configureByFile( "ext/self/ExerciseSelf.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    assertEmpty( highlightInfos );
  }

  public void testErrorHighlights()
  {
    myFixture.configureByFile( "ext/self/ExerciseSelfWithError.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    assertEquals( 16, highlightInfos.size() );
  }
}
