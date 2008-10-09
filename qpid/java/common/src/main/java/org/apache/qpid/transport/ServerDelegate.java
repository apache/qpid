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

import org.apache.qpid.SecurityHelper;

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

    public void init(Channel ch, ProtocolHeader hdr)
    {
        Connection conn = ch.getConnection();
        conn.send(new ProtocolHeader(1, 0, 10));
        List<Object> utf8 = new ArrayList<Object>();
        utf8.add("utf8");
        ch.connectionStart(null, Collections.EMPTY_LIST, utf8);
    }

    @Override public void connectionStartOk(Channel ch, ConnectionStartOk ok)
    {
        Connection conn = ch.getConnection();
        conn.setLocale(ok.getLocale());
        String mechanism = ok.getMechanism();

        if (mechanism == null || mechanism.length() == 0)
        {
            ch.connectionTune
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
                ch.connectionClose
                    (ConnectionCloseCode.CONNECTION_FORCED,
                     "null SASL mechanism: " + mechanism);
                return;
            }
            conn.setSaslServer(ss);
            secure(ch, ok.getResponse());
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    private void secure(Channel ch, byte[] response)
    {
        Connection conn = ch.getConnection();
        SaslServer ss = conn.getSaslServer();
        try
        {
            byte[] challenge = ss.evaluateResponse(response);
            if (ss.isComplete())
            {
                ss.dispose();
                ch.connectionTune
                    (Integer.MAX_VALUE,
                     org.apache.qpid.transport.network.ConnectionBinding.MAX_FRAME_SIZE,
                     0, Integer.MAX_VALUE);
            }
            else
            {
                ch.connectionSecure(challenge);
            }
        }
        catch (SaslException e)
        {
            conn.exception(e);
        }
    }

    @Override public void connectionSecureOk(Channel ch, ConnectionSecureOk ok)
    {
        secure(ch, ok.getResponse());
    }

    @Override public void connectionTuneOk(Channel ch, ConnectionTuneOk ok)
    {
        
    }

    @Override public void connectionOpen(Channel ch, ConnectionOpen open)
    {
        Connection conn = ch.getConnection();
        ch.connectionOpenOk(Collections.EMPTY_LIST);
        conn.setState(OPEN);
    }

}
