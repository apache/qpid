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

import static org.apache.qpid.transport.Connection.State.CLOSE_RCVD;

import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.server.configuration.BrokerConfig;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.virtualhost.State;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class ServerConnectionDelegate extends ServerDelegate
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConnectionDelegate.class);

    private final String _localFQDN;
    private final IApplicationRegistry _appRegistry;
    private int _maxNoOfChannels;
    private Map<String,Object> _clientProperties;

    public ServerConnectionDelegate(IApplicationRegistry appRegistry, String localFQDN)
    {
        this(createConnectionProperties(appRegistry.getBroker()), Collections.singletonList((Object)"en_US"), appRegistry, localFQDN);
    }

    public ServerConnectionDelegate(Map<String, Object> properties,
                                    List<Object> locales,
                                    IApplicationRegistry appRegistry,
                                    String localFQDN)
    {
        super(properties, parseToList(appRegistry.getAuthenticationManager().getMechanisms()), locales);
        
        _appRegistry = appRegistry;
        _localFQDN = localFQDN;
        _maxNoOfChannels = ApplicationRegistry.getInstance().getConfiguration().getMaxChannelCount();
    }

    private static Map<String, Object> createConnectionProperties(final BrokerConfig brokerConfig)
    {
        final Map<String,Object> map = new HashMap<String,Object>(2);
        map.put(ServerPropertyNames.FEDERATION_TAG, brokerConfig.getFederationTag());
        final List<String> features = brokerConfig.getFeatures();
        if (features != null && features.size() > 0)
        {
            map.put(ServerPropertyNames.QPID_FEATURES, features);
        }
        return map;
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

    @Override
    public void connectionClose(Connection conn, ConnectionClose close)
    {
        final ServerConnection sconn = (ServerConnection) conn;
        try
        {
            sconn.logClosed();
        }
        finally
        {
            sconn.closeCode(close);
            sconn.setState(CLOSE_RCVD);
            sendConnectionCloseOkAndCloseSender(conn);
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
                sconn.setState(Connection.State.CLOSING);
                sconn.invoke(new ConnectionClose(ConnectionCloseCode.CONNECTION_FORCED, "Permission denied '"+vhostName+"'"));
            }
            else if (vhost.getState() != State.ACTIVE)
            {
                sconn.setState(Connection.State.CLOSING);
                sconn.invoke(new ConnectionClose(ConnectionCloseCode.CONNECTION_FORCED, "Virtual host '"+vhostName+"' is not active"));
            }
            else
            {
                sconn.setState(Connection.State.OPEN);
                sconn.invoke(new ConnectionOpenOk(Collections.emptyList()));
            }
        }
        else
        {
            sconn.setState(Connection.State.CLOSING);
            sconn.invoke(new ConnectionClose(ConnectionCloseCode.INVALID_PATH, "Unknown virtualhost '"+vhostName+"'"));
        }
        
    }

    @Override
    public void connectionTuneOk(final Connection conn, final ConnectionTuneOk ok)
    {
        ServerConnection sconn = (ServerConnection) conn;
        int okChannelMax = ok.getChannelMax();

        if (okChannelMax > getChannelMax())
        {
            LOGGER.error("Connection '" + sconn.getConnectionId() + "' being severed, " +
                    "client connectionTuneOk returned a channelMax (" + okChannelMax +
                    ") above the server's offered limit (" + getChannelMax() +")");

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
        return _maxNoOfChannels;
    }

    protected void setChannelMax(int channelMax)
    {
        _maxNoOfChannels = channelMax;
    }

    @Override public void sessionDetach(Connection conn, SessionDetach dtc)
    {
        // To ensure a clean detach, we stop any remaining subscriptions. Stop ensures
        // that any in-progress delivery (SubFlushRunner/QueueRunner) is completed before the stop
        // completes.
        stopAllSubscriptions(conn, dtc);
        Session ssn = conn.getSession(dtc.getChannel());
        ((ServerSession)ssn).setClose(true);
        super.sessionDetach(conn, dtc);
    }

    private void stopAllSubscriptions(Connection conn, SessionDetach dtc)
    {
        final ServerSession ssn = (ServerSession) conn.getSession(dtc.getChannel());
        final Collection<Subscription_0_10> subs = ssn.getSubscriptions();
        for (Subscription_0_10 subscription_0_10 : subs)
        {
            subscription_0_10.stop();
        }
    }


    @Override
    public void sessionAttach(final Connection conn, final SessionAttach atc)
    {
        final Session ssn;

        if(isSessionNameUnique(atc.getName(), conn))
        {
            super.sessionAttach(conn, atc);
            ((ServerConnection)conn).checkForNotification();
        }
        else
        {
            ssn = getSession(conn, atc);
            ssn.invoke(new SessionDetached(atc.getName(), SessionDetachCode.SESSION_BUSY));
            ssn.closed();
        }
    }

    private boolean isSessionNameUnique(final byte[] name, final Connection conn)
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

    @Override
    public void connectionStartOk(Connection conn, ConnectionStartOk ok)
    {
        _clientProperties = ok.getClientProperties();
        super.connectionStartOk(conn, ok);
    }

    public Map<String,Object> getClientProperties()
    {
        return _clientProperties;
    }

    public String getClientId()
    {
        return _clientProperties == null ? null : (String) _clientProperties.get(ConnectionStartProperties.CLIENT_ID_0_10);
    }

    public String getClientVersion()
    {
        return _clientProperties == null ? null : (String) _clientProperties.get(ConnectionStartProperties.VERSION_0_10);
    }
}
