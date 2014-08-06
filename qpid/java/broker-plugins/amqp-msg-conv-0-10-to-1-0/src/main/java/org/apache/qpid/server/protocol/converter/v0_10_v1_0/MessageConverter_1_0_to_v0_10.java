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
package org.apache.qpid.server.protocol.converter.v0_10_v1_0;

import java.nio.ByteBuffer;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.protocol.v0_10.MessageMetaData_0_10;
import org.apache.qpid.server.protocol.v0_10.MessageTransferMessage;
import org.apache.qpid.server.protocol.v1_0.MessageConverter_from_1_0;
import org.apache.qpid.server.protocol.v1_0.Message_1_0;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;

@PluggableService
public class MessageConverter_1_0_to_v0_10 implements MessageConverter<Message_1_0, MessageTransferMessage>
{

    public Class<Message_1_0> getInputClass()
    {
        return Message_1_0.class;
    }

    @Override
    public Class<MessageTransferMessage> getOutputClass()
    {
        return MessageTransferMessage.class;
    }

    @Override
    public MessageTransferMessage convert(Message_1_0 serverMsg, VirtualHostImpl vhost)
    {
        return new MessageTransferMessage(convertToStoredMessage(serverMsg, vhost), null);
    }

    private StoredMessage<MessageMetaData_0_10> convertToStoredMessage(final Message_1_0 serverMsg,
                                                                       final VirtualHostImpl vhost)
    {
        Object bodyObject = MessageConverter_from_1_0.convertBodyToObject(serverMsg);

        final byte[] messageContent = MessageConverter_from_1_0.convertToBody(bodyObject);

        final MessageMetaData_0_10 messageMetaData_0_10 = convertMetaData(serverMsg,
                                                                          vhost,
                                                                          MessageConverter_from_1_0.getBodyMimeType(bodyObject),
                                                                          messageContent.length);

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
                return serverMsg.getMessageNumber();
            }

            @Override
            public void addContent(int offsetInMessage, ByteBuffer src)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getContent(int offsetInMessage, ByteBuffer dst)
            {
                int size = messageContent.length - offsetInMessage;
                if(dst.remaining() < size)
                {
                    size = dst.remaining();
                }
                ByteBuffer buf = ByteBuffer.wrap(messageContent, offsetInMessage, size);
                dst.put(buf);
                return size;
            }

            @Override
            public ByteBuffer getContent(int offsetInMessage, int size)
            {
                return ByteBuffer.wrap(messageContent, offsetInMessage, size);
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

    private MessageMetaData_0_10 convertMetaData(Message_1_0 serverMsg,
                                                 final VirtualHostImpl vhost,
                                                 final String bodyMimeType,
                                                 final int size)
    {
        DeliveryProperties deliveryProps = new DeliveryProperties();
        MessageProperties messageProps = new MessageProperties();

        final AMQMessageHeader origHeader = serverMsg.getMessageHeader();


        deliveryProps.setExpiration(serverMsg.getExpiration());
        deliveryProps.setPriority(MessageDeliveryPriority.get(origHeader.getPriority()));
        deliveryProps.setRoutingKey(serverMsg.getInitialRoutingAddress());
        deliveryProps.setTimestamp(origHeader.getTimestamp());

        messageProps.setContentEncoding(origHeader.getEncoding());
        messageProps.setContentLength(size);
        messageProps.setContentType(bodyMimeType);
        if(origHeader.getCorrelationId() != null)
        {
            messageProps.setCorrelationId(origHeader.getCorrelationId().getBytes());
        }
        final String origReplyTo = origHeader.getReplyTo();
        if(origReplyTo != null && !origReplyTo.equals(""))
        {
            ReplyTo replyTo;
            if(origReplyTo.startsWith("/"))
            {
                replyTo = new ReplyTo("",origReplyTo);
            }
            else if(origReplyTo.contains("/"))
            {
                String[] parts = origReplyTo.split("/",2);
                replyTo = new ReplyTo(parts[0],parts[1]);
            }
            else if(vhost.getExchange(origReplyTo) != null)
            {
                replyTo = new ReplyTo(origReplyTo,"");
            }
            else
            {
                replyTo = new ReplyTo("",origReplyTo);
            }
            messageProps.setReplyTo(replyTo);
        }

        messageProps.setApplicationHeaders(serverMsg.getMessageHeader().getHeadersAsMap());

        Header header = new Header(deliveryProps, messageProps, null);
        return new MessageMetaData_0_10(header, size, serverMsg.getArrivalTime());
    }



    @Override
    public String getType()
    {
        return "v1-0 to v0-10";
    }


}
