package com.helger.rabbit.html;

import java.io.IOException;

import com.helger.commons.io.stream.NonBlockingStringWriter;
import com.helger.xml.serialize.write.EXMLCharMode;
import com.helger.xml.serialize.write.EXMLIncorrectCharacterHandling;
import com.helger.xml.serialize.write.EXMLSerializeVersion;
import com.helger.xml.serialize.write.XMLMaskHelper;

/**
 * Escape strings to make them html-safe.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HtmlEscapeUtils
{
  /**
   * Make the gven string html-safe.
   *
   * @param s
   *        the String to escape
   * @return the escaped string
   */
  public static String escapeHtml (final String s)
  {
    try
    {
      final NonBlockingStringWriter aSW = new NonBlockingStringWriter ();
      XMLMaskHelper.maskXMLTextTo (EXMLSerializeVersion.HTML,
                                   EXMLCharMode.TEXT,
                                   EXMLIncorrectCharacterHandling.DEFAULT,
                                   s,
                                   aSW);
      return aSW.getAsString ();
    }
    catch (final IOException ex)
    {
      // Does not happen, as NonBlockingStringWriter does not throw!
      return s;
    }
  }
}
