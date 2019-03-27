package com.helger.rabbit.handler.convert;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;

/**
 * An image converter that runs an external program to do the actual conversion.
 */
public class ExternalProcessConverter implements ImageConverter
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ExternalProcessConverter.class);
  private static final String STD_CONVERT = "/usr/bin/gm";
  private static final String STD_CONVERT_ARGS = "convert -quality 10 -flatten $filename +profile \"*\" jpeg:$filename.c";

  private final boolean canConvert;
  private final String convert;
  private final String convertArgs;

  /**
   * Create a new ExternalProcessConverter configured from the given properties.
   *
   * @param props
   *        the configuration for this converter
   */
  public ExternalProcessConverter (final StringMap props)
  {
    convert = props.getOrDefault ("convert", STD_CONVERT);
    convertArgs = props.getOrDefault ("convertargs", STD_CONVERT_ARGS);
    final String conv = props.getOrDefault ("convert", STD_CONVERT);
    final File f = new File (conv);
    if (!f.exists () || !f.isFile ())
    {
      LOGGER.warn ("convert -" + conv + "- not found, is your path correct?");
      canConvert = false;
    }
    else
    {
      canConvert = true;
    }
  }

  public boolean canConvert ()
  {
    return canConvert;
  }

  public void convertImage (final File from, final File to, final String info) throws IOException
  {
    int idx;
    final String entryName = from.getAbsolutePath ();
    String convargs = convertArgs;
    while ((idx = convargs.indexOf ("$filename")) > -1)
    {
      convargs = convargs.substring (0, idx) + entryName + convargs.substring (idx + "$filename".length ());
    }
    final String command = convert + " " + convargs;
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("ImageHandler running: '" + command + "'");
    final Process ps = Runtime.getRuntime ().exec (command);
    try
    {
      ps.waitFor ();
      closeStreams (ps);
      final int exitValue = ps.exitValue ();
      if (exitValue != 0)
      {
        LOGGER.warn ("Bad conversion: " + entryName + ", got exit value: " + exitValue);
        throw new IOException ("failed to convert image, " + "exit value: " + exitValue + ", info: " + info);
      }
    }
    catch (final InterruptedException e)
    {
      LOGGER.warn ("Interupted during wait for: " + entryName);
    }
  }

  /**
   * Close the streams to the external process.
   *
   * @param ps
   *        the Process that did the image conversion
   * @throws IOException
   *         if close fails
   */
  public void closeStreams (final Process ps) throws IOException
  {
    ps.getInputStream ().close ();
    ps.getOutputStream ().close ();
    ps.getErrorStream ().close ();
  }
}
