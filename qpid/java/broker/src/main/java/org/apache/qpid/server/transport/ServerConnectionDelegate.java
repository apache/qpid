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
package org.apache.qpid.server.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.*;

public class ServerConnectionDelegate extends ServerDelegate
{
    private String _localFQDN;
    private final IApplicationRegistry _appRegistry;

    public ServerConnectionDelegate(IApplicationRegistry appRegistry, String localFQDN)
    {
        this(new HashMap<String,Object>(Collections.singletonMap("qpid.federation_tag",appRegistry.getBroker().getFederationTag())), Collections.singletonList((Object)"en_US"), appRegistry, localFQDN);
    }


    public ServerConnectionDelegate(Map<String, Object> properties,
                                    List<Object> locales,
                                    IApplicationRegistry appRegistry,
                                    String localFQDN)
    {
        super(properties, parseToList(appRegistry.getAuthenticationManager().getMechanisms()), locales);
        
        _appRegistry = appRegistry;
        _localFQDN = localFQDN;
    }

    private static List<Object> parseToList(String mechanisms)
    {
        List<Object> list = new ArrayList<Object>();
        StringTokenizer tokenizer = new StringTokenizer(mechanisms, " ");
        while(tokenizer.hasMoreTokens())
        {
            list.add(tokenizer.nextToken());
        }
        return list;
    }

    @Override
    public ServerSession getSession(Connection conn, SessionAttach atc)
    {
        SessionDelegate serverSessionDelegate = new ServerSessionDelegate(_appRegistry);

        ServerSession ssn = new ServerSession(conn, serverSessionDelegate,  new Binary(atc.getName()), 0);

        return ssn;
    }

    @Override
    protected SaslServer createSaslServer(String mechanism) throws SaslException
    {
        return _appRegistry.getAuthenticationManager().createSaslServer(mechanism, _localFQDN);

    }

    @Override
    public void connectionClose(Connection conn, ConnectionClose close)
    {
        try
        {
            ((ServerConnection) conn).logClosed();
        }
        finally
        {
            super.connectionClose(conn, close);
        }
        
    }

    @Override
    public void connectionOpen(Connection conn, ConnectionOpen open)
    {
        ServerConnection sconn = (ServerConnection) conn;
        
        VirtualHost vhost;
        String vhostName;
        if(open.hasVirtualHost())
        {
            vhostName = open.getVirtualHost();
        }
        else
        {
            vhostName = "";
        }
        vhost = _appRegistry.getVirtualHostRegistry().getVirtualHost(vhostName);

        SecurityManager.setThreadPrincipal(conn.getAuthorizationID());
        
        if(vhost != null)
        {
            sconn.setVirtualHost(vhost);

            if (!vhost.getSecurityManager().accessVirtualhost(vhostName, ((ProtocolEngine) sconn.getConfig()).getRemoteAddress()))
            {
                sconn.invoke(new ConnectionClose(ConnectionCloseCode.CONNECTION_FORCED, "Permission denied '"+vhostName+"'"));
                sconn.setState(Connection.State.CLOSING);
            }
            else
            {
	            sconn.invoke(new ConnectionOpenOk(Collections.emptyList()));
	            sconn.setState(Connection.State.OPEN);
            }
        }
        else
        {
            sconn.invoke(new ConnectionClose(ConnectionCloseCode.INVALID_PATH, "Unknown virtualhost '"+vhostName+"'"));
            sconn.setState(Connection.State.CLOSING);
        }
        
    }
    
    @Override
    protected int getHeartbeatMax()
    {
        //TODO: implement broker support for actually sending heartbeats
        return 0;
    }

    @Override
    protected int getChannelMax()
    {
        return ApplicationRegistry.getInstance().getConfiguration().getMaxChannelCount();
    }
}
