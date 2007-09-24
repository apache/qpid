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

import org.apache.qpidity.SecurityHelper;
import org.apache.qpidity.QpidException;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
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

    public void setCondition(Lock negotiationCompleteLock,Condition negotiationComplete)
    {
        _negotiationComplete = negotiationComplete;
        _negotiationCompleteLock = negotiationCompleteLock;
    }

    public void init(Channel ch, ProtocolHeader hdr)
    {
        System.out.println(hdr);
        // XXX: hardcoded version
        if (hdr.getMajor() != 0 && hdr.getMinor() != 10)
        {
            // XXX
            ch.getConnection().send(new ConnectionEvent(0, new ProtocolHeader(1, 0, 10)));
            ch.getConnection().close();
        }
        else
        {

            System.out.println("\n--------------------Broker Start Connection Negotiation -----------------------\n");

            ch.connectionStart(hdr.getMajor(), hdr.getMinor(), null, "PLAIN", "utf8");
        }
    }

    // ----------------------------------------------
    //           Client side
    //-----------------------------------------------
    @Override public void connectionStart(Channel context, ConnectionStart struct)
    {
        System.out.println("\n--------------------Client Start Connection Negotiation -----------------------\n");
        System.out.println("The broker has sent connection-start");

        String mechanism = null;
        String response = null;
        try
        {
            mechanism = SecurityHelper.chooseMechanism(struct.getMechanisms());
            saslClient = Sasl.createSaslClient(new String[]{ mechanism },null, "AMQP", "localhost", null,
                                                  SecurityHelper.createCallbackHandler(mechanism,_username,_password ));
            response = new String(saslClient.evaluateChallenge(new byte[0]),_locale);
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
        System.out.println("The broker has sent connection-secure with chanllenge " + struct.getChallenge());

        try
        {
            String response = new String(saslClient.evaluateChallenge(struct.getChallenge().getBytes()),_locale);
            context.connectionSecureOk(response);
        }
        catch (UnsupportedEncodingException e)
        {
           // need error handling
        }
        catch (SaslException e)
        {
          // need error handling
        }
    }

    @Override public void connectionTune(Channel context, ConnectionTune struct)
    {
        System.out.println("The broker has sent connection-tune " + struct.toString());

        // should update the channel max given by the broker.
        context.connectionTuneOk(struct.getChannelMax(), struct.getFrameMax(), struct.getHeartbeat());
        context.connectionOpen(_virtualHost, null, Option.INSIST);
    }


    @Override public void connectionOpenOk(Channel context, ConnectionOpenOk struct)
    {
        String knownHosts = struct.getKnownHosts();
        System.out.println("The broker has opened the connection for use");
        System.out.println("The broker supplied the following hosts for failover " + knownHosts);
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
        System.out.println("\n-------------------- Client End Connection Negotiation -----------------------\n");
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

        System.out.println("The client has sent connection-start-ok");

        //try
        //{
            //saslServer = Sasl.createSaslServer(_mechanism, "AMQP", "ABC",null,SecurityHelper.createCallbackHandler(_mechanism,_username,_password));
            //byte[] challenge = saslServer.evaluateResponse(struct.getResponse().getBytes());
            byte[] challenge = null;
            if ( challenge == null)
            {
                System.out.println("Authentication sucessfull");
                context.connectionTune(Integer.MAX_VALUE,maxFrame, 0);
            }
            else
            {
                System.out.println("Authentication failed");
                try
                {
                    context.connectionSecure(new String(challenge,_locale));
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

    @Override public void connectionTuneOk(Channel context, ConnectionTuneOk struct)
    {
        System.out.println("The client has excepted the tune params");
    }

    @Override public void connectionSecureOk(Channel context, ConnectionSecureOk struct)
    {
        System.out.println("The client has sent connection-secure-ok");
        try
        {
            saslServer = Sasl.createSaslServer(_mechanism, "AMQP", "ABC",new HashMap(),SecurityHelper.createCallbackHandler(_mechanism,_username,_password));
            byte[] challenge = saslServer.evaluateResponse(struct.getResponse().getBytes());
            if ( challenge == null)
            {
                System.out.println("Authentication sucessfull");
                context.connectionTune(Integer.MAX_VALUE,maxFrame, 0);
            }
            else
            {
                System.out.println("Authentication failed");
                try
                {
                    context.connectionSecure(new String(challenge,_locale));
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
       String hosts = "amqp:1223243232325";
       System.out.println("The client has sent connection-open");
       context.connectionOpenOk(hosts);
       System.out.println("\n-------------------- Broker End Connection Negotiation -----------------------\n");
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
}
