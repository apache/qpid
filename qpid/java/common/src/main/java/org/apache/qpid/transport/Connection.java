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

import org.apache.qpid.transport.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.nio.ByteBuffer;


/**
 * Connection
 *
 * @author Rafael H. Schloming
 *
 * @todo the channels map should probably be replaced with something
 * more efficient, e.g. an array or a map implementation that can use
 * short instead of Short
 */

public class Connection
    implements Receiver<ProtocolEvent>, Sender<ProtocolEvent>
{

    private static final Logger log = Logger.get(Connection.class);

    final private Sender<ProtocolEvent> sender;
    final private ConnectionDelegate delegate;
    private int channelMax = 1;
    // want to make this final
    private int _connectionId;

    final private Map<Integer,Channel> channels = new HashMap<Integer,Channel>();

    public Connection(Sender<ProtocolEvent> sender,
                      ConnectionDelegate delegate)
    {
        this.sender = sender;
        this.delegate = delegate;
    }

    public void setConnectionId(int id)
    {
        _connectionId = id;
    }

    public int getConnectionId()
    {
        return _connectionId;
    }

    public ConnectionDelegate getConnectionDelegate()
    {
        return delegate;
    }

    public void received(ProtocolEvent event)
    {
        log.debug("RECV: [%s] %s", this, event);
        Channel channel = getChannel(event.getChannel());
        channel.received(event);
    }

    public void send(ProtocolEvent event)
    {
        log.debug("SEND: [%s] %s", this, event);
        sender.send(event);
    }

    public void flush()
    {
        log.debug("FLUSH: [%s]", this);
        sender.flush();
    }

    public int getChannelMax()
    {
        return channelMax;
    }

    void setChannelMax(int max)
    {
        channelMax = max;
    }

    public Channel getChannel()
    {
        synchronized (channels)
        {
            for (int i = 0; i < getChannelMax(); i++)
            {
                if (!channels.containsKey(i))
                {
                    return getChannel(i);
                }
            }

            throw new RuntimeException("no more channels available");
        }
    }

    public Channel getChannel(int number)
    {
        synchronized (channels)
        {
            Channel channel = channels.get(number);
            if (channel == null)
            {
                channel = new Channel(this, number, delegate.getSessionDelegate());
                channels.put(number, channel);
            }
            return channel;
        }
    }

    void removeChannel(int number)
    {
        synchronized (channels)
        {
            channels.remove(number);
        }
    }

    public void exception(Throwable t)
    {
        delegate.exception(t);
    }

    public void closed()
    {
        log.debug("connection closed: %s", this);
        synchronized (channels)
        {
            List<Channel> values = new ArrayList<Channel>(channels.values());
            for (Channel ch : values)
            {
                ch.closed();
            }
        }
        delegate.closed();
    }

    public void close()
    {
        sender.close();
    }

    public String toString()
    {
        return String.format("conn:%x", System.identityHashCode(this));
    }

}
