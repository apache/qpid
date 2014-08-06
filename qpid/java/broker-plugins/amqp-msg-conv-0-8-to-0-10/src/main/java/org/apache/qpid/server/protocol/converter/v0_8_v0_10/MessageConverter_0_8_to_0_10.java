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
package org.apache.qpid.server.protocol.converter.v0_8_v0_10;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.protocol.v0_10.MessageMetaData_0_10;
import org.apache.qpid.server.protocol.v0_10.MessageTransferMessage;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;
import org.apache.qpid.url.AMQBindingURL;

@PluggableService
public class MessageConverter_0_8_to_0_10  implements MessageConverter<AMQMessage, MessageTransferMessage>
{
    @Override
    public Class<AMQMessage> getInputClass()
    {
        return AMQMessage.class;
    }

    @Override
    public Class<MessageTransferMessage> getOutputClass()
    {
        return MessageTransferMessage.class;
    }

    @Override
    public MessageTransferMessage convert(AMQMessage message_0_8, VirtualHostImpl vhost)
    {
        return new MessageTransferMessage(convertToStoredMessage(message_0_8), null);
    }

    private StoredMessage<MessageMetaData_0_10> convertToStoredMessage(final AMQMessage message_0_8)
    {
        final MessageMetaData_0_10 messageMetaData_0_10 = convertMetaData(message_0_8);
        return new StoredMessage<MessageMetaData_0_10>()
        {
            @Override
            public MessageMetaData_0_10 getMetaData()
            {
                return messageMetaData_0_10;
            }

            @Override
            public long getMessageNumber()
            {
                return message_0_8.getMessageNumber();
            }

            @Override
            public void addContent(int offsetInMessage, ByteBuffer src)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getContent(int offsetInMessage, ByteBuffer dst)
            {
                return message_0_8.getContent(dst, offsetInMessage);
            }

            @Override
            public ByteBuffer getContent(int offsetInMessage, int size)
            {
                return message_0_8.getContent(offsetInMessage, size);
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInMemory()
            {
                return true;
            }

            @Override
            public boolean flowToDisk()
            {
                return false;
            }
        };
    }

    private MessageMetaData_0_10 convertMetaData(AMQMessage message_0_8)
    {
        DeliveryProperties deliveryProps = new DeliveryProperties();
        MessageProperties messageProps = new MessageProperties();

        int size = (int) message_0_8.getSize();
        ByteBuffer body = ByteBuffer.allocate(size);
        message_0_8.getContent(body, 0);
        body.flip();

        BasicContentHeaderProperties properties =
                  message_0_8.getContentHeaderBody().getProperties();

        final AMQShortString exchange = message_0_8.getMessagePublishInfo().getExchange();
        if(exchange != null)
        {
            deliveryProps.setExchange(exchange.toString());
        }

        deliveryProps.setExpiration(message_0_8.getExpiration());
        deliveryProps.setImmediate(message_0_8.isImmediate());
        deliveryProps.setPriority(MessageDeliveryPriority.get(properties.getPriority()));
        deliveryProps.setRoutingKey(message_0_8.getInitialRoutingAddress());
        deliveryProps.setTimestamp(properties.getTimestamp());

        messageProps.setContentEncoding(properties.getEncodingAsString());
        messageProps.setContentLength(size);
        if(properties.getAppId() != null)
        {
            messageProps.setAppId(properties.getAppId().getBytes());
        }
        messageProps.setContentType(properties.getContentTypeAsString());
        if(properties.getCorrelationId() != null)
        {
            messageProps.setCorrelationId(properties.getCorrelationId().getBytes());
        }

        if(properties.getReplyTo() != null && properties.getReplyTo().length() != 0)
        {
            String origReplyToString = properties.getReplyTo().asString();
            ReplyTo replyTo = new ReplyTo();
            // if the string looks like a binding URL, then attempt to parse it...
            try
            {
                AMQBindingURL burl = new AMQBindingURL(origReplyToString);
                AMQShortString routingKey = burl.getRoutingKey();
                if(routingKey != null)
                {
                    replyTo.setRoutingKey(routingKey.asString());
                }

                AMQShortString exchangeName = burl.getExchangeName();
                if(exchangeName != null)
                {
                    replyTo.setExchange(exchangeName.asString());
                }
            }
            catch (URISyntaxException e)
            {
                replyTo.setRoutingKey(origReplyToString);
            }
            messageProps.setReplyTo(replyTo);

        }

        if(properties.getMessageId() != null)
        {
            try
            {
                String messageIdAsString = properties.getMessageIdAsString();
                if(messageIdAsString.startsWith("ID:"))
                {
                    messageIdAsString = messageIdAsString.substring(3);
                }
                UUID uuid = UUID.fromString(messageIdAsString);
                messageProps.setMessageId(uuid);
            }
            catch(IllegalArgumentException e)
            {
                // ignore - can't parse
            }
        }



        if(properties.getUserId() != null)
        {
            messageProps.setUserId(properties.getUserId().getBytes());
        }

        FieldTable fieldTable = properties.getHeaders();

        Map<String, Object> appHeaders = FieldTable.convertToMap(fieldTable);

        if(properties.getType() != null)
        {
            appHeaders.put("x-jms-type", properties.getTypeAsString());
        }


        messageProps.setApplicationHeaders(appHeaders);

        Header header = new Header(deliveryProps, messageProps, null);


        return new MessageMetaData_0_10(header, size, message_0_8.getArrivalTime());
    }

    @Override
    public String getType()
    {
        return "v0-8 to v0-10";
    }
}
