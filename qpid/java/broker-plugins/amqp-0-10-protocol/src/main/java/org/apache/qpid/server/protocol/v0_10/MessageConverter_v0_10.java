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
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;

public class MessageConverter_v0_10 implements MessageConverter<ServerMessage, MessageTransferMessage>
{
    @Override
    public Class<ServerMessage> getInputClass()
    {
        return ServerMessage.class;
    }

    @Override
    public Class<MessageTransferMessage> getOutputClass()
    {
        return MessageTransferMessage.class;
    }

    @Override
    public MessageTransferMessage convert(ServerMessage serverMsg, VirtualHost vhost)
    {
        return new MessageTransferMessage(convertToStoredMessage(serverMsg), null);
    }

    private StoredMessage<MessageMetaData_0_10> convertToStoredMessage(final ServerMessage serverMsg)
    {
        final MessageMetaData_0_10 messageMetaData_0_10 = convertMetaData(serverMsg);

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
                        return serverMsg.getContent(dst, offsetInMessage);
                    }

                    @Override
                    public ByteBuffer getContent(int offsetInMessage, int size)
                    {
                        return serverMsg.getContent(offsetInMessage, size);
                    }

                    @Override
                    public StoreFuture flushToStore()
                    {
                        return StoreFuture.IMMEDIATE_FUTURE;
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
    }

    private MessageMetaData_0_10 convertMetaData(ServerMessage serverMsg)
    {
        DeliveryProperties deliveryProps = new DeliveryProperties();
        MessageProperties messageProps = new MessageProperties();

        int size = (int) serverMsg.getSize();
        ByteBuffer body = ByteBuffer.allocate(size);
        serverMsg.getContent(body, 0);
        body.flip();


        deliveryProps.setExpiration(serverMsg.getExpiration());
        deliveryProps.setImmediate(serverMsg.isImmediate());
        deliveryProps.setPriority(MessageDeliveryPriority.get(serverMsg.getMessageHeader().getPriority()));
        deliveryProps.setRoutingKey(serverMsg.getRoutingKey());
        deliveryProps.setTimestamp(serverMsg.getMessageHeader().getTimestamp());

        messageProps.setContentEncoding(serverMsg.getMessageHeader().getEncoding());
        messageProps.setContentLength(size);
        messageProps.setContentType(serverMsg.getMessageHeader().getMimeType());
        if(serverMsg.getMessageHeader().getCorrelationId() != null)
        {
            messageProps.setCorrelationId(serverMsg.getMessageHeader().getCorrelationId().getBytes());
        }

        Header header = new Header(deliveryProps, messageProps, null);
        return new MessageMetaData_0_10(header, size, serverMsg.getArrivalTime());
    }

    @Override
    public String getType()
    {
        return "Unknown to v0-10";
    }
}
