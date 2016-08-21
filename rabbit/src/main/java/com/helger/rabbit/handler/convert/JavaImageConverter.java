package com.helger.rabbit.handler.convert;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.helger.commons.url.SMap;

/**
 * An image converter that uses javax.image to convert images
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class JavaImageConverter implements ImageConverter
{
  private static final String STD_QUALITY = "0.1";
  private final float quality;
  private final long maxImageSize;

  /**
   * Create a new JavaImageConverter using the given properties.
   *
   * @param props
   *        the properties hodling the configuration
   */
  public JavaImageConverter (final SMap props)
  {
    final Runtime rt = Runtime.getRuntime ();
    final long max = rt.maxMemory ();
    maxImageSize = max / 4;
    final String sq = props.getOrDefault ("quality", STD_QUALITY);
    quality = Float.parseFloat (sq);
  }

  public boolean canConvert ()
  {
    return true;
  }

  public void convertImage (final File input, final File output, final String info) throws IOException
  {
    BufferedImage origImage = getImage (input, info);
    if (origImage == null)
    {
      return;
    }
    try
    {
      if (origImage.getType () == BufferedImage.TYPE_CUSTOM || origImage.getTransparency () != Transparency.OPAQUE)
        origImage = getRGBImage (origImage);
      final ImageWriter writer = getImageWriter ();
      try
      {
        final ImageOutputStream ios = ImageIO.createImageOutputStream (output);
        try
        {
          writer.setOutput (ios);
          final IIOImage iioimage = new IIOImage (origImage, null, null);
          writer.write (null, iioimage, getParams ());
        }
        finally
        {
          ios.close ();
        }
      }
      finally
      {
        writer.dispose ();
      }
    }
    finally
    {
      origImage.flush ();
    }
  }

  private BufferedImage getImage (final File input, final String info) throws IOException
  {
    final ImageInputStream iis = ImageIO.createImageInputStream (input);
    try
    {
      final Iterator <ImageReader> readers = ImageIO.getImageReaders (iis);
      if (!readers.hasNext ())
        throw new IOException ("Failed to find image reader: " + info);

      final ImageReader ir = readers.next ();
      try
      {
        return getImage (ir, iis, info);
      }
      finally
      {
        ir.dispose ();
      }
    }
    finally
    {
      iis.close ();
    }
  }

  private BufferedImage getImage (final ImageReader ir,
                                  final ImageInputStream iis,
                                  final String info) throws IOException
  {
    ir.setInput (iis);
    // 4 bytes per pixels, we may need 2 images
    final long size = ir.getWidth (0) * ir.getHeight (0) * 4 * 2;
    if (size > maxImageSize)
      throw new IOException ("Image is too large, wont' convert: " + info);
    return ir.read (0);
  }

  private ImageWriter getImageWriter () throws IOException
  {
    final Iterator <ImageWriter> iter = ImageIO.getImageWritersByFormatName ("jpeg");
    if (iter.hasNext ())
      return iter.next ();
    throw new IOException ("Failed to find jpeg writer");
  }

  private JPEGImageWriteParam getParams ()
  {
    final JPEGImageWriteParam iwparam = new JPEGImageWriteParam (Locale.getDefault ());
    iwparam.setCompressionMode (ImageWriteParam.MODE_EXPLICIT);
    iwparam.setCompressionQuality (quality);
    return iwparam;
  }

  private BufferedImage getRGBImage (final BufferedImage orig)
  {
    // Image without alpha channel since jpeg has no alpha
    final BufferedImage newImage = new BufferedImage (orig.getWidth (),
                                                      orig.getHeight (),
                                                      BufferedImage.TYPE_3BYTE_BGR);
    try
    {
      final Graphics g2 = newImage.getGraphics ();
      try
      {
        g2.setColor (Color.WHITE);
        g2.fillRect (0, 0, orig.getWidth (), orig.getHeight ());
        g2.drawImage (orig, 0, 0, null);
      }
      finally
      {
        g2.dispose ();
      }
    }
    finally
    {
      orig.flush ();
    }
    return newImage;
  }
}
