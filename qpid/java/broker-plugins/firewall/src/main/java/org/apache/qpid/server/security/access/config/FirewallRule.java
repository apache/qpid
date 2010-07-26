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
package org.apache.qpid.server.security.access.config;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.qpid.server.security.Result;
import org.apache.qpid.util.NetMatcher;

public class FirewallRule 
{
	public static final String ALLOW = "ALLOW";
	public static final String DENY = "DENY";
	
    private static final long DNS_TIMEOUT = 30000;
    private static final ExecutorService DNS_LOOKUP = Executors.newCachedThreadPool();
    
    private Result _access;
    private NetMatcher _network;
    private Pattern[] _hostnamePatterns;

    public FirewallRule(String access, List networks, List hostnames)
    {
        _access = (access.equalsIgnoreCase(ALLOW)) ? Result.ALLOWED : Result.DENIED;

        if (networks != null && networks.size() > 0)
        {
            String[] networkStrings = objListToStringArray(networks);
            _network = new NetMatcher(networkStrings);
        }

        if (hostnames != null && hostnames.size() > 0)
        {
            int i = 0;
            _hostnamePatterns = new Pattern[hostnames.size()];
            for (String hostname : objListToStringArray(hostnames))
            {
                _hostnamePatterns[i++] = Pattern.compile(hostname);
            }
        }
    }

    private String[] objListToStringArray(List objList)
    {
        String[] networkStrings = new String[objList.size()];
        int i = 0;
        for (Object network : objList)
        {
            networkStrings[i++] = (String) network;
        }
        return networkStrings;
    }

    public boolean match(InetAddress remote) throws FirewallException
    {
        if (_hostnamePatterns != null)
        {
            String hostname = getHostname(remote);
            if (hostname == null)
            {
                throw new FirewallException("DNS lookup failed");
            }
            for (Pattern pattern : _hostnamePatterns)
            {
                if (pattern.matcher(hostname).matches())
                {
                    return true;
                }
            }
            return false;
        }
        else
        {
            return _network.matchInetNetwork(remote);
        }
    }

    /**
     * @param remote the InetAddress to look up
     * @return the hostname, null if not found, takes longer than 30s to find or otherwise fails
     */
    private String getHostname(final InetAddress remote) throws FirewallException
    {
        FutureTask<String> lookup = new FutureTask<String>(new Callable<String>()
        {
           public String call()
           {
               return remote.getCanonicalHostName();
           }
        });
        DNS_LOOKUP.execute(lookup);
        
        try
        {
            return lookup.get(DNS_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            return null; 
        }
        finally
        {
            lookup.cancel(true);
        }
    }

    public Result getAccess()
    {
        return _access;
    }
}