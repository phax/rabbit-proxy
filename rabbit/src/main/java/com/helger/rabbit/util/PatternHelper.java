package com.helger.rabbit.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.helger.commons.url.SMap;

/**
 * Helper class for regular expresions.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class PatternHelper
{

  private final Logger logger = Logger.getLogger (getClass ().getName ());

  /**
   * Get a Pattern for a given property.
   *
   * @param properties
   *        the properties to use.
   * @param configOption
   *        the property to get.
   * @param warn
   *        the warning message to log if construction fails
   * @return a Pattern or null if no pattern could be created.
   */
  public Pattern getPattern (final SMap properties, final String configOption, final String warn)
  {
    Pattern ret = null;
    final String val = properties.get (configOption);
    if (val != null)
    {
      try
      {
        ret = Pattern.compile (val, Pattern.CASE_INSENSITIVE);
      }
      catch (final PatternSyntaxException e)
      {
        logger.log (Level.WARNING, warn, e);
      }
    }
    return ret;
  }
}
