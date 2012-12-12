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
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.virtualhost.State;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.*;
import org.apache.qpid.transport.network.NetworkConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.qpid.transport.Connection.State.CLOSE_RCVD;

public class ServerConnectionDelegate extends ServerDelegate
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConnectionDelegate.class);

    private final Broker _broker;
    private final String _localFQDN;
    private int _maxNoOfChannels;
    private Map<String,Object> _clientProperties;
    private final SubjectCreator _subjectCreator;

    public ServerConnectionDelegate(Broker broker, String localFQDN, SubjectCreator subjectCreator)
    {
        this(createConnectionProperties(broker), Collections.singletonList((Object)"en_US"), broker, localFQDN, subjectCreator);
    }

    private ServerConnectionDelegate(Map<String, Object> properties,
                                    List<Object> locales,
                                    Broker broker,
                                    String localFQDN,
                                    SubjectCreator subjectCreator)
    {
        super(properties, parseToList(subjectCreator.getMechanisms()), locales);

        _broker = broker;
        _localFQDN = localFQDN;
        _maxNoOfChannels = (Integer)broker.getAttribute(Broker.SESSION_COUNT_LIMIT);
        _subjectCreator = subjectCreator;
    }

    private static List<String> getFeatures(Broker broker)
    {
        String brokerDisabledFeatures = System.getProperty(BrokerProperties.PROPERTY_DISABLED_FEATURES);
        final List<String> features = new ArrayList<String>();
        if (brokerDisabledFeatures == null || !brokerDisabledFeatures.contains(ServerPropertyNames.FEATURE_QPID_JMS_SELECTOR))
        {
            features.add(ServerPropertyNames.FEATURE_QPID_JMS_SELECTOR);
        }

        return Collections.unmodifiableList(features);
    }

    private static Map<String, Object> createConnectionProperties(final Broker broker)
    {
        final Map<String,Object> map = new HashMap<String,Object>(2);
        // Federation tag is used by the client to identify the broker instance
        map.put(ServerPropertyNames.FEDERATION_TAG, broker.getId().toString());
        final List<String> features = getFeatures(broker);
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

    protected SaslServer createSaslServer(Connection conn, String mechanism) throws SaslException
    {
        return _subjectCreator.createSaslServer(mechanism, _localFQDN, ((ServerConnection) conn).getPeerPrincipal());

    }

    protected void secure(final SaslServer ss, final Connection conn, final byte[] response)
    {
        final ServerConnection sconn = (ServerConnection) conn;
        final SubjectAuthenticationResult authResult = _subjectCreator.authenticate(ss, response);

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
        vhost = _broker.getVirtualHostRegistry().getVirtualHost(vhostName);

        SecurityManager.setThreadSubject(sconn.getAuthorizedSubject());

        if(vhost != null)
        {
            sconn.setVirtualHost(vhost);

            if (!vhost.getSecurityManager().accessVirtualhost(vhostName, sconn.getRemoteAddress()))
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

        if(ok.hasHeartbeat())
        {
            final int heartbeat = ok.getHeartbeat();
            if(heartbeat > 0)
            {
                final NetworkConnection networkConnection = sconn.getNetworkConnection();
                networkConnection.setMaxReadIdle(2 * heartbeat);
                networkConnection.setMaxWriteIdle(heartbeat);
            }
        }

        setConnectionTuneOkChannelMax(sconn, okChannelMax);
    }

    @Override
    public int getChannelMax()
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
