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
package org.apache.qpid.client.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.BasicMessageConsumer;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.framing.BasicReturnBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MethodDispatcher;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

/**
 * This class contains everything needed to process a JMS message. It assembles the deliver body, the content header and
 * the content body/ies.
 *
 * Note that the actual work of creating a JMS message for the client code's use is done outside of the MINA dispatcher
 * thread in order to minimise the amount of work done in the MINA dispatcher thread.
 */
public abstract class UnprocessedMessage
{
    private long _bytesReceived = 0L;

    private ContentHeaderBody _contentHeader;

    /** List of ContentBody instances. Due to fragmentation you don't know how big this will be in general */
    private List<ContentBody> _bodies;

    public void receiveBody(ContentBody body) //throws UnexpectedBodyReceivedException
    {

        if (body.payload != null)
        {
            final long payloadSize = body.payload.remaining();

            if (_bodies == null)
            {
                if (payloadSize == getContentHeader().bodySize)
                {
                    _bodies = Collections.singletonList(body);
                }
                else
                {
                    _bodies = new ArrayList<ContentBody>();
                    _bodies.add(body);
                }

            }
            else
            {
                _bodies.add(body);
            }
            _bytesReceived += payloadSize;
        }
    }

    public boolean isAllBodyDataReceived()
    {
        return _bytesReceived == getContentHeader().bodySize;
    }



    abstract public BasicDeliverBody getDeliverBody();

    abstract public BasicReturnBody getBounceBody();

    public ContentHeaderBody getContentHeader()
    {
        return _contentHeader;
    }

    public void setContentHeader(ContentHeaderBody contentHeader)
    {
        this._contentHeader = contentHeader;
    }

    public List<ContentBody> getBodies()
    {
        return _bodies;
    }

    abstract public boolean isDeliverMessage();

    public static final class UnprocessedDeliverMessage extends UnprocessedMessage
    {
        private final BasicDeliverBody _body;

        public UnprocessedDeliverMessage(final BasicDeliverBody body)
        {
            _body = body;
        }


        public BasicDeliverBody getDeliverBody()
        {
            return _body;
        }

        public BasicReturnBody getBounceBody()
        {
            return null;
        }

        public boolean isDeliverMessage()
        {
            return true;
        }
    }

    public static final class UnprocessedBouncedMessage extends UnprocessedMessage
    {
        private final BasicReturnBody _body;

        public UnprocessedBouncedMessage(final BasicReturnBody body)
        {
            _body = body;
        }


        public BasicDeliverBody getDeliverBody()
        {
            return null;
        }

        public BasicReturnBody getBounceBody()
        {
            return _body;
        }

        public boolean isDeliverMessage()
        {
            return false;
        }
    }

    public static final class CloseConsumerMessage extends UnprocessedMessage
    {
        BasicMessageConsumer _consumer;

        public CloseConsumerMessage(BasicMessageConsumer consumer)
        {
            _consumer = consumer;
        }

        public BasicDeliverBody getDeliverBody()
        {
            return new BasicDeliverBody()
            {
                // This is the only thing we need to preserve so the correct consumer can be found later.
                public AMQShortString getConsumerTag()
                {
                    return _consumer.getConsumerTag();
                }

                // The Rest of these methods are not used
                public long getDeliveryTag()
                {
                    return 0;
                }

                public AMQShortString getExchange()
                {
                    return null;
                }

                public boolean getRedelivered()
                {
                    return false;
                }

                public AMQShortString getRoutingKey()
                {
                    return null;
                }

                public byte getMajor()
                {
                    return 0;
                }

                public byte getMinor()
                {
                    return 0;
                }

                public int getClazz()
                {
                    return 0;
                }

                public int getMethod()
                {
                    return 0;
                }

                public void writeMethodPayload(ByteBuffer buffer)
                {
                }

                public byte getFrameType()
                {
                    return 0;
                }

                public int getSize()
                {
                    return 0;
                }

                public void writePayload(ByteBuffer buffer)
                {
                }

                public void handle(int channelId, AMQVersionAwareProtocolSession amqMinaProtocolSession) throws AMQException
                {
                }

                public AMQFrame generateFrame(int channelId)
                {
                    return null;
                }

                public AMQChannelException getChannelNotFoundException(int channelId)
                {
                    return null;
                }

                public AMQChannelException getChannelException(AMQConstant code, String message)
                {
                    return null;
                }

                public AMQChannelException getChannelException(AMQConstant code, String message, Throwable cause)
                {
                    return null;
                }

                public AMQConnectionException getConnectionException(AMQConstant code, String message)
                {
                    return null;
                }

                public AMQConnectionException getConnectionException(AMQConstant code, String message, Throwable cause)
                {
                    return null;
                }

                public boolean execute(MethodDispatcher methodDispatcher, int channelId) throws AMQException
                {
                    return false;
                }
            };
        }

        public BasicReturnBody getBounceBody()
        {
            return null;
        }

        public boolean isDeliverMessage()
        {
            return false;
        }
    }

}
