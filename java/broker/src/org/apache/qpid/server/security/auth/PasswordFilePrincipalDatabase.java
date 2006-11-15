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
package org.apache.qpid.server.security.auth;

import org.apache.log4j.Logger;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import java.security.Principal;
import java.io.*;
import java.util.regex.Pattern;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form:
 * username:password
 * username1:password1
 * ...
 * usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in
 * plain text.
 *
 */
public class PasswordFilePrincipalDatabase implements PrincipalDatabase
{
    private static final Logger _logger = Logger.getLogger(PasswordFilePrincipalDatabase.class);

    private File _passwordFile;

    private Pattern _regexp = Pattern.compile(":");

    public PasswordFilePrincipalDatabase()
    {
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

    /**
     * Looks up the password for a specified user in the password file.
     * Note this code is <b>not</b> secure since it creates strings of passwords. It should be modified
     * to create only char arrays which get nulled out.
     * @param name
     * @return
     * @throws IOException
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
