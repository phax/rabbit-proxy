package com.helger.rabbit.proxy;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;

/**
 * A HttpGeneratorFactory that creates FileTemplateHttpGenerator instances.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FileTemplateHttpGeneratorFactory implements HttpGeneratorFactory
{
  private static final Logger LOGGER = LoggerFactory.getLogger (FileTemplateHttpGeneratorFactory.class);

  private File dir;

  public HttpGenerator create (final String identity, final Connection con)
  {
    if (dir != null)
      return new FileTemplateHttpGenerator (identity, con, dir);
    return new StandardResponseHeaders (identity, con);
  }

  public void setup (final StringMap props)
  {
    final String templateDir = props.get ("error_pages");
    dir = new File (templateDir);
    if (!dir.exists ())
    {
      dir = null;
      LOGGER.warn ("Failed to find error pages directory: " + templateDir);
    }
  }
}
