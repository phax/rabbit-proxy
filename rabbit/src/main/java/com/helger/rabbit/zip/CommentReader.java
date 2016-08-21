package com.helger.rabbit.zip;

import static com.helger.rabbit.zip.GZipFlags.FHCRC;

/**
 * GZipState for reading the gzip headers comment
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class CommentReader implements GZipUnpackState
{
  private final GZipUnpackListener listener;
  private final byte flag;

  public CommentReader (final GZipUnpackListener listener, final byte flag)
  {
    this.listener = listener;
    this.flag = flag;
  }

  public void handleCurrentData (final GZipUnpacker unpacker)
  {
    throw new IllegalStateException ("need more input");
  }

  public boolean needsInput ()
  {
    return true;
  }

  public void handleBuffer (final GZipUnpacker unpacker, final byte [] buf, int off, int len)
  {
    if (len <= 0)
      return;

    byte b = -1;
    while (len > 0 && (b = buf[off++]) != 0)
      len--;
    if (b != 0)
      return;
    // TODO: listener.comment (someString);

    if ((flag & FHCRC) == FHCRC)
    {
      final GZipUnpackState crc = new HCRCReader (listener, flag);
      unpacker.setState (crc);
      crc.handleBuffer (unpacker, buf, off, len);
    }
    final GZipUnpackState uc = new UnCompressor (listener, true);
    unpacker.setState (uc);
    uc.handleBuffer (unpacker, buf, off, len);
  }
}
