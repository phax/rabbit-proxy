package com.helger.rabbit.proxy;

import com.helger.commons.collection.attr.StringMap;

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

  public void setup (final StringMap props)
  {
    // nothing to do
  }
}
