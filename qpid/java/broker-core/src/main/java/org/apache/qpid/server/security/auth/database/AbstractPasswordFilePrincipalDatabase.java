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
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.util.BaseAction;
import org.apache.qpid.server.util.FileHelper;

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
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public abstract class AbstractPasswordFilePrincipalDatabase<U extends PasswordPrincipal> implements PrincipalDatabase
{
    protected static final String DEFAULT_ENCODING = "utf-8";

    private final Pattern _regexp = Pattern.compile(":");
    private final Map<String, U> _userMap = new HashMap<>();
    private final ReentrantLock _userUpdate = new ReentrantLock();
    private final FileHelper _fileHelper = new FileHelper();
    private File _passwordFile;

    public final void open(File passwordFile) throws IOException
    {
        getLogger().info("PasswordFile using file " + passwordFile.getAbsolutePath());
        _passwordFile = passwordFile;
        if (!passwordFile.exists())
        {
            throw new FileNotFoundException("Cannot find password file " + passwordFile);
        }
        if (!passwordFile.canRead())
        {
            throw new FileNotFoundException("Cannot read password file " + passwordFile + ". Check permissions.");
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
            final Map<String, U> newUserMap = new HashMap<>();

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
                    newUserMap.put(user.getName(), user);
                }
            }
            finally
            {
                if (reader != null)
                {
                    reader.close();
                }
            }

            _userMap.clear();
            _userMap.putAll(newUserMap);
        }
        finally
        {
            _userUpdate.unlock();
        }
    }

    protected abstract U createUserFromFileData(String[] result);


    protected abstract Logger getLogger();


    protected void savePasswordFile() throws IOException
    {
        try
        {
            _userUpdate.lock();

            _fileHelper.writeFileSafely(_passwordFile.toPath(), new BaseAction<File,IOException>()
            {
                @Override
                public void performAction(File file) throws IOException
                {
                    writeToFile(file);
                }
            });
        }
        finally
        {
            _userUpdate.unlock();
        }
    }

    private void writeToFile(File tmp) throws IOException
    {
            BufferedReader reader = null;
            PrintStream writer = null;

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
                throw new IOException("Unable to create the new password file",e);
            }
            finally
            {

                try
                {
                    if (reader != null)
                    {
                        reader.close();
                    }
                }
                finally
                {
                    if (writer != null)
                    {
                        writer.close();
                    }
                }

        }
    }

    protected abstract U createUserFromPassword(Principal principal, char[] passwd);


    public void reload() throws IOException
    {
        loadPasswordFile();
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
