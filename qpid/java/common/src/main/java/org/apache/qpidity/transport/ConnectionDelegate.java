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
package org.apache.qpidity.transport;

import org.apache.qpidity.transport.util.Logger;

import org.apache.qpidity.SecurityHelper;
import org.apache.qpidity.QpidException;

import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;


/**
 * ConnectionDelegate
 *
 * @author Rafael H. Schloming
 */

/**
 * Currently only implemented client specific methods
 * the server specific methods are dummy impls for testing
 *
 * the connectionClose is kind of different for both sides
 */
public abstract class ConnectionDelegate extends MethodDelegate<Channel>
{

    private static final Logger log = Logger.get(ConnectionDelegate.class);

    private String _username = "guest";
    private String _password = "guest";;
    private String _mechanism;
    private String _virtualHost;
    private SaslClient saslClient;
    private SaslServer saslServer;
    private String _locale = "utf8";
    private int maxFrame = 64*1024;
    private Condition _negotiationComplete;
    private Lock _negotiationCompleteLock;

    public abstract SessionDelegate getSessionDelegate();

    public abstract void exception(Throwable t);

    public abstract void closed();

    public void setCondition(Lock negotiationCompleteLock,Condition negotiationComplete)
    {
        _negotiationComplete = negotiationComplete;
        _negotiationCompleteLock = negotiationCompleteLock;
    }

    public void init(Channel ch, ProtocolHeader hdr)
    {
        ch.getConnection().send(new ConnectionEvent(0, new ProtocolHeader
                                                    (1,
                                                     TransportConstants.getVersionMajor(),
                                                     TransportConstants.getVersionMinor())));
        if (hdr.getMajor() != TransportConstants.getVersionMajor() &&
            hdr.getMinor() != TransportConstants.getVersionMinor())
        {
            // XXX
            ch.getConnection().send(new ConnectionEvent(0, new ProtocolHeader
                                                        (1,
                                                         TransportConstants.getVersionMajor(),
                                                         TransportConstants.getVersionMinor())));
            ch.getConnection().close();
        }
        else
        {
            List<Object> plain = new ArrayList<Object>();
            plain.add("PLAIN");
            List<Object> utf8 = new ArrayList<Object>();
            utf8.add("utf8");
            ch.connectionStart(null, plain, utf8);
        }
    }

    // ----------------------------------------------
    //           Client side
    //-----------------------------------------------
    @Override public void connectionStart(Channel context, ConnectionStart struct)
    {
        String mechanism = null;
        byte[] response = null;
        try
        {
            mechanism = SecurityHelper.chooseMechanism(struct.getMechanisms());
            saslClient = Sasl.createSaslClient(new String[]{ mechanism },null, "AMQP", "localhost", null,
                                                  SecurityHelper.createCallbackHandler(mechanism,_username,_password ));
            response = saslClient.evaluateChallenge(new byte[0]);
        }
        catch (UnsupportedEncodingException e)
        {
           // need error handling
        }
        catch (SaslException e)
        {
          // need error handling
        }
        catch (QpidException e)
        {
          //  need error handling
        }

        Map<String,Object> props = new HashMap<String,Object>();
        context.connectionStartOk(props, mechanism, response, _locale);
    }

    @Override public void connectionSecure(Channel context, ConnectionSecure struct)
    {
        try
        {
            byte[] response = saslClient.evaluateChallenge(struct.getChallenge());
            context.connectionSecureOk(response);
        }
        catch (SaslException e)
        {
          // need error handling
        }
    }

    @Override public void connectionTune(Channel context, ConnectionTune struct)
    {
        context.getConnection().setChannelMax(struct.getChannelMax());
        context.connectionTuneOk(struct.getChannelMax(), struct.getMaxFrameSize(), struct.getHeartbeatMax());
        context.connectionOpen(_virtualHost, null, Option.INSIST);
    }


    @Override public void connectionOpenOk(Channel context, ConnectionOpenOk struct)
    {
        List<Object> knownHosts = struct.getKnownHosts();
        if(_negotiationCompleteLock != null)
        {
            _negotiationCompleteLock.lock();
            try
            {
                _negotiationComplete.signalAll();
            }
            finally
            {
                _negotiationCompleteLock.unlock();
            }
        }
    }

    public void connectionRedirect(Channel context, ConnectionRedirect struct)
    {
        // not going to bother at the moment
    }

    //  ----------------------------------------------
    //           Server side
    //-----------------------------------------------
    @Override public void connectionStartOk(Channel context, ConnectionStartOk struct)
    {
        //set the client side locale on the server side
        _locale = struct.getLocale();
        _mechanism = struct.getMechanism();

        //try
        //{
            //saslServer = Sasl.createSaslServer(_mechanism, "AMQP", "ABC",null,SecurityHelper.createCallbackHandler(_mechanism,_username,_password));
            //byte[] challenge = saslServer.evaluateResponse(struct.getResponse().getBytes());
            byte[] challenge = null;
            if ( challenge == null)
            {
                context.connectionTune(Integer.MAX_VALUE, maxFrame, 0, Integer.MAX_VALUE);
            }
            else
            {
                try
                {
                    context.connectionSecure(challenge);
                }
                catch(Exception e)
                {

                }
            }


        /*}
        catch (SaslException e)
        {
          // need error handling
        }
        catch (QpidException e)
        {
          //  need error handling
        }*/
    }

    @Override public void connectionSecureOk(Channel context, ConnectionSecureOk struct)
    {
        try
        {
            saslServer = Sasl.createSaslServer(_mechanism, "AMQP", "ABC",new HashMap(),SecurityHelper.createCallbackHandler(_mechanism,_username,_password));
            byte[] challenge = saslServer.evaluateResponse(struct.getResponse());
            if ( challenge == null)
            {
                context.connectionTune(Integer.MAX_VALUE, maxFrame, 0, Integer.MAX_VALUE);
            }
            else
            {
                try
                {
                    context.connectionSecure(challenge);
                }
                catch(Exception e)
                {

                }
            }


        }
        catch (SaslException e)
        {
          // need error handling
        }
        catch (QpidException e)
        {
          //  need error handling
        }
    }


    @Override public void connectionOpen(Channel context, ConnectionOpen struct)
    {
        List<Object> hosts = new ArrayList<Object>();
        hosts.add("amqp:1223243232325");
        context.connectionOpenOk(hosts);
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(String host)
    {
        _virtualHost = host;
    }

    public String getUnsupportedProtocol()
    {
        return null;
    }
}
