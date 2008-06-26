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

import java.util.List;

import org.apache.qpid.framing.AMQShortString;
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
public abstract class UnprocessedMessage<H,B>
{
    private final int _channelId;
    private final long _deliveryId;
    private final AMQShortString _consumerTag;
    protected AMQShortString _exchange;
    protected AMQShortString _routingKey;
    protected boolean _redelivered;

    public UnprocessedMessage(int channelId,long deliveryId,AMQShortString consumerTag,AMQShortString exchange,AMQShortString routingKey,boolean redelivered)
    {
        _channelId = channelId;
        _deliveryId = deliveryId;
        _consumerTag = consumerTag;
        _exchange = exchange;
        _routingKey = routingKey;
        _redelivered = redelivered;
    }

    public abstract void receiveBody(B nativeMessageBody);

    public abstract void setContentHeader(H nativeMessageHeader);

    public int getChannelId()
    {
        return _channelId;
    }

    public long getDeliveryTag()
    {
        return _deliveryId;
    }

    public AMQShortString getConsumerTag()
    {
        return _consumerTag;
    }

    public AMQShortString getExchange()
    {
        return _exchange;
    }

    public AMQShortString getRoutingKey()
    {
        return _routingKey;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }
    public abstract List<B> getBodies();
  
    public abstract H getContentHeader();
 
    // specific to 0_10
    public String getReplyToURL()
    {
        return "";
    }    
    
    public static final class CloseConsumerMessage extends UnprocessedMessage
    {
        AMQShortString _consumerTag;

        public CloseConsumerMessage(int channelId, long deliveryId, AMQShortString consumerTag,
                AMQShortString exchange, AMQShortString routingKey, boolean redelivered)
        {
            super(channelId, deliveryId, consumerTag, exchange, routingKey, redelivered);
            _consumerTag = consumerTag;
        }

        public CloseConsumerMessage(BasicMessageConsumer consumer)
        {
            this(0, 0, consumer.getConsumerTag(), null, null, false);            
        }

        public BasicDeliverBody getDeliverBody()
             {
                 return new BasicDeliverBody()
                  {
    
                     public AMQShortString getConsumerTag()
                     {
                         return _consumerTag;
                     }

                    @Override
                    public long getDeliveryTag()
                    {
                        return 0;
                    }

                    @Override
                    public AMQShortString getExchange()
                    {
                        return null;
                    }

                    @Override
                    public boolean getRedelivered()
                    {
                        return false;
                    }

                    @Override
                    public AMQShortString getRoutingKey()
                    {
                        return null;
                    }

                    @Override
                    public boolean execute(MethodDispatcher methodDispatcher, int channelId) throws AMQException
                    {
                        return false;
                    }

                    @Override
                    public AMQFrame generateFrame(int channelId)
                    {
                        return null;
                    }

                    @Override
                    public AMQChannelException getChannelException(AMQConstant code, String message)
                    {
                        return null;
                    }

                    @Override
                    public AMQChannelException getChannelException(AMQConstant code, String message, Throwable cause)
                    {
                        return null;
                    }

                    @Override
                    public AMQChannelException getChannelNotFoundException(int channelId)
                    {
                        return null;
                    }

                    @Override
                    public int getClazz()
                    {
                        return 0;
                    }

                    @Override
                    public AMQConnectionException getConnectionException(AMQConstant code, String message)
                    {
                        return null;
                    }

                    @Override
                    public AMQConnectionException getConnectionException(AMQConstant code, String message,
                            Throwable cause)
                    {
                        return null;
                    }

                    @Override
                    public byte getMajor()
                    {
                        return 0;
                    }

                    @Override
                    public int getMethod()
                    {
                        return 0;
                    }

                    @Override
                    public byte getMinor()
                    {
                        return 0;
                    }

                    @Override
                    public int getSize()
                    {
                        return 0;
                    }

                    @Override
                    public void writeMethodPayload(ByteBuffer buffer)
                    {
                    }

                    @Override
                    public void writePayload(ByteBuffer buffer)
                    {
                    }

                    @Override
                    public byte getFrameType()
                    {
                        return 0;
                    }

                    @Override
                    public void handle(int channelId, AMQVersionAwareProtocolSession amqMinaProtocolSession)
                            throws AMQException
                    {
                        
                    }
                  };
             }

        @Override
        public List getBodies()
        {
            return null;
        }

        @Override
        public Object getContentHeader()
        {
            return null;
        }

        @Override
        public void receiveBody(Object nativeMessageBody)
        {
            
        }

        @Override
        public void setContentHeader(Object nativeMessageHeader)
        {
            
        }
    }
}