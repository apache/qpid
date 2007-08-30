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
import org.apache.qpid.server.security.access.AccessManager;
import org.apache.qpid.server.security.access.AccessResult;
import org.apache.qpid.server.security.access.AccessRights;
import org.apache.qpid.server.security.access.Accessable;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.Principal;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class PlainPasswordVhostFilePrincipalDatabase extends PlainPasswordFilePrincipalDatabase implements AccessManager
{
    private static final Logger _logger = Logger.getLogger(PlainPasswordVhostFilePrincipalDatabase.class);

    /**
     * Looks up the virtual hosts for a specified user in the password file.
     *
     * @param user The user to lookup
     *
     * @return a list of virtualhosts
     */
    private String[] lookupVirtualHost(String user)
    {
        try
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
                        if (result == null || result.length < 3)
                        {
                            continue;
                        }

                        if (user.equals(result[0]))
                        {
                            return result[2].split(",");
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
        catch (IOException ioe)
        {
            //ignore
        }
        return null;
    }


    public AccessResult isAuthorized(Accessable accessObject, String username)
    {
        return isAuthorized(accessObject, new UsernamePrincipal(username), AccessRights.Rights.READ);
    }

    public AccessResult isAuthorized(Accessable accessObject, Principal user, AccessRights.Rights rights)
    {

        if (accessObject instanceof VirtualHost)
        {
            String[] hosts = lookupVirtualHost(user.getName());

            if (hosts != null)
            {
                for (String host : hosts)
                {
                    if (accessObject.getAccessableName().equals(host))
                    {
                        return new AccessResult(this, AccessResult.AccessStatus.GRANTED);
                    }
                }
            }
        }

        return new AccessResult(this, AccessResult.AccessStatus.REFUSED);
    }

    public String getName()
    {
        return "PlainPasswordVhostFile";
    }

}
