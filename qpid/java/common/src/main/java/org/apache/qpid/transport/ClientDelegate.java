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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import java.io.UnsupportedEncodingException;

import org.apache.qpid.QpidException;

import org.apache.qpid.security.UsernamePasswordCallbackHandler;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;


import static org.apache.qpid.transport.Connection.State.*;


/**
 * ClientDelegate
 *
 */

public class ClientDelegate extends ConnectionDelegate
{

    private String vhost;
    private String username;
    private String password;

    public ClientDelegate(String vhost, String username, String password)
    {
        this.vhost = vhost;
        this.username = username;
        this.password = password;
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
        List<Object> mechanisms = start.getMechanisms();
        if (mechanisms == null || mechanisms.isEmpty())
        {
            conn.connectionStartOk
                (Collections.EMPTY_MAP, null, null, conn.getLocale());
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
                (new String[] {"PLAIN"}, null, "AMQP", "localhost", null, handler);
            conn.setSaslClient(sc);

            byte[] response = sc.hasInitialResponse() ?
                sc.evaluateChallenge(new byte[0]) : null;
            conn.connectionStartOk
                (Collections.EMPTY_MAP, sc.getMechanismName(), response,
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
        conn.connectionTuneOk(tune.getChannelMax(), tune.getMaxFrameSize(), tune.getHeartbeatMax());
        conn.connectionOpen(vhost, null, Option.INSIST);
    }

    @Override public void connectionOpenOk(Connection conn, ConnectionOpenOk ok)
    {
        conn.setState(OPEN);
    }

    @Override public void connectionRedirect(Connection conn, ConnectionRedirect redir)
    {
        throw new UnsupportedOperationException();
    }

}
