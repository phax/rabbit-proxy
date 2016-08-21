/**
 * Copyright (c) 2010 Robert Olofsson.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the authors nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHORS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.helger.rnio.impl;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;

import com.helger.rnio.AcceptHandler;
import com.helger.rnio.ConnectHandler;
import com.helger.rnio.ReadHandler;
import com.helger.rnio.SocketChannelHandler;
import com.helger.rnio.WriteHandler;

/**
 * The handler of channel operations.
 */
class ChannelOpsHandler
{
  private static class NullHandler implements ReadHandler, WriteHandler, AcceptHandler, ConnectHandler
  {
    public void closed ()
    { /* empty */ }

    public void timeout ()
    { /* empty */ }

    public boolean useSeparateThread ()
    {
      return false;
    }

    public String getDescription ()
    {
      return "NullHandler";
    }

    public Long getTimeout ()
    {
      return null;
    }

    public void read ()
    { /* empty */ }

    public void write ()
    { /* empty */ }

    public void accept ()
    { /* empty */ }

    public void connect ()
    { /* empty */ }

    @Override
    public String toString ()
    {
      return "NullHandler";
    }
  }

  private static final NullHandler NULL_HANDLER = new NullHandler ();

  private ReadHandler readHandler = NULL_HANDLER;
  private WriteHandler writeHandler = NULL_HANDLER;
  private AcceptHandler acceptHandler = NULL_HANDLER;
  private ConnectHandler connectHandler = NULL_HANDLER;

  @Override
  public String toString ()
  {
    return getClass ().getSimpleName () +
           "{" +
           "r: " +
           readHandler +
           ", w: " +
           writeHandler +
           ", a: " +
           acceptHandler +
           ", c: " +
           connectHandler +
           "}";
  }

  public int getInterestOps ()
  {
    int ret = 0;
    if (readHandler != NULL_HANDLER)
      ret |= SelectionKey.OP_READ;
    if (writeHandler != NULL_HANDLER)
      ret |= SelectionKey.OP_WRITE;
    if (acceptHandler != NULL_HANDLER)
      ret |= SelectionKey.OP_ACCEPT;
    if (connectHandler != NULL_HANDLER)
      ret |= SelectionKey.OP_CONNECT;
    return ret;
  }

  private void checkNullHandler (final SocketChannelHandler handler,
                                 final SocketChannelHandler newHandler,
                                 final String type)
  {
    if (handler != NULL_HANDLER)
    {
      final String msg = "Trying to overwrite the existing " +
                         type +
                         ": " +
                         handler +
                         ", new " +
                         type +
                         ": " +
                         newHandler +
                         ", coh: " +
                         this;
      throw new IllegalStateException (msg);
    }
  }

  public void setReadHandler (final ReadHandler rh)
  {
    if (rh == null)
      throw new IllegalArgumentException ("read handler may not be null");
    checkNullHandler (this.readHandler, rh, "readHandler");
    this.readHandler = rh;
  }

  public void setWriteHandler (final WriteHandler writeHandler)
  {
    if (writeHandler == null)
      throw new IllegalArgumentException ("write handler may not be null");
    checkNullHandler (this.writeHandler, writeHandler, "writeHandler");
    this.writeHandler = writeHandler;
  }

  public void setAcceptHandler (final AcceptHandler acceptHandler)
  {
    if (acceptHandler == null)
      throw new IllegalArgumentException ("accept handler may not be null");
    checkNullHandler (this.acceptHandler, acceptHandler, "acceptHandler");
    this.acceptHandler = acceptHandler;
  }

  public void setConnectHandler (final ConnectHandler connectHandler)
  {
    if (connectHandler == null)
      throw new IllegalArgumentException ("connect handler may not be null");
    checkNullHandler (this.connectHandler, connectHandler, "connectHandler");
    this.connectHandler = connectHandler;
  }

  private void handleRead (final ExecutorService executorService, final ReadHandler rh)
  {
    if (rh.useSeparateThread ())
    {
      executorService.execute ( () -> rh.read ());
    }
    else
    {
      rh.read ();
    }
  }

  private void handleWrite (final ExecutorService executorService, final WriteHandler wh)
  {
    if (wh.useSeparateThread ())
    {
      executorService.execute ( () -> wh.write ());
    }
    else
    {
      wh.write ();
    }
  }

  private void handleAccept (final ExecutorService executorService, final AcceptHandler ah)
  {
    if (ah.useSeparateThread ())
    {
      executorService.execute ( () -> ah.accept ());
    }
    else
    {
      ah.accept ();
    }
  }

  private void handleConnect (final ExecutorService executorService, final ConnectHandler ch)
  {
    if (ch.useSeparateThread ())
    {
      executorService.execute ( () -> ch.connect ());
    }
    else
    {
      ch.connect ();
    }
  }

  public void handle (final ExecutorService executorService, final SelectionKey sk)
  {
    sk.interestOps (0);
    final ReadHandler rh = readHandler;
    final WriteHandler wh = writeHandler;
    final AcceptHandler ah = acceptHandler;
    final ConnectHandler ch = connectHandler;
    readHandler = NULL_HANDLER;
    writeHandler = NULL_HANDLER;
    acceptHandler = NULL_HANDLER;
    connectHandler = NULL_HANDLER;

    if (sk.isReadable ())
      handleRead (executorService, rh);
    else
      if (rh != NULL_HANDLER)
        setReadHandler (rh);

    if (sk.isValid () && sk.isWritable ())
      handleWrite (executorService, wh);
    else
      if (wh != NULL_HANDLER)
        setWriteHandler (wh);

    if (sk.isValid () && sk.isAcceptable ())
      handleAccept (executorService, ah);
    else
      if (ah != NULL_HANDLER)
        setAcceptHandler (ah);

    if (sk.isValid () && sk.isConnectable ())
      handleConnect (executorService, ch);
    else
      if (ch != NULL_HANDLER)
        setConnectHandler (ch);
  }

  private boolean doTimeout (final long now, final SocketChannelHandler sch)
  {
    if (sch == null)
      return false;
    final Long t = sch.getTimeout ();
    if (t == null)
      return false;
    final boolean ret = t.longValue () < now;
    if (ret)
      sch.timeout ();
    return ret;
  }

  public boolean doTimeouts (final long now)
  {
    boolean ret = false;
    if (ret |= doTimeout (now, readHandler))
      readHandler = NULL_HANDLER;
    if (ret |= doTimeout (now, writeHandler))
      writeHandler = NULL_HANDLER;
    if (ret |= doTimeout (now, acceptHandler))
      acceptHandler = NULL_HANDLER;
    if (ret |= doTimeout (now, connectHandler))
      connectHandler = NULL_HANDLER;
    return ret;
  }

  private Long minTimeout (final Long t, final SocketChannelHandler sch)
  {
    if (sch == null)
      return t;
    final Long t2 = sch.getTimeout ();
    if (t == null)
      return t2;
    if (t2 == null)
      return t;
    return t2.longValue () < t.longValue () ? t2 : t;
  }

  public Long getMinimumTimeout ()
  {
    Long t = readHandler.getTimeout ();
    t = minTimeout (t, writeHandler);
    t = minTimeout (t, acceptHandler);
    t = minTimeout (t, connectHandler);
    return t;
  }

  public void cancel (final SocketChannelHandler sch)
  {
    if (readHandler == sch)
      readHandler = NULL_HANDLER;
    if (writeHandler == sch)
      writeHandler = NULL_HANDLER;
    if (acceptHandler == sch)
      acceptHandler = NULL_HANDLER;
    if (connectHandler == sch)
      connectHandler = NULL_HANDLER;
  }

  private void closedIfSet (final SocketChannelHandler sch)
  {
    if (sch != null)
      sch.closed ();
  }

  public void closed ()
  {
    closedIfSet (readHandler);
    closedIfSet (writeHandler);
    closedIfSet (acceptHandler);
    closedIfSet (connectHandler);
  }
}
