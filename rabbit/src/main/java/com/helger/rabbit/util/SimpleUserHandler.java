package com.helger.rabbit.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.io.stream.StreamHelper;

/**
 * This is a class that handles users authentication using a simple text file.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleUserHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (SimpleUserHandler.class);

  private String userFile = null;
  private Map <String, String> users = new HashMap <> ();

  /**
   * Set the file to use for users, will read the files. Will discard any
   * previous loaded users.
   *
   * @param userFile
   *        the filename to read the users from.
   */
  public void setFile (final String userFile)
  {
    this.userFile = userFile;

    FileReader fr = null;
    try
    {
      fr = new FileReader (userFile);
      users = loadUsers (fr);
    }
    catch (final FileNotFoundException e)
    {
      LOGGER.warn ("could not load the users file: '" + userFile, e);
    }
    catch (final IOException e)
    {
      LOGGER.warn ("Error while loading the users file: '" + userFile, e);
    }
    finally
    {
      final Closeable c = fr;
      StreamHelper.close (c);
    }
  }

  /**
   * Load the users from the given Reader.
   *
   * @param r
   *        the Reader with the users.
   * @return a Map with usernames and passwords
   * @throws IOException
   *         if reading the users fail
   */
  public Map <String, String> loadUsers (final Reader r) throws IOException
  {
    final BufferedReader br = new BufferedReader (r);
    String line;
    final Map <String, String> u = new HashMap <> ();
    while ((line = br.readLine ()) != null)
    {
      final String [] creds = line.split ("[: \n\t]");
      if (creds.length != 2)
        continue;
      final String name = creds[0];
      final String pass = creds[1];
      u.put (name, pass);
    }
    return u;
  }

  /**
   * Saves the users from the given Reader.
   *
   * @param r
   *        the Reader with the users.
   * @throws IOException
   *         if reading the users fail if writing users fails
   */
  public void saveUsers (final Reader r) throws IOException
  {
    if (userFile == null)
      return;
    final BufferedReader br = new BufferedReader (r);
    try (PrintWriter fw = new PrintWriter (new FileWriter (userFile)))
    {
      String line;
      while ((line = br.readLine ()) != null)
        fw.println (line);
      fw.flush ();
    }
  }

  /**
   * Return the hash of users.
   *
   * @return the Map of usernames to passwords
   */
  public Map <String, String> getUsers ()
  {
    return users;
  }

  /**
   * Set the usernames and passwords to use for authentication.
   *
   * @param users
   *        the new set of usernames and passwords
   */
  public void setUsers (final Map <String, String> users)
  {
    this.users = users;
  }

  /**
   * Check if a user/password combination is valid.
   *
   * @param username
   *        the username.
   * @param password
   *        the decrypted password.
   * @return true if both username and password match a valid user.
   */
  public boolean isValidUser (final String username, final String password)
  {
    if (username == null)
      return false;
    final String pass = users.get (username);
    return pass != null && password != null && pass.equals (password);
  }
}
