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
package org.apache.qpid.client;

import java.util.concurrent.TimeUnit;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicMessageConsumer_0_8 extends BasicMessageConsumer<ContentHeaderBody,ContentBody>
{
    protected final Logger _logger = LoggerFactory.getLogger(getClass());

    protected BasicMessageConsumer_0_8(int channelId, AMQConnection connection, AMQDestination destination,
            String messageSelector, boolean noLocal, MessageFactoryRegistry messageFactory, AMQSession session,
            AMQProtocolHandler protocolHandler, FieldTable rawSelectorFieldTable, int prefetchHigh, int prefetchLow,
            boolean exclusive, int acknowledgeMode, boolean noConsume, boolean autoClose)
    {
        super(channelId, connection, destination,messageSelector,noLocal,messageFactory,session,
              protocolHandler, rawSelectorFieldTable, prefetchHigh, prefetchLow, exclusive,
              acknowledgeMode, noConsume, autoClose);
    }

    public void sendCancel() throws JMSAMQException
    {
        final AMQFrame cancelFrame =
            BasicCancelBody.createAMQFrame(_channelId, _protocolHandler.getProtocolMajorVersion(),
                _protocolHandler.getProtocolMinorVersion(), _consumerTag, // consumerTag
                false); // nowait

        try
        {
            _protocolHandler.syncWrite(cancelFrame, BasicCancelOkBody.class);

            if (_logger.isDebugEnabled())
            {
                _logger.debug("CancelOk'd for consumer:" + debugIdentity());
            }

        }
        catch (AMQException e)
        {
            throw new JMSAMQException("Error closing consumer: " + e, e);
        }
        catch (FailoverException e)
        {
            throw new JMSAMQException("FailoverException interrupted basic cancel.", e);
        }
    }

     public AbstractJMSMessage createJMSMessageFromUnprocessedMessage(UnprocessedMessage<ContentHeaderBody, ContentBody> messageFrame)throws Exception
     {

        return _messageFactory.createMessage(messageFrame.getDeliveryTag(),
            messageFrame.isRedelivered(), messageFrame.getExchange(),
            messageFrame.getRoutingKey(), messageFrame.getContentHeader(), messageFrame.getBodies());

    }
     
}