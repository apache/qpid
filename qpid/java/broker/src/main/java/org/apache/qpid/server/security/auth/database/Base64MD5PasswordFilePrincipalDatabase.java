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

import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HashedInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexInitialiser;

import javax.security.auth.login.AccountNotFoundException;
import java.security.Principal;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class Base64MD5PasswordFilePrincipalDatabase extends AbstractPasswordFilePrincipalDatabase<HashedUser>
{
    private final Logger _logger = Logger.getLogger(Base64MD5PasswordFilePrincipalDatabase.class);

    public Base64MD5PasswordFilePrincipalDatabase()
    {
        /**
         *  Create Authenticators for MD5 Password file.
         */
        super(new CRAMMD5HashedInitialiser(), new CRAMMD5HexInitialiser());

    }


    /**
     * Used to verify that the presented Password is correct. Currently only used by Management Console
     *
     * @param principal The principal to authenticate
     * @param password  The password to check
     *
     * @return true if password is correct
     *
     * @throws AccountNotFoundException if the principal cannot be found
     */
    public boolean verifyPassword(String principal, char[] password) throws AccountNotFoundException
    {
        char[] pwd = lookupPassword(principal);
        
        if (pwd == null)
        {
            throw new AccountNotFoundException("Unable to lookup the specfied users password");
        }
        
        byte[] byteArray = new byte[password.length];
        int index = 0;
        for (char c : password)
        {
            byteArray[index++] = (byte) c;
        }
        
        byte[] MD5byteArray;
        try
        {
            MD5byteArray = HashedUser.getMD5(byteArray);
        }
        catch (Exception e1)
        {
            getLogger().warn("Unable to hash password for user '" + principal + "' for comparison");
            return false;
        }
        
        char[] hashedPassword = new char[MD5byteArray.length];

        index = 0;
        for (byte c : MD5byteArray)
        {
            hashedPassword[index++] = (char) c;
        }

        return compareCharArray(pwd, hashedPassword);
    }

    protected HashedUser createUserFromPassword(Principal principal, char[] passwd)
    {
        return new HashedUser(principal.getName(), passwd);
    }


    protected HashedUser createUserFromFileData(String[] result)
    {
        return new HashedUser(result);
    }

    protected Logger getLogger()
    {
        return _logger;
    }

}
