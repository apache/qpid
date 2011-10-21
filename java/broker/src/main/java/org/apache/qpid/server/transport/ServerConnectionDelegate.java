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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionClose;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ConnectionOpen;
import org.apache.qpid.transport.ConnectionOpenOk;
import org.apache.qpid.transport.ConnectionTuneOk;
import org.apache.qpid.transport.ServerDelegate;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionAttach;
import org.apache.qpid.transport.SessionDelegate;
import org.apache.qpid.transport.SessionDetach;
import org.apache.qpid.transport.SessionDetachCode;
import org.apache.qpid.transport.SessionDetached;

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

    public ServerSession getSession(Connection conn, SessionAttach atc)
    {
        SessionDelegate serverSessionDelegate = new ServerSessionDelegate();

        ServerSession ssn = new ServerSession(conn, serverSessionDelegate,  new Binary(atc.getName()), 0);

        return ssn;
    }

    protected SaslServer createSaslServer(String mechanism) throws SaslException
    {
        return _appRegistry.getAuthenticationManager().createSaslServer(mechanism, _localFQDN);

    }

    protected void secure(final SaslServer ss, final Connection conn, final byte[] response)
    {
        final AuthenticationResult authResult = _appRegistry.getAuthenticationManager().authenticate(ss, response);
        final ServerConnection sconn = (ServerConnection) conn;
        
        
        if (AuthenticationStatus.SUCCESS.equals(authResult.getStatus()))
        {
            tuneAuthorizedConnection(sconn);
            sconn.setAuthorizedSubject(authResult.getSubject());
        }
        else if (AuthenticationStatus.CONTINUE.equals(authResult.getStatus()))
        {
            connectionAuthContinue(sconn, authResult.getChallenge());
        }
        else
        {
            connectionAuthFailed(sconn, authResult.getCause());
        }
    }

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

    public void connectionOpen(Connection conn, ConnectionOpen open)
    {
        final ServerConnection sconn = (ServerConnection) conn;
        
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

        SecurityManager.setThreadSubject(sconn.getAuthorizedSubject());
        
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
    public void connectionTuneOk(final Connection conn, final ConnectionTuneOk ok)
    {
        ServerConnection sconn = (ServerConnection) conn;
        int okChannelMax = ok.getChannelMax();

        if (okChannelMax > getChannelMax())
        {
            _logger.error("Connection '" + sconn.getConnectionId() + "' being severed, " +
                    "client connectionTuneOk returned a channelMax (" + okChannelMax +
                    ") above the servers offered limit (" + getChannelMax() +")");

            //Due to the error we must forcefully close the connection without negotiation
            sconn.getSender().close();
            return;
        }

        setConnectionTuneOkChannelMax(sconn, okChannelMax);
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

    @Override public void sessionDetach(Connection conn, SessionDetach dtc)
    {
        // To ensure a clean detach, we unregister any remaining subscriptions. Unregister ensures
        // that any in-progress delivery (SubFlushRunner/QueueRunner) is completed before the unregister
        // completes.
        unregisterAllSubscriptions(conn, dtc);
        super.sessionDetach(conn, dtc);
    }

    private void unregisterAllSubscriptions(Connection conn, SessionDetach dtc)
    {
        final ServerSession ssn = (ServerSession) conn.getSession(dtc.getChannel());
        final Collection<Subscription_0_10> subs = ssn.getSubscriptions();
        for (Subscription_0_10 subscription_0_10 : subs)
        {
            ssn.unregister(subscription_0_10);
        }
    }

    @Override
    public void sessionAttach(final Connection conn, final SessionAttach atc)
    {
        final String clientId = new String(atc.getName());
        final Session ssn = getSession(conn, atc);

        if(isSessionNameUnique(clientId,conn))
        {
            conn.registerSession(ssn);
            super.sessionAttach(conn, atc);
        }
        else
        {
            ssn.invoke(new SessionDetached(atc.getName(), SessionDetachCode.SESSION_BUSY));
            ssn.closed();
        }
    }

    private boolean isSessionNameUnique(final String name, final Connection conn)
    {
        final ServerConnection sconn = (ServerConnection) conn;
        final String userId = sconn.getUserName();

        final Iterator<AMQConnectionModel> connections =
                        ((ServerConnection)conn).getVirtualHost().getConnectionRegistry().getConnections().iterator();
        while(connections.hasNext())
        {
            final AMQConnectionModel amqConnectionModel = (AMQConnectionModel) connections.next();
            if (userId.equals(amqConnectionModel.getUserName()) && !amqConnectionModel.isSessionNameUnique(name))
            {
                return false;
            }
        }
        return true;
    }
}
