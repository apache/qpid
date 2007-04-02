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

import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5Initialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainInitialiser;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.security.Principal;
import java.io.IOException;

public class PropertiesPrincipalDatabase implements PrincipalDatabase
{
    private Properties _users;

    private Map<String, AuthenticationProviderInitialiser> _saslServers;

    public PropertiesPrincipalDatabase(Properties users)
    {
        _users = users;

        _saslServers = new HashMap<String, AuthenticationProviderInitialiser>();

        /**
         *  Create Authenticators for Properties Principal Database.
         */

        //  Accept MD5 incomming and use plain comparison with the file
        PlainInitialiser cram = new PlainInitialiser();
        cram.initialise(this);
        // Accept Plain incomming and hash it for comparison to the file.
        CRAMMD5Initialiser plain = new CRAMMD5Initialiser();
        plain.initialise(this, CRAMMD5Initialiser.HashDirection.INCOMMING);

        _saslServers.put(plain.getMechanismName(), cram);
        _saslServers.put(cram.getMechanismName(), plain);
    }

    public void setPassword(Principal principal, PasswordCallback callback) throws IOException, AccountNotFoundException
    {
        if (principal == null)
        {
            throw new IllegalArgumentException("principal must not be null");
        }
        char[] pwd = _users.getProperty(principal.getName()).toCharArray();
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
        char[] pwd = _users.getProperty(principal.getName()).toCharArray();

        return compareCharArray(pwd, password);
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

    public Map<String, AuthenticationProviderInitialiser> getMechanisms()
    {
        return _saslServers;
    }
}
