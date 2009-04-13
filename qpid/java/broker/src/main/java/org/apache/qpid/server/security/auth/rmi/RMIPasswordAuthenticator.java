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
package org.apache.qpid.server.security.auth.rmi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;

import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;

public class RMIPasswordAuthenticator implements JMXAuthenticator
{
    static final String UNABLE_TO_LOOKUP = "The broker was unable to lookup the user details";
    static final String SHOULD_BE_STRING_ARRAY = "User details should be String[]";
    static final String SHOULD_HAVE_2_ELEMENTS = "User details should have 2 elements, username, password";
    static final String SHOULD_BE_NON_NULL = "Supplied username and password should be non-null";
    static final String INVALID_CREDENTIALS = "Invalid user details supplied";
    static final String CREDENTIALS_REQUIRED = "User details are required. " +
    		            "Please ensure you are using an up to date management console to connect.";
    
    public static final String DEFAULT_ENCODING = "utf-8";
    private PrincipalDatabase _db = null;

    public RMIPasswordAuthenticator()
    {
    }
    
    public void setPrincipalDatabase(PrincipalDatabase pd)
    {
        this._db = pd;
    }

    public Subject authenticate(Object credentials) throws SecurityException
    {
        // Verify that credential's are of type String[].
        if (!(credentials instanceof String[]))
        {
            if (credentials == null)
            {
                throw new SecurityException(CREDENTIALS_REQUIRED);
            }
            else
            {
                throw new SecurityException(SHOULD_BE_STRING_ARRAY);
            }
        }

        // Verify that required number of credential's.
        final String[] userCredentials = (String[]) credentials;
        if (userCredentials.length != 2)
        {
            throw new SecurityException(SHOULD_HAVE_2_ELEMENTS);
        }

        String username = (String) userCredentials[0];
        String password = (String) userCredentials[1];

        // Verify that all required credential's are actually present.
        if (username == null || password == null)
        {
            throw new SecurityException(SHOULD_BE_NON_NULL);
        }
        
        boolean authenticated = false;

        // Perform authentication
        try
        {
            PasswordCallback pwCallback = new PasswordCallback("prompt",false);
            UsernamePrincipal uname = new UsernamePrincipal(username);
            
            if (_db instanceof Base64MD5PasswordFilePrincipalDatabase)
            {
                //retrieve the stored password for the given user
                _db.setPassword(uname, pwCallback);
                
                //compare the MD5Hash of the given password with the stored value
                if (Arrays.equals(getMD5Hash(password), pwCallback.getPassword()))
                {                  
                    authenticated = true;
                }
            }
            else if (_db instanceof PlainPasswordFilePrincipalDatabase)
            {
                //retrieve the users stored password and compare with given value
                _db.setPassword(uname, pwCallback);
                
                if (password.equals(new String(pwCallback.getPassword())))
                {
                    authenticated = true;
                }
            }
            else
            {
                throw new SecurityException(UNABLE_TO_LOOKUP);
            }
        }
        catch (AccountNotFoundException e)
        {
            throw new SecurityException(INVALID_CREDENTIALS);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new SecurityException(UNABLE_TO_LOOKUP);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new SecurityException(UNABLE_TO_LOOKUP);
        }
        catch (IOException e)
        {
            throw new SecurityException(UNABLE_TO_LOOKUP);
        }

        if (authenticated)
        {
            //credential's check out, return the appropriate JAAS Subject
            return new Subject(true,
                    Collections.singleton(new JMXPrincipal(username)),
                    Collections.EMPTY_SET,
                    Collections.EMPTY_SET);
        }
        else
        {
            throw new SecurityException(INVALID_CREDENTIALS);
        }
    }
    
    public static char[] getMD5Hash(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        byte[] data = text.getBytes(DEFAULT_ENCODING);

        MessageDigest md = MessageDigest.getInstance("MD5");

        for (byte b : data)
        {
            md.update(b);
        }

        byte[] digest = md.digest();

        char[] hash = new char[digest.length ];

        int index = 0;
        for (byte b : digest)
        {            
            hash[index++] = (char) b;
        }

        return hash;
    }
}