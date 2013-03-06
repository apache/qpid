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
package org.apache.qpid.client.transport;

import org.apache.qpid.client.HeartbeatListener;
import org.apache.qpid.transport.ConnectionHeartbeat;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import org.apache.qpid.client.security.AMQCallbackHandler;
import org.apache.qpid.client.security.CallbackHandlerRegistry;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.transport.ClientDelegate;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionOpenOk;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.Strings;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class ClientConnectionDelegate extends ClientDelegate
{
    private static final Logger LOGGER = Logger.get(ClientDelegate.class);

    private static final String KRB5_OID_STR = "1.2.840.113554.1.2.2";
    protected static final Oid KRB5_OID;

    static
    {
        Oid oid;
        try
        {
            oid = new Oid(KRB5_OID_STR);
        }
        catch (GSSException ignore)
        {
            oid = null;
        }

        KRB5_OID = oid;
    }

    private final ConnectionURL _connectionURL;
    private HeartbeatListener _heartbeatListener = HeartbeatListener.DEFAULT;

    /**
     * @param settings
     * @param connectionURL
     */
    public ClientConnectionDelegate(ConnectionSettings settings, ConnectionURL connectionURL)
    {
        super(settings);
        this._connectionURL = connectionURL;
    }

    @Override
    protected SaslClient createSaslClient(List<Object> brokerMechs) throws ConnectionException, SaslException
    {
        final String brokerMechanisms = Strings.join(" ", brokerMechs);
        final String restrictionList = getConnectionSettings().getSaslMechs();
        final String selectedMech = CallbackHandlerRegistry.getInstance().selectMechanism(brokerMechanisms, restrictionList);
        if (selectedMech == null)
        {
            throw new ConnectionException("Client and broker have no SASL mechanisms in common." +
                    " Broker allows : " + brokerMechanisms +
                    " Client has : " + CallbackHandlerRegistry.getInstance().getMechanisms()  +
                    " Client restricted itself to : " + (restrictionList != null ? restrictionList : "no restriction"));
        }

        if (CallbackHandlerRegistry.getInstance().isUserPassRequired(selectedMech))
        {
            throw new ConnectionException("Username and Password is required for the selected mechanism : " + selectedMech +
                    " Broker allows : " + brokerMechanisms +
                    " Client has : " + CallbackHandlerRegistry.getInstance().getMechanisms()  +
                    " Client restricted itself to : " + (restrictionList != null ? restrictionList : "no restriction"));
        }

        Map<String,Object> saslProps = new HashMap<String,Object>();
        if (getConnectionSettings().isUseSASLEncryption())
        {
            saslProps.put(Sasl.QOP, "auth-conf");
        }

        final AMQCallbackHandler handler = CallbackHandlerRegistry.getInstance().createCallbackHandler(selectedMech);
        handler.initialise(_connectionURL);
        final SaslClient sc = Sasl.createSaslClient(new String[] {selectedMech}, null, getConnectionSettings().getSaslProtocol(), getConnectionSettings().getSaslServerName(), saslProps, handler);

        return sc;
    }

    @Override
    public void connectionOpenOk(Connection conn, ConnectionOpenOk ok)
    {
        SaslClient sc = conn.getSaslClient();
        if (sc != null)
        {
            if (sc.getMechanismName().equals("GSSAPI"))
            {
                String id = getKerberosUser();
                if (id != null)
                {
                    conn.setUserID(id);
                }
            }
            else if (sc.getMechanismName().equals("EXTERNAL"))
            {
                if (conn.getSecurityLayer() != null)
                {
                    conn.setUserID(conn.getSecurityLayer().getUserID());
                }
            }
        }

        super.connectionOpenOk(conn, ok);
    }

    private String getKerberosUser()
    {
        LOGGER.debug("Obtaining userID from kerberos");
        String service = getConnectionSettings().getSaslProtocol() + "@" + getConnectionSettings().getSaslServerName();
        GSSManager manager = GSSManager.getInstance();

        try
        {
            GSSName acceptorName = manager.createName(service,
                GSSName.NT_HOSTBASED_SERVICE, KRB5_OID);

            GSSContext secCtx = manager.createContext(acceptorName,
                                                      KRB5_OID,
                                                      null,
                                                      GSSContext.INDEFINITE_LIFETIME);

            secCtx.initSecContext(new byte[0], 0, 1);

            if (secCtx.getSrcName() != null)
            {
                return secCtx.getSrcName().toString();
            }

        }
        catch (GSSException e)
        {
            LOGGER.warn("Unable to retrieve userID from Kerberos due to error",e);
        }

        return null;
    }

    @Override
    public void connectionHeartbeat(Connection conn, ConnectionHeartbeat hearbeat)
    {
        // ClientDelegate simply responds to heartbeats with heartbeats
        _heartbeatListener.heartbeatReceived();
        super.connectionHeartbeat(conn, hearbeat);
        _heartbeatListener.heartbeatSent();
    }


    public void setHeartbeatListener(HeartbeatListener listener)
    {
        _heartbeatListener = listener == null ? HeartbeatListener.DEFAULT : listener;
    }
}
