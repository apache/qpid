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
package org.apache.qpid.server.protocol.v0_10;

import static org.apache.qpid.transport.Connection.State.CLOSE_RCVD;

import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionClose;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ConnectionOpen;
import org.apache.qpid.transport.ConnectionOpenOk;
import org.apache.qpid.transport.ConnectionStartOk;
import org.apache.qpid.transport.ConnectionTuneOk;
import org.apache.qpid.transport.ServerDelegate;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionAttach;
import org.apache.qpid.transport.SessionDelegate;
import org.apache.qpid.transport.SessionDetach;
import org.apache.qpid.transport.SessionDetachCode;
import org.apache.qpid.transport.SessionDetached;
import org.apache.qpid.transport.network.NetworkConnection;

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
        super(properties, (List) subjectCreator.getMechanisms(), locales);

        _broker = broker;
        _localFQDN = localFQDN;
        _maxNoOfChannels = broker.getConnection_sessionCountLimit();
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
        final Map<String,Object> map = new HashMap<String,Object>();
        // Federation tag is used by the client to identify the broker instance
        map.put(ServerPropertyNames.FEDERATION_TAG, broker.getId().toString());
        final List<String> features = getFeatures(broker);
        if (features != null && features.size() > 0)
        {
            map.put(ServerPropertyNames.QPID_FEATURES, features);
        }

        map.put(ServerPropertyNames.PRODUCT, QpidProperties.getProductName());
        map.put(ServerPropertyNames.VERSION, QpidProperties.getReleaseVersion());
        map.put(ServerPropertyNames.QPID_BUILD, QpidProperties.getBuildVersion());
        map.put(ServerPropertyNames.QPID_INSTANCE_NAME, broker.getName());

        return map;
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

        VirtualHostImpl vhost;
        String vhostName;
        if(open.hasVirtualHost())
        {
            vhostName = open.getVirtualHost();
        }
        else
        {
            vhostName = "";
        }

        vhost = ((AmqpPort)sconn.getPort()).getVirtualHost(vhostName);



        if(vhost != null)
        {
            if (vhost.getState() != State.ACTIVE)
            {
                sconn.setState(Connection.State.CLOSING);
                sconn.invoke(new ConnectionClose(ConnectionCloseCode.CONNECTION_FORCED, "Virtual host '"+vhostName+"' is not active"));
                return;
            }

            sconn.setVirtualHost(vhost);
            try
            {
                vhost.getSecurityManager().authoriseCreateConnection(sconn);
            }
            catch (AccessControlException e)
            {
                sconn.setState(Connection.State.CLOSING);
                sconn.invoke(new ConnectionClose(ConnectionCloseCode.CONNECTION_FORCED, e.getMessage()));
                return;
            }

            sconn.setState(Connection.State.OPEN);
            sconn.invoke(new ConnectionOpenOk(Collections.emptyList()));
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

        final NetworkConnection networkConnection = sconn.getNetworkConnection();
        if(ok.hasHeartbeat())
        {
            int heartbeat = ok.getHeartbeat();
            if(heartbeat < 0)
            {
                heartbeat = 0;
            }

            networkConnection.setMaxReadIdle(2 * heartbeat);
            networkConnection.setMaxWriteIdle(heartbeat);

        }
        else
        {
            networkConnection.setMaxReadIdle(0);
            networkConnection.setMaxWriteIdle(0);
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
        final Collection<ConsumerTarget_0_10> subs = ssn.getSubscriptions();
        for (ConsumerTarget_0_10 subscription_0_10 : subs)
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
        final Principal authorizedPrincipal = sconn.getAuthorizedPrincipal();
        final String userId = authorizedPrincipal == null ? "" : authorizedPrincipal.getName();

        final Iterator<AMQConnectionModel> connections =
                        ((ServerConnection)conn).getVirtualHost().getConnectionRegistry().getConnections().iterator();
        while(connections.hasNext())
        {
            final AMQConnectionModel amqConnectionModel = connections.next();
            final String userName = amqConnectionModel.getAuthorizedPrincipal() == null
                    ? ""
                    : amqConnectionModel.getAuthorizedPrincipal().getName();
            if (userId.equals(userName) && !amqConnectionModel.isSessionNameUnique(name))
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

    public String getClientProduct()
    {
        return _clientProperties == null ? null : (String) _clientProperties.get(ConnectionStartProperties.PRODUCT);
    }

    public String getRemoteProcessPid()
    {
        return (_clientProperties == null || _clientProperties.get(ConnectionStartProperties.PID) == null) ? null : String.valueOf(_clientProperties.get(ConnectionStartProperties.PID));
    }

    @Override
    protected int getHeartbeatMax()
    {
        int delay = (Integer)_broker.getAttribute(Broker.CONNECTION_HEART_BEAT_DELAY);
        return delay == 0 ? super.getHeartbeatMax() : delay;
    }
}
