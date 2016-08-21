package com.helger.rabbit.zip;

/**
 * GZipUnpackState after unpacking has been performed.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class AfterEndState implements GZipUnpackState
{
  public void handleBuffer (final GZipUnpacker unpacker, final byte [] buf, final int off, final int len)
  {
    throw new IllegalStateException ("gzip handling is already finished");
  }

  public void handleCurrentData (final GZipUnpacker unpacker)
  {
    throw new IllegalStateException ("gzip handling is already finished");
  }

  public boolean needsInput ()
  {
    return false;
  }
}
