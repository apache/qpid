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
package org.apache.qpid.server.cluster;

import org.apache.qpid.framing.AMQShortString;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class SimpleMemberHandle implements MemberHandle
{
    private final String _host;
    private final int _port;

    public SimpleMemberHandle(String host, int port)
    {
        _host = host;
        _port = port;
    }

    public SimpleMemberHandle(AMQShortString details)
    {
        this(details.toString());
    }

    public SimpleMemberHandle(String details)
    {
        String[] parts = details.split(":");
        _host = parts[0];
        _port = Integer.parseInt(parts[1]);
    }

    public SimpleMemberHandle(InetSocketAddress address) throws UnknownHostException
    {
        this(address.getAddress(), address.getPort());
    }

    public SimpleMemberHandle(InetAddress address, int port) throws UnknownHostException
    {
        this(canonical(address).getHostAddress(), port);
    }

    public String getHost()
    {
        return _host;
    }

    public int getPort()
    {
        return _port;
    }

    public int hashCode()
    {
        return getPort();
    }

    public boolean equals(Object o)
    {
        return o instanceof MemberHandle && matches((MemberHandle) o);
    }

    public boolean matches(MemberHandle m)
    {
        return matches(m.getHost(), m.getPort());
    }

    public boolean matches(String host, int port)
    {
        return _host.equals(host) && _port == port;
    }

    public AMQShortString getDetails()
    {
        return new AMQShortString(_host + ":" + _port);
    }

    public String toString()
    {
        return getDetails().toString();
    }

    static List<MemberHandle> stringToMembers(String membership)
    {
        String[] names = membership.split("\\s");
        List<MemberHandle> members = new ArrayList<MemberHandle>();
        for (String name : names)
        {
            members.add(new SimpleMemberHandle(name));
        }
        return members;
    }

    static String membersToString(List<MemberHandle> members)
    {
        StringBuffer buffer = new StringBuffer();
        boolean first = true;
        for (MemberHandle m : members)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                buffer.append(" ");
            }
            buffer.append(m.getDetails());
        }

        return buffer.toString();
    }

    private static InetAddress canonical(InetAddress address) throws UnknownHostException
    {
        if (address.isLoopbackAddress())
        {
            return InetAddress.getLocalHost();
        }
        else
        {
            return address;
        }
    }

    public MemberHandle resolve()
    {
        return resolve(this);
    }

    public static MemberHandle resolve(MemberHandle handle)
    {
        try
        {
            return new SimpleMemberHandle(new InetSocketAddress(handle.getHost(), handle.getPort()));
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
            return handle;
        }
    }


}
