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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.framing.BasicReturnBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;

/**
 * This class contains everything needed to process a JMS message. It assembles the deliver body, the content header and
 * the content body/ies.
 *
 * Note that the actual work of creating a JMS message for the client code's use is done outside of the MINA dispatcher
 * thread in order to minimise the amount of work done in the MINA dispatcher thread.
 */
public class UnprocessedMessage_0_8 extends UnprocessedMessage<ContentHeaderBody,ContentBody>
{
    private long _bytesReceived = 0;

    private BasicDeliverBody _deliverBody;
    private ContentHeaderBody _contentHeader;

    /** List of ContentBody instances. Due to fragmentation you don't know how big this will be in general */
    private List<ContentBody> _bodies;

    public UnprocessedMessage_0_8(int channelId,long deliveryId,AMQShortString consumerTag,AMQShortString exchange,AMQShortString routingKey,boolean redelivered)
    {
        super(channelId,deliveryId,consumerTag,exchange,routingKey,redelivered);
    }

    public UnprocessedMessage_0_8(int channelId, BasicReturnBody body)
    {
        //FIXME: TGM, SRSLY 4RL
        super(channelId, 0, null, body.getExchange(), body.getRoutingKey(), false);
    }

    public UnprocessedMessage_0_8(int channelId, BasicDeliverBody body)
    {
        super(channelId, body.getDeliveryTag(), body.getConsumerTag(), body.getExchange(), body.getRoutingKey(), false);
    }

    public void receiveBody(ContentBody body)
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

    public void setMethodBody(BasicDeliverBody deliverBody)
    {
        _deliverBody = deliverBody;
    }

    public void setContentHeader(ContentHeaderBody contentHeader)
    {
        this._contentHeader = contentHeader;
    }

    public boolean isAllBodyDataReceived()
    {
        return _bytesReceived == getContentHeader().bodySize;
    }

    public BasicDeliverBody getDeliverBody()
    {
        return _deliverBody;
    }

    public ContentHeaderBody getContentHeader()
    {
        return _contentHeader;
    }

    public List<ContentBody> getBodies()
    {
        return _bodies;
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        buf.append("Channel Id : " + this.getChannelId());
        if (_contentHeader != null)
        {
          buf.append("ContentHeader " + _contentHeader);
        }
        if(_deliverBody != null)
        {
            buf.append("Delivery tag " + _deliverBody.getDeliveryTag());
            buf.append("Consumer tag " + _deliverBody.getConsumerTag());
            buf.append("Deliver Body " + _deliverBody);
        }

        return buf.toString();
    }

}
