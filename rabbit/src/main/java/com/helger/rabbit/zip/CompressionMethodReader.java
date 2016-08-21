package com.helger.rabbit.zip;

/**
 * GZipState for validating the compression method.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class CompressionMethodReader implements GZipUnpackState
{
  private final GZipUnpackListener listener;
  private static final int GZIP_DEFLATE = 8;

  public CompressionMethodReader (final GZipUnpackListener listener)
  {
    this.listener = listener;
  }

  public void handleCurrentData (final GZipUnpacker unpacker)
  {
    throw new IllegalStateException ("need more input");
  }

  public boolean needsInput ()
  {
    return true;
  }

  public void handleBuffer (final GZipUnpacker unpacker, final byte [] buf, final int off, final int len)
  {
    if (len <= 0)
      return;
    final byte b = buf[off];
    if (b != GZIP_DEFLATE)
    {
      final String err = "unknown compression method: " + b;
      final Exception e = new IllegalArgumentException (err);
      listener.failed (e);
    }
    final GZipUnpackState fr = new FlagReader (listener);
    unpacker.setState (fr);
    fr.handleBuffer (unpacker, buf, off + 1, len - 1);
  }
}
