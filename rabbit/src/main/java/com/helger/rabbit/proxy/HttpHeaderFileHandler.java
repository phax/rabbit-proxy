package com.helger.rabbit.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.helger.rabbit.cache.ncache.IFileHandler;
import com.helger.rabbit.http.HttpHeader;

/**
 * A FileHandler for HttpHeader
 * 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpHeaderFileHandler implements IFileHandler <HttpHeader>
{
  public HttpHeader read (final InputStream is) throws IOException
  {
    final DataInputStream dos = new DataInputStream (is);
    final HttpHeader h = new HttpHeader ();
    h.read (dos);
    return h;
  }

  public void write (final OutputStream os, final HttpHeader t) throws IOException
  {
    final DataOutputStream dos = new DataOutputStream (os);
    t.write (dos);
  }
}
