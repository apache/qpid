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
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HashedInitialiser;
import org.apache.commons.codec.binary.Base64;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.security.Principal;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class Base64MD5PasswordFilePrincipalDatabase implements PrincipalDatabase
{
    private static final Logger _logger = Logger.getLogger(Base64MD5PasswordFilePrincipalDatabase.class);

    private File _passwordFile;

    private Pattern _regexp = Pattern.compile(":");

    private Map<String, AuthenticationProviderInitialiser> _saslServers;

    public Base64MD5PasswordFilePrincipalDatabase()
    {
        _saslServers = new HashMap<String, AuthenticationProviderInitialiser>();

        /**
         *  Create Authenticators for MD5 Password file.
         */

        // Accept Plain incomming and hash it for comparison to the file.
        CRAMMD5HashedInitialiser cram = new CRAMMD5HashedInitialiser();
        cram.initialise(this);
        _saslServers.put(cram.getMechanismName(), cram);
    }

    public void setPasswordFile(String passwordFile) throws FileNotFoundException
    {
        File f = new File(passwordFile);
        _logger.info("PasswordFilePrincipalDatabase using file " + f.getAbsolutePath());
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

    public boolean verifyPassword(Principal principal, char[] password) throws AccountNotFoundException
    {
        try
        {
            char[] pwd = lookupPassword(principal.getName());
            return compareCharArray(pwd, password);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public Map<String, AuthenticationProviderInitialiser> getMechanisms()
    {
        return _saslServers;
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
     * @param name
     *
     * @return
     *
     * @throws java.io.IOException
     */
    private char[] lookupPassword(String name) throws IOException
    {
        BufferedReader reader = null;
        byte[] passwd = null;
        try
        {
            reader = new BufferedReader(new FileReader(_passwordFile));
            String line;

            while ((line = reader.readLine()) != null)
            {
                String[] result = _regexp.split(line);
                if (result == null || result.length < 2)
                {
                    continue;
                }

                if (name.equals(result[0]))
                {


                    char[] raw = result[1].toCharArray();

                    byte[] encoded = new byte[result[1].length() + 1];

                    int index = 0;
                    for (char c : raw)
                    {
                        index++;
                        encoded[index] = (byte) c;
                    }

                    Base64 b64 = new Base64();
                    byte[] decoded = b64.decode(encoded);


                    char[] hashedPassword = new char[decoded.length + 1];

                    index = 0;
                    for (byte c : decoded)
                    {
                        index++;
                        hashedPassword[index] = (char) c;
                    }

                    return hashedPassword;
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
}
