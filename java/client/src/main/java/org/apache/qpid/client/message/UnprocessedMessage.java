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
    private final String _consumerTag;
    protected AMQShortString _exchange;
    protected AMQShortString _routingKey;
    protected boolean _redelivered;

    public UnprocessedMessage(int channelId,long deliveryId,String consumerTag,AMQShortString exchange,AMQShortString routingKey,boolean redelivered)
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

    public String getConsumerTag()
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
}
