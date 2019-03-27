package com.helger.rabbit.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;

/**
 * Helper class for regular expressions.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class PatternHelper
{
  private static final Logger LOGGER = LoggerFactory.getLogger (PatternHelper.class);

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
  public Pattern getPattern (final StringMap properties, final String configOption, final String warn)
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
        LOGGER.warn (warn, e);
      }
    }
    return ret;
  }
}
