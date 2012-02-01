/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.security.auth.database;

import org.apache.log4j.Logger;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.UsernamePasswordInitialiser;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public abstract class AbstractPasswordFilePrincipalDatabase<U extends PasswordPrincipal> implements PrincipalDatabase
{
    private final Pattern _regexp = Pattern.compile(":");

    private final Map<String, AuthenticationProviderInitialiser> _saslServers =
            new HashMap<String, AuthenticationProviderInitialiser>();

    protected static final String DEFAULT_ENCODING = "utf-8";
    private final Map<String, U> _userMap = new HashMap<String, U>();
    private final ReentrantLock _userUpdate = new ReentrantLock();
    private final Random _random = new Random();
    private File _passwordFile;


    protected AbstractPasswordFilePrincipalDatabase(UsernamePasswordInitialiser... initialisers)
    {
        for(UsernamePasswordInitialiser initialiser : initialisers)
        {
            initialiser.initialise(this);
            _saslServers.put(initialiser.getMechanismName(), initialiser);
        }
    }

    public final void setPasswordFile(String passwordFile) throws IOException
    {
        File f = new File(passwordFile);
        getLogger().info("PasswordFile using file " + f.getAbsolutePath());
        _passwordFile = f;
        if (!f.exists())
        {
            throw new FileNotFoundException("Cannot find password file " + f);
        }
        if (!f.canRead())
        {
            throw new FileNotFoundException("Cannot read password file " + f +
                                            ". Check permissions.");
        }

        loadPasswordFile();
    }

    /**
     * SASL Callback Mechanism - sets the Password in the PasswordCallback based on the value in the PasswordFile
     * If you want to change the password for a user, use updatePassword instead.
     *
     * @param principal The Principal to set the password for
     * @param callback  The PasswordCallback to call setPassword on
     *
     * @throws javax.security.auth.login.AccountNotFoundException If the Principal cannot be found in this Database
     */
    public final void setPassword(Principal principal, PasswordCallback callback) throws AccountNotFoundException
    {
        if (_passwordFile == null)
        {
            throw new AccountNotFoundException("Unable to locate principal since no password file was specified during initialisation");
        }
        if (principal == null)
        {
            throw new IllegalArgumentException("principal must not be null");
        }
        char[] pwd = lookupPassword(principal.getName());

        if (pwd != null)
        {
            callback.setPassword(pwd);
        }
        else
        {
            throw new AccountNotFoundException("No account found for principal " + principal);
        }
    }


    /**
     * Looks up the password for a specified user in the password file. Note this code is <b>not</b> secure since it
     * creates strings of passwords. It should be modified to create only char arrays which get nulled out.
     *
     * @param name The principal name to lookup
     *
     * @return a char[] for use in SASL.
     */
    protected final char[] lookupPassword(String name)
    {
        U user = _userMap.get(name);
        if (user == null)
        {
            return null;
        }
        else
        {
            return user.getPassword();
        }
    }

    protected boolean compareCharArray(char[] a, char[] b)
    {
        boolean equal = false;
        if (a.length == b.length)
        {
            equal = true;
            int index = 0;
            while (equal && index < a.length)
            {
                equal = a[index] == b[index];
                index++;
            }
        }
        return equal;
    }

    /**
     * Changes the password for the specified user
     *
     * @param principal to change the password for
     * @param password plaintext password to set the password too
     */
    public boolean updatePassword(Principal principal, char[] password) throws AccountNotFoundException
    {
        U user = _userMap.get(principal.getName());

        if (user == null)
        {
            throw new AccountNotFoundException(principal.getName());
        }

        char[] orig = user.getPassword();
        _userUpdate.lock();
        try
        {
            user.setPassword(password);

            savePasswordFile();

            return true;
        }
        catch (IOException e)
        {
            getLogger().error("Unable to save password file due to '" + e.getMessage()
                              + "', password change for user '" + principal + "' discarded");
            //revert the password change
            user.restorePassword(orig);

            return false;
        }
        finally
        {
            _userUpdate.unlock();
        }
    }


    private void loadPasswordFile() throws IOException
    {
        try
        {
            _userUpdate.lock();
            _userMap.clear();

            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(_passwordFile));
                String line;

                while ((line = reader.readLine()) != null)
                {
                    String[] result = _regexp.split(line);
                    if (result == null || result.length < 2 || result[0].startsWith("#"))
                    {
                        continue;
                    }

                    U user = createUserFromFileData(result);
                    getLogger().info("Created user:" + user);
                    _userMap.put(user.getName(), user);
                }
            }
            finally
            {
                if (reader != null)
                {
                    reader.close();
                }
            }
        }
        finally
        {
            _userUpdate.unlock();
        }
    }

    protected abstract U createUserFromFileData(String[] result);


    protected abstract Logger getLogger();

    protected File createTempFileOnSameFilesystem()
    {
        File liveFile = _passwordFile;
        File tmp;

        do
        {
            tmp = new File(liveFile.getPath() + _random.nextInt() + ".tmp");
        }
        while(tmp.exists());

        tmp.deleteOnExit();
        return tmp;
    }

    protected void swapTempFileToLive(final File temp) throws IOException
    {
        File live = _passwordFile;
        // Remove any existing ".old" file
        final File old = new File(live.getAbsoluteFile() + ".old");
        if (old.exists())
        {
            old.delete();
        }

        // Create an new ".old" file
        if(!live.renameTo(old))
        {
            //unable to rename the existing file to the backup name
            getLogger().error("Could not backup the existing password file");
            throw new IOException("Could not backup the existing password file");
        }

        // Move temp file to be the new "live" file
        if(!temp.renameTo(live))
        {
            //failed to rename the new file to the required filename
            if(!old.renameTo(live))
            {
                //unable to return the backup to required filename
                getLogger().error(
                        "Could not rename the new password file into place, and unable to restore original file");
                throw new IOException("Could not rename the new password file into place, and unable to restore original file");
            }

            getLogger().error("Could not rename the new password file into place");
            throw new IOException("Could not rename the new password file into place");
        }
    }

    protected void savePasswordFile() throws IOException
    {
        try
        {
            _userUpdate.lock();

            BufferedReader reader = null;
            PrintStream writer = null;

            File tmp = createTempFileOnSameFilesystem();

            try
            {
                writer = new PrintStream(tmp);
                reader = new BufferedReader(new FileReader(_passwordFile));
                String line;

                while ((line = reader.readLine()) != null)
                {
                    String[] result = _regexp.split(line);
                    if (result == null || result.length < 2 || result[0].startsWith("#"))
                    {
                        writer.write(line.getBytes(DEFAULT_ENCODING));
                        writer.println();
                        continue;
                    }

                    U user = _userMap.get(result[0]);

                    if (user == null)
                    {
                        writer.write(line.getBytes(DEFAULT_ENCODING));
                        writer.println();
                    }
                    else if (!user.isDeleted())
                    {
                        if (!user.isModified())
                        {
                            writer.write(line.getBytes(DEFAULT_ENCODING));
                            writer.println();
                        }
                        else
                        {
                            byte[] encodedPassword = user.getEncodedPassword();

                            writer.write((user.getName() + ":").getBytes(DEFAULT_ENCODING));
                            writer.write(encodedPassword);
                            writer.println();

                            user.saved();
                        }
                    }
                }

                for (U user : _userMap.values())
                {
                    if (user.isModified())
                    {
                        byte[] encodedPassword;
                        encodedPassword = user.getEncodedPassword();
                        writer.write((user.getName() + ":").getBytes(DEFAULT_ENCODING));
                        writer.write(encodedPassword);
                        writer.println();
                        user.saved();
                    }
                }
            }
            catch(IOException e)
            {
                getLogger().error("Unable to create the new password file: " + e);
                throw new IOException("Unable to create the new password file" + e);
            }
            finally
            {
                if (reader != null)
                {
                    reader.close();
                }

                if (writer != null)
                {
                    writer.close();
                }
            }

            swapTempFileToLive(tmp);
        }
        finally
        {
            _userUpdate.unlock();
        }
    }

    protected abstract U createUserFromPassword(Principal principal, char[] passwd);


    public void reload() throws IOException
    {
        loadPasswordFile();
    }

    public Map<String, AuthenticationProviderInitialiser> getMechanisms()
    {
        return _saslServers;
    }

    public List<Principal> getUsers()
    {
        return new LinkedList<Principal>(_userMap.values());
    }

    public Principal getUser(String username)
    {
        if (_userMap.containsKey(username))
        {
            return new UsernamePrincipal(username);
        }
        return null;
    }

    public boolean deletePrincipal(Principal principal) throws AccountNotFoundException
    {
        U user = _userMap.get(principal.getName());

        if (user == null)
        {
            throw new AccountNotFoundException(principal.getName());
        }

        try
        {
            _userUpdate.lock();
            user.delete();

            try
            {
                savePasswordFile();
            }
            catch (IOException e)
            {
                getLogger().error("Unable to remove user '" + user.getName() + "' from password file.");
                return false;
            }

            _userMap.remove(user.getName());
        }
        finally
        {
            _userUpdate.unlock();
        }

        return true;
    }

    public boolean createPrincipal(Principal principal, char[] password)
    {
        if (_userMap.get(principal.getName()) != null)
        {
            return false;
        }

        U user = createUserFromPassword(principal, password);


        try
        {
            _userUpdate.lock();
            _userMap.put(user.getName(), user);

            try
            {
                savePasswordFile();
                return true;
            }
            catch (IOException e)
            {
                //remove the use on failure.
                _userMap.remove(user.getName());
                return false;
            }
        }
        finally
        {
            _userUpdate.unlock();
        }
    }
}
