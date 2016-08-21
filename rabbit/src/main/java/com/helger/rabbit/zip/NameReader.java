package com.helger.rabbit.zip;

import static com.helger.rabbit.zip.GZipFlags.FCOMMENT;
import static com.helger.rabbit.zip.GZipFlags.FHCRC;

/**
 * GZipState for reading the gzip headers file name
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class NameReader implements GZipUnpackState
{
  private final GZipUnpackListener listener;
  private final byte flag;

  public NameReader (final GZipUnpackListener listener, final byte flag)
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
    // TODO: listener.name (someString);

    if ((flag & FCOMMENT) == FCOMMENT)
      useNewState (unpacker, new CommentReader (listener, flag), buf, off, len);
    if ((flag & FHCRC) == FHCRC)
      useNewState (unpacker, new HCRCReader (listener, flag), buf, off, len);
    useNewState (unpacker, new UnCompressor (listener, true), buf, off, len);
  }

  private void useNewState (final GZipUnpacker unpacker,
                            final GZipUnpackState state,
                            final byte [] buf,
                            final int off,
                            final int len)
  {
    unpacker.setState (state);
    state.handleBuffer (unpacker, buf, off, len);
  }
}
