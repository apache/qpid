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

import java.util.Collections;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import java.io.UnsupportedEncodingException;

import org.apache.qpid.QpidException;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;


import static org.apache.qpid.transport.Connection.State.*;


/**
 * ServerDelegate
 *
 */

public class ServerDelegate extends ConnectionDelegate
{

    private SaslServer saslServer;

    public void init(Connection conn, ProtocolHeader hdr)
    {
        conn.send(new ProtocolHeader(1, 0, 10));
        List<Object> utf8 = new ArrayList<Object>();
        utf8.add("utf8");
        conn.connectionStart(null, Collections.EMPTY_LIST, utf8);
    }

    @Override public void connectionStartOk(Connection conn, ConnectionStartOk ok)
    {
        conn.setLocale(ok.getLocale());
        String mechanism = ok.getMechanism();

        if (mechanism == null || mechanism.length() == 0)
        {
            conn.connectionTune
                (Integer.MAX_VALUE,
                 org.apache.qpid.transport.network.ConnectionBinding.MAX_FRAME_SIZE,
                 0, Integer.MAX_VALUE);
            return;
        }

        try
        {
            SaslServer ss = Sasl.createSaslServer
                (mechanism, "AMQP", "localhost", null, null);
            if (ss == null)
            {
                conn.connectionClose
                    (ConnectionCloseCode.CONNECTION_FORCED,
                     "null SASL mechanism: " + mechanism);
                return;
            }
            conn.setSaslServer(ss);
            secure(conn, ok.getResponse());
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    private void secure(Connection conn, byte[] response)
    {
        SaslServer ss = conn.getSaslServer();
        try
        {
            byte[] challenge = ss.evaluateResponse(response);
            if (ss.isComplete())
            {
                ss.dispose();
                conn.connectionTune
                    (Integer.MAX_VALUE,
                     org.apache.qpid.transport.network.ConnectionBinding.MAX_FRAME_SIZE,
                     0, Integer.MAX_VALUE);
            }
            else
            {
                conn.connectionSecure(challenge);
            }
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    @Override public void connectionSecureOk(Connection conn, ConnectionSecureOk ok)
    {
        secure(conn, ok.getResponse());
    }

    @Override public void connectionTuneOk(Connection conn, ConnectionTuneOk ok)
    {
        
    }

    @Override public void connectionOpen(Connection conn, ConnectionOpen open)
    {
        conn.connectionOpenOk(Collections.EMPTY_LIST);
        conn.setState(OPEN);
    }

    public Session getSession(Connection conn, SessionAttach atc)
    {
        return new Session(conn, new Binary(atc.getName()), 0);
    }

    @Override public void sessionAttach(Connection conn, SessionAttach atc)
    {
        Session ssn = getSession(conn, atc);
        conn.map(ssn, atc.getChannel());
        ssn.sessionAttached(atc.getName());
        ssn.setState(Session.State.OPEN);
    }

}
