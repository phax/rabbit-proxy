package com.helger.rabbit.proxy;

import com.helger.commons.url.SMap;

/**
 * A HttpGeneratorFactory that creates StandardResponseHeaders instances.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class StandardHttpGeneratorFactory implements HttpGeneratorFactory
{
  public HttpGenerator create (final String identity, final Connection con)
  {
    return new StandardResponseHeaders (identity, con);
  }

  public void setup (final SMap props)
  {
    // nothing to do
  }
}
