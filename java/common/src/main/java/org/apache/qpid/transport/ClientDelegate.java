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
package org.apache.qpid.transport;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import org.apache.qpid.security.UsernamePasswordCallbackHandler;
import static org.apache.qpid.transport.Connection.State.OPEN;
import static org.apache.qpid.transport.Connection.State.RESUMING;
import org.apache.qpid.transport.util.Logger;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * ClientDelegate
 *
 */

public class ClientDelegate extends ConnectionDelegate
{
    private static final Logger log = Logger.get(ClientDelegate.class);

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

    private List<String> clientMechs;
    private ConnectionSettings conSettings;

    public ClientDelegate(ConnectionSettings settings)
    {
        this.conSettings = settings;
        this.clientMechs = Arrays.asList(settings.getSaslMechs().split(" "));
    }

    public void init(Connection conn, ProtocolHeader hdr)
    {
        if (!(hdr.getMajor() == 0 && hdr.getMinor() == 10))
        {
            conn.exception(new ProtocolVersionException(hdr.getMajor(), hdr.getMinor()));
        }
    }

    @Override
    public void connectionStart(Connection conn, ConnectionStart start)
    {
        Map<String,Object> clientProperties = new HashMap<String,Object>();

        if(this.conSettings.getClientProperties() != null)
        {
            clientProperties.putAll(this.conSettings.getClientProperties());
        }

        clientProperties.put("qpid.session_flow", 1);
        clientProperties.put("qpid.client_pid",getPID());
        clientProperties.put("qpid.client_process",
                System.getProperty("qpid.client_process","Qpid Java Client"));

        List<Object> brokerMechs = start.getMechanisms();
        if (brokerMechs == null || brokerMechs.isEmpty())
        {
            conn.connectionStartOk
                (clientProperties, null, null, conn.getLocale());
            return;
        }

        List<String> choosenMechs = new ArrayList<String>();
        for (String mech:clientMechs)
        {
            if (brokerMechs.contains(mech))
            {
                choosenMechs.add(mech);
            }
        }

        if (choosenMechs.size() == 0)
        {
            conn.exception(new ConnectionException("The following SASL mechanisms " +
                    clientMechs.toString()  +
                    " specified by the client are not supported by the broker"));
            return;
        }

        String[] mechs = new String[choosenMechs.size()];
        choosenMechs.toArray(mechs);

        conn.setServerProperties(start.getServerProperties());

        try
        {
            Map<String,Object> saslProps = new HashMap<String,Object>();
            if (conSettings.isUseSASLEncryption())
            {
                saslProps.put(Sasl.QOP, "auth-conf");
            }
            UsernamePasswordCallbackHandler handler =
                new UsernamePasswordCallbackHandler();
            handler.initialise(conSettings.getUsername(), conSettings.getPassword());
            SaslClient sc = Sasl.createSaslClient
                (mechs, null, conSettings.getSaslProtocol(), conSettings.getSaslServerName(), saslProps, handler);
            conn.setSaslClient(sc);

            byte[] response = sc.hasInitialResponse() ?
                sc.evaluateChallenge(new byte[0]) : null;
            conn.connectionStartOk
                (clientProperties, sc.getMechanismName(), response,
                 conn.getLocale());
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    @Override
    public void connectionSecure(Connection conn, ConnectionSecure secure)
    {
        SaslClient sc = conn.getSaslClient();
        try
        {
            byte[] response = sc.evaluateChallenge(secure.getChallenge());
            conn.connectionSecureOk(response);
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    @Override
    public void connectionTune(Connection conn, ConnectionTune tune)
    {
        int hb_interval = calculateHeartbeatInterval(conSettings.getHeartbeatInterval(),
                                                     tune.getHeartbeatMin(),
                                                     tune.getHeartbeatMax()
                                                     );
        conn.connectionTuneOk(tune.getChannelMax(),
                              tune.getMaxFrameSize(),
                              hb_interval);
        // The idle timeout is twice the heartbeat amount (in milisecs)
        conn.setIdleTimeout(hb_interval*1000*2);

        int channelMax = tune.getChannelMax();
        //0 means no implied limit, except available server resources
        //(or that forced by protocol limitations [0xFFFF])
        conn.setChannelMax(channelMax == 0 ? Connection.MAX_CHANNEL_MAX : channelMax);

        conn.connectionOpen(conSettings.getVhost(), null, Option.INSIST);
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
        
        if (conn.isConnectionResuming())
        {
            conn.setState(RESUMING);
        }
        else
        {
            conn.setState(OPEN);
        }
    }

    @Override
    public void connectionRedirect(Connection conn, ConnectionRedirect redir)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectionHeartbeat(Connection conn, ConnectionHeartbeat hearbeat)
    {
        conn.connectionHeartbeat();
    }

    /**
     * Currently the spec specified the min and max for heartbeat using secs
     */
    private int calculateHeartbeatInterval(int heartbeat,int min, int max)
    {
        int i = heartbeat;
        if (i == 0)
        {
            log.info("Idle timeout is 0 sec. Heartbeats are disabled.");
            return 0; // heartbeats are disabled.
        }
        else if (i >= min && i <= max)
        {
            return i;
        }
        else
        {
            log.info("The broker does not support the configured connection idle timeout of %s sec," +
                     " using the brokers max supported value of %s sec instead.", i,max);
            return max;
        }
    }

    private int getPID()
    {
        RuntimeMXBean rtb = ManagementFactory.getRuntimeMXBean();
        String processName = rtb.getName();
        if (processName != null && processName.indexOf('@')>0)
        {
            try
            {
                return Integer.parseInt(processName.substring(0,processName.indexOf('@')));
            }
            catch(Exception e)
            {
                log.warn("Unable to get the client PID due to error",e);
                return -1;
            }
        }
        else
        {
            log.warn("Unable to get the client PID due to unsupported format : " + processName);
            return -1;
        }

    }

    private String getKerberosUser()
    {
        log.debug("Obtaining userID from kerberos");
        String service = conSettings.getSaslProtocol() + "@" + conSettings.getSaslServerName();
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
            log.warn("Unable to retrieve userID from Kerberos due to error",e);
        }

        return null;
    }
}
