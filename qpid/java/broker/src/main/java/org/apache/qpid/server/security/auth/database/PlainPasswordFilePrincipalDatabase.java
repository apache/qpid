/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.auth.database;

import org.apache.log4j.Logger;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.amqplain.AmqPlainInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5Initialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainInitialiser;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class PlainPasswordFilePrincipalDatabase implements PrincipalDatabase
{
    private static final Logger _logger = Logger.getLogger(PlainPasswordFilePrincipalDatabase.class);

    protected File _passwordFile;

    protected Pattern _regexp = Pattern.compile(":");

    protected Map<String, AuthenticationProviderInitialiser> _saslServers;

    public PlainPasswordFilePrincipalDatabase()
    {
        _saslServers = new HashMap<String, AuthenticationProviderInitialiser>();

        /**
         *  Create Authenticators for Plain Password file.
         */

        // Accept AMQPlain incomming and compare it to the file.
        AmqPlainInitialiser amqplain = new AmqPlainInitialiser();
        amqplain.initialise(this);

        // Accept Plain incomming and compare it to the file.
        PlainInitialiser plain = new PlainInitialiser();
        plain.initialise(this);

        //  Accept MD5 incomming and Hash file value for comparison
        CRAMMD5Initialiser cram = new CRAMMD5Initialiser();
        cram.initialise(this);

        _saslServers.put(amqplain.getMechanismName(), amqplain);
        _saslServers.put(plain.getMechanismName(), plain);
        _saslServers.put(cram.getMechanismName(), cram);
    }

    public void setPasswordFile(String passwordFile) throws FileNotFoundException
    {
        File f = new File(passwordFile);
        _logger.info("PlainPasswordFile using file " + f.getAbsolutePath());
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
    }

    public void setPassword(Principal principal, PasswordCallback callback) throws IOException,
                                                                                   AccountNotFoundException
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

    public boolean verifyPassword(String principal, char[] password) throws AccountNotFoundException
    {
        try
        {
            char[] pwd = lookupPassword(principal);

            return compareCharArray(pwd, password);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public boolean updatePassword(Principal principal, char[] password) throws AccountNotFoundException
    {
        return false; // updates denied
    }

    public boolean createPrincipal(Principal principal, char[] password)
    {
        return false; // updates denied
    }

    public boolean deletePrincipal(Principal principal) throws AccountNotFoundException
    {
        return false; // updates denied
    }

    public Map<String, AuthenticationProviderInitialiser> getMechanisms()
    {
        return _saslServers;
    }

    public List<Principal> getUsers()
    {
        return new LinkedList<Principal>(); //todo
    }

    public Principal getUser(String username)
    {
        try
        {
            if (lookupPassword(username) != null)
            {
                return new UsernamePrincipal(username);
            }
        }
        catch (IOException e)
        {
            //fall through to null return
        }
        return null;
    }

    private boolean compareCharArray(char[] a, char[] b)
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
     * Looks up the password for a specified user in the password file. Note this code is <b>not</b> secure since it
     * creates strings of passwords. It should be modified to create only char arrays which get nulled out.
     *
     * @param name the name of the principal to lookup
     *
     * @return char[] of the password
     *
     * @throws java.io.IOException whilst accessing the file
     */
    private char[] lookupPassword(String name) throws IOException
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(_passwordFile));
            String line;

            while ((line = reader.readLine()) != null)
            {
                if (!line.startsWith("#"))
                {
                    String[] result = _regexp.split(line);
                    if (result == null || result.length < 2)
                    {
                        continue;
                    }

                    if (name.equals(result[0]))
                    {
                        return result[1].toCharArray();
                    }
                }
            }
            return null;
        }
        finally
        {
            if (reader != null)
            {
                reader.close();
            }
        }
    }

    public void reload() throws IOException
    {
        //This PD is not cached, so do nothing.
    }
}
