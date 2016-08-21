package com.helger.rabbit.proxy;

import static com.helger.rabbit.http.StatusCode._400;
import static com.helger.rabbit.http.StatusCode._401;
import static com.helger.rabbit.http.StatusCode._403;
import static com.helger.rabbit.http.StatusCode._404;
import static com.helger.rabbit.http.StatusCode._407;
import static com.helger.rabbit.http.StatusCode._412;
import static com.helger.rabbit.http.StatusCode._414;
import static com.helger.rabbit.http.StatusCode._416;
import static com.helger.rabbit.http.StatusCode._417;
import static com.helger.rabbit.http.StatusCode._500;
import static com.helger.rabbit.http.StatusCode._504;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.commons.lang.StackTraceHelper;
import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.HtmlEscapeUtils;
import com.helger.rabbit.html.HtmlParseException;
import com.helger.rabbit.html.HtmlParser;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.TagType;
import com.helger.rabbit.html.Token;
import com.helger.rabbit.html.TokenType;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.http.HttpHeaderWithContent;
import com.helger.rabbit.http.StatusCode;
import com.helger.rnio.impl.Closer;

/**
 * A HttpGenerator that creates error pages from file templates.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FileTemplateHttpGenerator extends StandardResponseHeaders
{

  private final File templateDir;
  private final Logger logger = Logger.getLogger (getClass ().getName ());

  public FileTemplateHttpGenerator (final String identity, final Connection con, final File templateDir)
  {
    super (identity, con);
    this.templateDir = templateDir;
  }

  private File getFile (final StatusCode sc)
  {
    return new File (templateDir, Integer.toString (sc.getCode ()));
  }

  private boolean hasFile (final StatusCode sc)
  {
    return getFile (sc).exists ();
  }

  private boolean match (final Token t, final TagType type)
  {
    if (t.getType () == TokenType.TAG)
      return t.getTag ().getTagType () == type;
    return false;
  }

  private void replaceValue (final Tag tag, final String attribute, final String match, final String replacer)
  {
    String attr = tag.getAttribute (attribute);
    if (attr != null)
    {
      boolean found = false;
      int idx;
      // only expect to find zero or one
      while ((idx = attr.indexOf (match)) > -1)
      {
        found = true;
        attr = attr.substring (0, idx) + replacer + attr.substring (idx + match.length ());
      }
      if (found)
        tag.setAttribute (attribute, attr);
    }
  }

  private void replaceLinks (final HtmlBlock block, final String match, final String replacer)
  {
    for (final Token t : block.getTokens ())
    {
      if (match (t, TagType.A))
        replaceValue (t.getTag (), "href", match, replacer);
      else
        if (match (t, TagType.IMG))
          replaceValue (t.getTag (), "src", match, replacer);
    }
  }

  private boolean isTagOfType (final Token token, final String type)
  {
    if (token.getType () == TokenType.TAG)
    {
      final Tag tag = token.getTag ();
      if (tag.getLowerCaseType ().equals (type))
        return true;
    }
    return false;
  }

  private void replaceTemplate (final HtmlBlock block, final String tagType, final String text)
  {
    for (final Token t : block.getTokens ())
      if (isTagOfType (t, tagType))
        t.setText (text);
  }

  private void replacePlaces (final HtmlBlock block, final String tag, final URL url)
  {
    for (final Token t : block.getTokens ())
      if (isTagOfType (t, tag))
        t.setText (getPlaces (url).toString ());
  }

  private void replaceStackTrace (final HtmlBlock block, final String tag, final Throwable thrown)
  {
    for (final Token t : block.getTokens ())
      if (isTagOfType (t, tag))
        t.setText (StackTraceHelper.getStackAsString (thrown));
  }

  private void replace (final HtmlBlock block, final String tag, final String value)
  {
    if (value != null)
      replaceTemplate (block, tag, HtmlEscapeUtils.escapeHtml (value));
  }

  private void replaceTemplates (final HtmlBlock block, final TemplateData td) throws IOException
  {
    replace (block, "%url%", td.url);
    if (td.thrown != null)
    {
      replace (block, "%exception%", td.thrown.toString ());
      replaceStackTrace (block, "%stacktrace%", td.thrown);
    }
    replace (block, "%filename%", td.file);
    replace (block, "%expectation%", td.expectation);
    replace (block, "%realm%", td.realm);
    if (td.url != null)
      replacePlaces (block, "%places%", new URL (td.url));

    final HttpProxy proxy = getProxy ();
    final String sproxy = proxy.getHost ().getHostName () + ":" + proxy.getPort ();
    replaceLinks (block, "$proxy", sproxy);
  }

  private static class TemplateData
  {
    private final String url;
    private final Throwable thrown;
    private final String file;
    private final String expectation;
    private final String realm;
    private final String realmType;

    public TemplateData (final String url,
                         final Throwable thrown,
                         final String file,
                         final String expectation,
                         final String realm,
                         final String realmType)
    {
      this.url = url;
      this.thrown = thrown;
      this.file = file;
      this.expectation = expectation;
      this.realm = realm;
      this.realmType = realmType;
    }
  }

  public TemplateData getTemplateData ()
  {
    return new TemplateData (getConnection ().getRequestURI (), null, null, null, null, null);
  }

  public TemplateData getTemplateData (final Throwable thrown)
  {
    return new TemplateData (getConnection ().getRequestURI (), thrown, null, null, null, null);
  }

  public TemplateData getTemplateData (final URL url)
  {
    return new TemplateData (url.toString (), null, null, null, null, null);
  }

  public TemplateData getExpectionationData (final String expectation)
  {
    return new TemplateData (getConnection ().getRequestURI (), null, null, expectation, null, null);
  }

  public TemplateData getURLExceptionData (final String url, final Throwable thrown)
  {
    return new TemplateData (url, thrown, null, null, null, null);
  }

  public TemplateData getURLRealmData (final URL url, final String realm, final String realmType)
  {
    return new TemplateData (url.toString (), null, null, null, realm, realmType);
  }

  private HttpHeader getTemplated (final StatusCode sc, final TemplateData td)
  {
    final HttpHeaderWithContent ret = getHeader (sc);
    if (td.realm != null)
      ret.setHeader (td.realmType + "-Authenticate", "Basic realm=\"" + td.realm + "\"");
    final File f = getFile (sc);
    try
    {
      final FileInputStream fis = new FileInputStream (f);
      try
      {
        final byte [] buf = new byte [(int) f.length ()];
        final DataInputStream dis = new DataInputStream (fis);
        try
        {
          dis.readFully (buf);
          final Charset utf8 = Charset.forName ("UTF-8");
          final HtmlParser parser = new HtmlParser (utf8);
          parser.setText (buf);
          final HtmlBlock block = parser.parse ();
          replaceTemplates (block, td);
          ret.setContent (block.toString (), "UTF-8");
        }
        finally
        {
          Closer.close (dis, logger);
        }
      }
      finally
      {
        Closer.close (fis, logger);
      }
    }
    catch (final HtmlParseException e)
    {
      logger.log (Level.WARNING, "Failed to read template", e);
    }
    catch (final IOException e)
    {
      logger.log (Level.WARNING, "Failed to read template", e);
    }
    return ret;
  }

  @Override
  public HttpHeader get400 (final Exception exception)
  {
    if (hasFile (_400))
      return getTemplated (_400, getTemplateData (exception));
    return super.get400 (exception);
  }

  @Override
  public HttpHeader get401 (final URL url, final String realm)
  {
    if (hasFile (_401))
      return getTemplated (_401, getURLRealmData (url, realm, "WWW"));
    return super.get401 (url, realm);
  }

  @Override
  public HttpHeader get403 ()
  {
    if (hasFile (_403))
      return getTemplated (_403, getTemplateData ());
    return super.get403 ();
  }

  @Override
  public HttpHeader get404 (final String file)
  {
    if (hasFile (_404))
      return getTemplated (_404, getTemplateData ());
    return super.get404 (file);
  }

  @Override
  public HttpHeader get407 (final URL url, final String realm)
  {
    if (hasFile (_407))
      return getTemplated (_407, getURLRealmData (url, realm, "Proxy"));
    return super.get407 (url, realm);
  }

  @Override
  public HttpHeader get412 ()
  {
    if (hasFile (_412))
      return getTemplated (_412, getTemplateData ());
    return super.get412 ();
  }

  @Override
  public HttpHeader get414 ()
  {
    if (hasFile (_414))
      return getTemplated (_414, getTemplateData ());
    return super.get414 ();
  }

  @Override
  public HttpHeader get416 (final Throwable exception)
  {
    if (hasFile (_416))
      return getTemplated (_416, getTemplateData (exception));
    return super.get416 (exception);
  }

  @Override
  public HttpHeader get417 (final String expectation)
  {
    if (hasFile (_417))
      return getTemplated (_417, getExpectionationData (expectation));
    return super.get417 (expectation);
  }

  @Override
  public HttpHeader get500 (final String url, final Throwable exception)
  {
    if (hasFile (_500))
      return getTemplated (_500, getURLExceptionData (url, exception));
    return super.get500 (url, exception);
  }

  @Override
  public HttpHeader get504 (final String url, final Throwable exception)
  {
    if (hasFile (_504))
      return getTemplated (_504, getURLExceptionData (url, exception));
    return super.get504 (url, exception);
  }
}
