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
package org.apache.qpid.server.security.access;

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.regex.Pattern;
import java.security.Principal;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class FileAccessManager implements AccessManager
{
    private static final Logger _logger = Logger.getLogger(FileAccessManager.class);

    protected File _accessFile;

    protected Pattern _regexp = Pattern.compile(":");

    private static final short USER_INDEX = 0;
    private static final short VIRTUALHOST_INDEX = 1;

    public void setAccessFile(String accessFile) throws FileNotFoundException
    {
        File f = new File(accessFile);
        _logger.info("FileAccessManager using file " + f.getAbsolutePath());
        _accessFile = f;
        if (!f.exists())
        {
            throw new FileNotFoundException("Cannot find access file " + f);
        }
        if (!f.canRead())
        {
            throw new FileNotFoundException("Cannot read access file " + f +
                                            ". Check permissions.");
        }
    }

    /**
     * Looks up the virtual hosts for a specified user in the access file.
     *
     * @param user The user to lookup
     *
     * @return a list of virtualhosts
     */
    private VirtualHostAccess[] lookupVirtualHost(String user)
    {
        String[] results = lookup(user, VIRTUALHOST_INDEX);
        VirtualHostAccess vhosts[] = new VirtualHostAccess[results.length];

        for (int index = 0; index < results.length; index++)
        {
            vhosts[index] = new VirtualHostAccess(results[index]);
        }

        return vhosts;
    }


    private String[] lookup(String user, int index)
    {
        try
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(_accessFile));
                String line;

                while ((line = reader.readLine()) != null)
                {
                    String[] result = _regexp.split(line);
                    if (result == null || result.length < (index + 1))
                    {
                        continue;
                    }

                    if (user.equals(result[USER_INDEX]))
                    {
                        return result[index].split(",");
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
            VirtualHostAccess[] hosts = lookupVirtualHost(user.getName());

            if (hosts != null)
            {
                for (VirtualHostAccess host : hosts)
                {
                    if (accessObject.getAccessableName().equals(host.getVirtualHost()))
                    {
                        if (host.getAccessRights().allows(rights))
                        {
                            return new AccessResult(this, AccessResult.AccessStatus.GRANTED);
                        }
                        else
                        {
                            return new AccessResult(this, AccessResult.AccessStatus.REFUSED);
                        }
                    }
                }
            }
        }
//        else if (accessObject instanceof AMQQueue)
//        {
//            String[] queues = lookupQueue(username, ((AMQQueue) accessObject).getVirtualHost());
//
//            if (queues != null)
//            {
//                for (String queue : queues)
//                {
//                    if (accessObject.getAccessableName().equals(queue))
//                    {
//                        return new AccessResult(this, AccessResult.AccessStatus.GRANTED);
//                    }
//                }
//            }
//        }

        return new AccessResult(this, AccessResult.AccessStatus.REFUSED);
    }

    public String getName()
    {
        return "FileAccessManager";
    }

}
