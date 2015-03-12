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
package org.apache.qpid.server.protocol.v0_8;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.network.NetworkConnection;

public class InternalTestProtocolSession extends AMQProtocolEngine implements ProtocolOutputConverter
{
    private static final Logger _logger = Logger.getLogger(InternalTestProtocolSession.class);
    // ChannelID(LIST)  -> LinkedList<Pair>
    private final Map<Integer, Map<String, LinkedList<DeliveryPair>>> _channelDelivers;
    private AtomicInteger _deliveryCount = new AtomicInteger(0);
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    public InternalTestProtocolSession(VirtualHostImpl virtualHost, Broker<?> broker, final AmqpPort<?> port) throws AMQException
    {
        super(broker, new TestNetworkConnection(), ID_GENERATOR.getAndIncrement(), port, null);

        _channelDelivers = new HashMap<Integer, Map<String, LinkedList<DeliveryPair>>>();

        setTestAuthorizedSubject();
        setVirtualHost(virtualHost);
    }

    private void setTestAuthorizedSubject()
    {
        Principal principal = new AuthenticatedPrincipal(new UsernamePrincipal("InternalTestProtocolSession"));
        Subject authorizedSubject = new Subject(
                true,
                Collections.singleton(principal),
                Collections.emptySet(),
                Collections.emptySet());

        setAuthorizedSubject(authorizedSubject);
    }

    public ProtocolOutputConverter getProtocolOutputConverter()
    {
        return this;
    }

    public byte getProtocolMajorVersion()
    {
        return (byte) 8;
    }

    public void writeReturn(MessagePublishInfo messagePublishInfo,
                            ContentHeaderBody header,
                            MessageContentSource msgContent,
                            int channelId,
                            int replyCode,
                            AMQShortString replyText)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public byte getProtocolMinorVersion()
    {
        return (byte) 0;
    }

    // ***

    public List<DeliveryPair> getDelivers(int channelId, AMQShortString consumerTag, int count)
    {
        synchronized (_channelDelivers)
        {
            List<DeliveryPair> all =_channelDelivers.get(channelId).get(AMQShortString.toString(consumerTag));

            if (all == null)
            {
                return new ArrayList<DeliveryPair>(0);
            }

            List<DeliveryPair> msgs = all.subList(0, count);

            List<DeliveryPair> response = new ArrayList<DeliveryPair>(msgs);

            //Remove the msgs from the receivedList.
            msgs.clear();

            return response;
        }
    }

    public ClientDeliveryMethod createDeliveryMethod(int channelId)
    {
        return new InternalWriteDeliverMethod(channelId);
    }

    public void confirmConsumerAutoClose(int channelId, AMQShortString consumerTag)
    {
    }

    public long writeDeliver(final ServerMessage msg,
                             final InstanceProperties props, int channelId,
                             long deliveryTag,
                             AMQShortString consumerTag)
    {
        _deliveryCount.incrementAndGet();
        long size = msg.getSize();
        synchronized (_channelDelivers)
        {
            Map<String, LinkedList<DeliveryPair>> consumers = _channelDelivers.get(channelId);

            if (consumers == null)
            {
                consumers = new HashMap<String, LinkedList<DeliveryPair>>();
                _channelDelivers.put(channelId, consumers);
            }

            LinkedList<DeliveryPair> consumerDelivers = consumers.get(AMQShortString.toString(consumerTag));

            if (consumerDelivers == null)
            {
                consumerDelivers = new LinkedList<DeliveryPair>();
                consumers.put(consumerTag.toString(), consumerDelivers);
            }

            consumerDelivers.add(new DeliveryPair(deliveryTag, msg));
        }
        return size;
    }

    public long writeGetOk(final ServerMessage msg,
                           final InstanceProperties props,
                           int channelId,
                           long deliveryTag,
                           int queueSize)
    {
        return msg.getSize();
    }

    public void awaitDelivery(int msgs)
    {
        while (msgs > _deliveryCount.get())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                _logger.error("Thread interrupted", e);
            }
        }
    }

    public class DeliveryPair
    {
        private long _deliveryTag;
        private ServerMessage _message;

        public DeliveryPair(long deliveryTag, ServerMessage message)
        {
            _deliveryTag = deliveryTag;
            _message = message;
        }

        public ServerMessage getMessage()
        {
            return _message;
        }

        public long getDeliveryTag()
        {
            return _deliveryTag;
        }
    }

    public void closeProtocolSession()
    {
        // Override as we don't have a real IOSession to close.
        //  The alternative is to fully implement the TestIOSession to return a CloseFuture from close();
        //  Then the AMQMinaProtocolSession can join on the returning future without a NPE.
    }

    private class InternalWriteDeliverMethod implements ClientDeliveryMethod
    {
        private int _channelId;

        public InternalWriteDeliverMethod(int channelId)
        {
            _channelId = channelId;
        }


        @Override
        public long deliverToClient(ConsumerImpl sub, ServerMessage message,
                                    InstanceProperties props, long deliveryTag)
        {
            _deliveryCount.incrementAndGet();
            long size = message.getSize();
            synchronized (_channelDelivers)
            {
                Map<String, LinkedList<DeliveryPair>> consumers = _channelDelivers.get(_channelId);

                if (consumers == null)
                {
                    consumers = new HashMap<String, LinkedList<DeliveryPair>>();
                    _channelDelivers.put(_channelId, consumers);
                }

                LinkedList<DeliveryPair> consumerDelivers = consumers.get(sub.getName());

                if (consumerDelivers == null)
                {
                    consumerDelivers = new LinkedList<DeliveryPair>();
                    consumers.put(sub.getName(), consumerDelivers);
                }

                consumerDelivers.add(new DeliveryPair(deliveryTag, message));
            }
            return size;
        }
    }

    void assertState(final ConnectionState requiredState)
    {
        // no-op
    }


    private static final AtomicInteger portNumber = new AtomicInteger(0);
    
    private static class TestNetworkConnection implements NetworkConnection
    {
        private String _remoteHost = "127.0.0.1";
        private String _localHost = "127.0.0.1";
        private int _port = portNumber.incrementAndGet();
        private final ByteBufferSender _sender;

        public TestNetworkConnection()
        {
            _sender = new ByteBufferSender()
            {
                public void send(ByteBuffer msg)
                {
                }

                public void flush()
                {
                }

                public void close()
                {
                }
            };
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return new InetSocketAddress(_localHost, _port);
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return new InetSocketAddress(_remoteHost, _port);
        }

        @Override
        public void setMaxReadIdle(int idleTime)
        {
        }

        @Override
        public Principal getPeerPrincipal()
        {
            return null;
        }

        @Override
        public int getMaxReadIdle()
        {
            return 0;
        }

        @Override
        public int getMaxWriteIdle()
        {
            return 0;
        }

        @Override
        public void setMaxWriteIdle(int idleTime)
        {
        }

        @Override
        public void close()
        {
        }

        @Override
        public ByteBufferSender getSender()
        {
            return _sender;
        }

        @Override
        public void start()
        {
        }
    }
}
