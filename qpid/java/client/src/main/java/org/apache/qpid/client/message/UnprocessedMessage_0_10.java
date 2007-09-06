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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.Struct;

/**
 * This class contains everything needed to process a JMS message. It assembles the deliver body, the content header and
 * the content body/ies.
 *
 * Note that the actual work of creating a JMS message for the client code's use is done outside of the MINA dispatcher
 * thread in order to minimise the amount of work done in the MINA dispatcher thread.
 */
public class UnprocessedMessage_0_10 extends UnprocessedMessage<Struct[],ByteBuffer>
{
    private Struct[] _headers;

    /** List of ContentBody instances. Due to fragmentation you don't know how big this will be in general */
    private List<ByteBuffer> _bodies = new ArrayList<ByteBuffer>();

    public UnprocessedMessage_0_10(int channelId,long deliveryId,String consumerTag,AMQShortString exchange,AMQShortString routingKey,boolean redelivered)
    {
        super(channelId,deliveryId,consumerTag,exchange,routingKey,redelivered);
    }

    public void receiveBody(ByteBuffer body)
    {

        _bodies.add(body);
    }

    public void setContentHeader(Struct[] headers)
    {
        this._headers = headers;
        for(Struct s: headers)
        {
            if (s instanceof DeliveryProperties)
            {
                DeliveryProperties props = (DeliveryProperties)s;
                _exchange = new AMQShortString(props.getExchange());
                _routingKey = new AMQShortString(props.getRoutingKey());
                _redelivered = props.getRedelivered();
            }
        }
    }

    public Struct[] getContentHeader()
    {
        return _headers;
    }

    public List<ByteBuffer> getBodies()
    {
        return _bodies;
    }

}
