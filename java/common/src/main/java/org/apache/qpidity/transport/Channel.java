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

import java.nio.ByteBuffer;

import java.util.List;
import java.util.ArrayList;


import static org.apache.qpidity.transport.network.Frame.*;
import static org.apache.qpidity.transport.util.Functions.*;


/**
 * Channel
 *
 * @author Rafael H. Schloming
 */

public class Channel extends Invoker
    implements Receiver<ProtocolEvent>, ProtocolDelegate<Void>
{

    private static final Logger log = Logger.get(Channel.class);

    final private Connection connection;
    final private int channel;
    final private MethodDelegate<Channel> delegate;
    final private SessionDelegate sessionDelegate;
    // session may be null
    private Session session;

    private boolean first = true;
    private ByteBuffer data = null;

    public Channel(Connection connection, int channel, SessionDelegate delegate)
    {
        this.connection = connection;
        this.channel = channel;
        this.delegate = new ChannelDelegate();
        this.sessionDelegate = delegate;
    }

    public Connection getConnection()
    {
        return connection;
    }

    public void received(ProtocolEvent event)
    {
        event.delegate(null, this);
    }

    public void init(Void v, ProtocolHeader hdr)
    {
        connection.getConnectionDelegate().init(this, hdr);
    }

    public void method(Void v, Method method)
    {
        switch (method.getEncodedTrack())
        {
        case L1:
            method.dispatch(this, connection.getConnectionDelegate());
            break;
        case L2:
            method.dispatch(this, delegate);
            break;
        case L3:
            method.delegate(session, sessionDelegate);
            break;
        case L4:
            method.delegate(session, sessionDelegate);
            break;
        default:
            throw new IllegalStateException
                ("unknown track: " + method.getEncodedTrack());
        }
    }

    public void header(Void v, Header header)
    {
        header.delegate(session, sessionDelegate);
    }

    public void data(Void v, Data data)
    {
        data.delegate(session, sessionDelegate);
    }

    public void error(Void v, ProtocolError error)
    {
        throw new RuntimeException(error.getMessage());
    }

    public void exception(Throwable t)
    {
        session.exception(t);
    }

    public void closed()
    {
        log.debug("channel closed: ", this);
        if (session != null)
        {
            session.closed();
        }
    }

    public void close()
    {
        connection.removeChannel(channel);
    }

    public int getEncodedChannel() {
        return channel;
    }

    public Session getSession()
    {
        return session;
    }

    void setSession(Session session)
    {
        this.session = session;
    }

    private void emit(ProtocolEvent event)
    {
        connection.send(new ConnectionEvent(channel, event));
    }

    public void method(Method m)
    {
        emit(m);
    }

    public void header(Header header)
    {
        emit(header);
    }

    public void data(ByteBuffer buf)
    {
        if (data != null)
        {
            emit(new Data(data, first, false));
            first = false;
        }

        data = buf;
    }

    public void data(String str)
    {
        data(str.getBytes());
    }

    public void data(byte[] bytes)
    {
        data(ByteBuffer.wrap(bytes));
    }

    public void end()
    {
        emit(new Data(data, first, true));
        first = true;
        data = null;
    }

    protected void invoke(Method m)
    {
        method(m);
    }

    protected <T> Future<T> invoke(Method m, Class<T> cls)
    {
        throw new UnsupportedOperationException();
    }

}
