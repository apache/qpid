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
package org.apache.qpid.server.protocol.v0_10;

import java.nio.ByteBuffer;

import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;

@PluggableService
public class MessageConverter_Internal_to_v0_10 implements MessageConverter<InternalMessage, MessageTransferMessage>
{
    @Override
    public Class<InternalMessage> getInputClass()
    {
        return InternalMessage.class;
    }

    @Override
    public Class<MessageTransferMessage> getOutputClass()
    {
        return MessageTransferMessage.class;
    }

    @Override
    public MessageTransferMessage convert(InternalMessage serverMsg, VirtualHostImpl vhost)
    {
        return new MessageTransferMessage(convertToStoredMessage(serverMsg), null);
    }

    private StoredMessage<MessageMetaData_0_10> convertToStoredMessage(final InternalMessage serverMsg)
    {
        final byte[] messageContent = MessageConverter_v0_10.convertToBody(serverMsg.getMessageBody());
        final MessageMetaData_0_10 messageMetaData_0_10 = convertMetaData(serverMsg,
                                                                          MessageConverter_v0_10.getBodyMimeType(
                                                                                  serverMsg.getMessageBody()),
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
                };
    }

    private MessageMetaData_0_10 convertMetaData(InternalMessage serverMsg, final String bodyMimeType, final int size)
    {
        DeliveryProperties deliveryProps = new DeliveryProperties();
        MessageProperties messageProps = new MessageProperties();



        deliveryProps.setExpiration(serverMsg.getExpiration());
        deliveryProps.setPriority(MessageDeliveryPriority.get(serverMsg.getMessageHeader().getPriority()));
        deliveryProps.setRoutingKey(serverMsg.getInitialRoutingAddress());
        deliveryProps.setTimestamp(serverMsg.getMessageHeader().getTimestamp());

        messageProps.setContentEncoding(serverMsg.getMessageHeader().getEncoding());
        messageProps.setContentLength(size);
        messageProps.setContentType(bodyMimeType);
        if(serverMsg.getMessageHeader().getCorrelationId() != null)
        {
            messageProps.setCorrelationId(serverMsg.getMessageHeader().getCorrelationId().getBytes());
        }
        messageProps.setApplicationHeaders(serverMsg.getMessageHeader().getHeaderMap());
        Header header = new Header(deliveryProps, messageProps, null);
        return new MessageMetaData_0_10(header, size, serverMsg.getArrivalTime());
    }


    @Override
    public String getType()
    {
        return "Internal to v0-10";
    }
}
