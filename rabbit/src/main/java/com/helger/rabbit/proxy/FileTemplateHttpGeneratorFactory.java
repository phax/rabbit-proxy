package com.helger.rabbit.proxy;

import java.io.File;
import java.util.logging.Logger;

import com.helger.commons.url.SMap;

/**
 * A HttpGeneratorFactory that creates FileTemplateHttpGenerator instances.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FileTemplateHttpGeneratorFactory implements HttpGeneratorFactory
{
  private File dir;

  public HttpGenerator create (final String identity, final Connection con)
  {
    if (dir != null)
      return new FileTemplateHttpGenerator (identity, con, dir);
    return new StandardResponseHeaders (identity, con);
  }

  public void setup (final SMap props)
  {
    final String templateDir = props.get ("error_pages");
    dir = new File (templateDir);
    if (!dir.exists ())
    {
      dir = null;
      final Logger logger = Logger.getLogger (getClass ().getName ());
      logger.warning ("Failed to find error pages directory: " + templateDir);
    }
  }
}
