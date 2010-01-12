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

import static org.apache.qpid.transport.Connection.State.OPEN;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.apache.qpid.security.UsernamePasswordCallbackHandler;
import org.apache.qpid.transport.util.Logger;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;


/**
 * ClientDelegate
 *
 */

public class ClientDelegate extends ConnectionDelegate
{
    private static final Logger log = Logger.get(ClientDelegate.class);

    private static final String KRB5_OID_STR = "1.2.840.113554.1.2.2";
    protected static Oid KRB5_OID;
    
    static {
        try {
            KRB5_OID = new Oid(KRB5_OID_STR);
        } catch (GSSException ignore) {}
    }
    
    private String vhost;
    private String username;
    private String password;
    private String[] saslMechs;
    private String protocol;
    private String serverName;
    
    public ClientDelegate(String vhost, String username, String password,String saslMechs)
    {
        this.vhost = vhost;
        this.username = username;
        this.password = password;
        this.saslMechs = saslMechs.split(" ");
        
        // Looks kinda of silly but the Sun SASL Kerberos client uses the 
        // protocol + servername as the service key.
        this.protocol = System.getProperty("qpid.sasl_protocol","AMQP");
        this.serverName = System.getProperty("qpid.sasl_server_name","localhost");
    }

    public void init(Connection conn, ProtocolHeader hdr)
    {
        if (!(hdr.getMajor() == 0 && hdr.getMinor() == 10))
        {
            conn.exception(new ProtocolVersionException(hdr.getMajor(), hdr.getMinor()));
        }
    }

    @Override public void connectionStart(Connection conn, ConnectionStart start)
    {
        Map<String,Object> clientProperties = new HashMap<String,Object>();
        clientProperties.put("qpid.session_flow", 1);
        clientProperties.put("qpid.client_pid",getPID());
        clientProperties.put("qpid.client_pid",clientProperties.get("qpid.client_pid"));
        clientProperties.put("qpid.client_process",
                System.getProperty("qpid.client_process","Qpid Java Client"));
        
        List<Object> mechanisms = start.getMechanisms();
        if (mechanisms == null || mechanisms.isEmpty())
        {
            conn.connectionStartOk
                (clientProperties, null, null, conn.getLocale());
            return;
        }

        String[] mechs = new String[mechanisms.size()];
        mechanisms.toArray(mechs);

        try
        {
            UsernamePasswordCallbackHandler handler =
                new UsernamePasswordCallbackHandler();
            handler.initialise(username, password);
            SaslClient sc = Sasl.createSaslClient
                (saslMechs, null, protocol, serverName, null, handler);
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

    @Override public void connectionSecure(Connection conn, ConnectionSecure secure)
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

    @Override public void connectionTune(Connection conn, ConnectionTune tune)
    {
        conn.setChannelMax(tune.getChannelMax());
        int hb_interval = calculateHeartbeatInterval(conn,
                                                     tune.getHeartbeatMin(),
                                                     tune.getHeartbeatMax()
                                                     );
        conn.connectionTuneOk(tune.getChannelMax(), 
                              tune.getMaxFrameSize(), 
                              hb_interval);
        conn.setIdleTimeout(hb_interval*1000);
        conn.connectionOpen(vhost, null, Option.INSIST);
    }

    @Override public void connectionOpenOk(Connection conn, ConnectionOpenOk ok)
    {
        SaslClient sc = conn.getSaslClient();
        if (sc.getMechanismName().equals("GSSAPI") && getUserID() != null)
        {
            conn.setUserID(getUserID());
        }
        conn.setState(OPEN);
    }

    @Override public void connectionRedirect(Connection conn, ConnectionRedirect redir)
    {
        throw new UnsupportedOperationException();
    }

    @Override public void connectionHeartbeat(Connection conn, ConnectionHeartbeat hearbeat)
    {
        conn.connectionHeartbeat();
    }

    /**
     * Currently the spec specified the min and max for heartbeat using secs
     */
    private int calculateHeartbeatInterval(Connection conn,int min, int max)
    {
        long l = conn.getIdleTimeout()/1000;
        if (l == 0)
        {
            log.warn("Idle timeout is zero. Heartbeats are disabled");
            return 0; // heartbeats are disabled.
        }
        else if (l >= min && l <= max)
        {
            return (int)l;
        }
        else
        {
            log.warn("Ignoring the idle timeout %s set by the connection," +
            		" using the brokers max value %s", l,max);
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
    
    private String getUserID()
    {
        log.debug("Obtaining userID from kerberos");
        String service = protocol + "@" + serverName;
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
