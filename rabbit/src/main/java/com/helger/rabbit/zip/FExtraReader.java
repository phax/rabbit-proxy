package com.helger.rabbit.zip;

import static com.helger.rabbit.zip.GZipFlags.FCOMMENT;
import static com.helger.rabbit.zip.GZipFlags.FHCRC;
import static com.helger.rabbit.zip.GZipFlags.FNAME;

/**
 * GZipState for reading the gzip flags
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FExtraReader implements GZipUnpackState
{
  private final GZipUnpackListener listener;
  private final byte flag;
  private final byte [] xlen = new byte [2];
  private int pos = 0;
  private int toSkip = -1;

  public FExtraReader (final GZipUnpackListener listener, final byte flag)
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

    if (toSkip == -1)
    {
      while (len > 0 && pos < xlen.length)
      {
        xlen[pos++] = buf[off++];
        len--;
      }

      if (pos <= xlen.length)
        return;

      toSkip = (xlen[0] & 0xff) | ((xlen[1] << 8) & 0xff00);
    }

    // TODO: listener.fextra (fextraData);
    while (toSkip > 0)
    {
      if (len < toSkip)
      {
        toSkip -= len;
        return;
      }

      len -= toSkip;
      off += toSkip;
    }

    if ((flag & FNAME) == FNAME)
      useNewState (unpacker, new NameReader (listener, flag), buf, off, len);

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
