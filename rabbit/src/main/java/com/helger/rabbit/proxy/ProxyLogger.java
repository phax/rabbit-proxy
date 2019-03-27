package com.helger.rabbit.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;

/**
 * A class to handle proxy logging.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyLogger implements ConnectionLogger
{

  /** Output for accesses */
  private Logger accessLog;

  /** The format we write dates on. */
  private final SimpleDateFormat sdf = new SimpleDateFormat ("dd/MMM/yyyy:HH:mm:ss 'GMT'");

  /** The monitor for sdf. */
  private final Object sdfMonitor = new Object ();

  /** The distance to GMT in milis. */
  private final long offset;

  /** Create a new ProxyLogger. */
  public ProxyLogger ()
  {
    final TimeZone tz = sdf.getTimeZone ();
    final GregorianCalendar gc = new GregorianCalendar ();
    gc.setTime (new Date ());
    offset = tz.getOffset (gc.get (Calendar.ERA),
                           gc.get (Calendar.YEAR),
                           gc.get (Calendar.MONTH),
                           gc.get (Calendar.DAY_OF_MONTH),
                           gc.get (Calendar.DAY_OF_WEEK),
                           gc.get (Calendar.MILLISECOND));
  }

  /**
   * Get the distance to GMT in millis.
   *
   * @return the time offset
   */
  public long getOffset ()
  {
    return offset;
  }

  /**
   * Configure this LOGGER from the given properties
   *
   * @param config
   *        the properties to use for configuration.
   * @throws IOException
   *         if logging setup fails
   */
  public void setup (final StringMap config) throws IOException
  {
    accessLog = LoggerFactory.getLogger ("access");
  }

  public void logConnection (final Connection con)
  {
    if (accessLog == null)
      return;

    final StringBuilder sb = new StringBuilder ();
    final Socket s = con.getChannel ().socket ();
    if (s != null)
    {
      final InetAddress ia = s.getInetAddress ();
      if (ia != null)
        sb.append (ia.getHostAddress ());
      else
        sb.append ("????");
    }
    sb.append (" - ");
    sb.append ((con.getUserName () != null ? con.getUserName () : "-"));
    sb.append (" ");
    final long now = System.currentTimeMillis ();
    final Date d = new Date (now - offset);
    synchronized (sdfMonitor)
    {
      sb.append (sdf.format (d));
    }
    sb.append (" \"");
    sb.append (con.getRequestLine ());
    sb.append ("\" ");
    sb.append (con.getStatusCode ());
    sb.append (" ");
    sb.append (con.getContentLength ());
    sb.append (" ");
    sb.append (con.getId ().toString ());
    sb.append (" ");
    sb.append ((con.getExtraInfo () != null ? con.getExtraInfo () : ""));

    accessLog.info (sb.toString ());
  }
}
