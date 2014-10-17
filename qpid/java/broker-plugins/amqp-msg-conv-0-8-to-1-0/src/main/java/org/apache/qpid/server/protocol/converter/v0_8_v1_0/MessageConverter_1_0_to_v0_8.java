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
package org.apache.qpid.server.protocol.converter.v0_8_v1_0;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v0_8.MessageMetaData;
import org.apache.qpid.server.protocol.v1_0.MessageConverter_from_1_0;
import org.apache.qpid.server.protocol.v1_0.MessageMetaData_1_0;
import org.apache.qpid.server.protocol.v1_0.Message_1_0;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

@PluggableService
public class MessageConverter_1_0_to_v0_8 implements MessageConverter<Message_1_0, AMQMessage>
{
    private static final int BASIC_CLASS_ID = 60;


    public Class<Message_1_0> getInputClass()
    {
        return Message_1_0.class;
    }

    @Override
    public Class<AMQMessage> getOutputClass()
    {
        return AMQMessage.class;
    }

    @Override
    public AMQMessage convert(Message_1_0 serverMsg, VirtualHostImpl vhost)
    {
        return new AMQMessage(convertToStoredMessage(serverMsg), null);
    }

    private StoredMessage<MessageMetaData> convertToStoredMessage(final Message_1_0 serverMsg)
    {
        Object bodyObject = MessageConverter_from_1_0.convertBodyToObject(serverMsg);




        final byte[] messageContent = MessageConverter_from_1_0.convertToBody(bodyObject);
        final MessageMetaData messageMetaData_0_8 = convertMetaData(serverMsg,
                                                                    MessageConverter_from_1_0.getBodyMimeType(bodyObject),
                                                                    messageContent.length);

        return new StoredMessage<MessageMetaData>()
        {
            @Override
            public MessageMetaData getMetaData()
            {
                return messageMetaData_0_8;
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

    private MessageMetaData convertMetaData(final Message_1_0 serverMsg, final String bodyMimeType, final int size)
    {

        final MessageMetaData_1_0.MessageHeader_1_0 header = serverMsg.getMessageHeader();
        String key = header.getTo();
        if(key == null)
        {
            key = header.getSubject();
        }

        MessagePublishInfo publishInfo = new MessagePublishInfo(null, false, false, AMQShortString.valueOf(key));


        final BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        props.setAppId(serverMsg.getMessageHeader().getAppId());
        props.setContentType(bodyMimeType);
        props.setCorrelationId(serverMsg.getMessageHeader().getCorrelationId());
        props.setDeliveryMode(serverMsg.isPersistent() ? BasicContentHeaderProperties.PERSISTENT : BasicContentHeaderProperties.NON_PERSISTENT);
        props.setExpiration(serverMsg.getExpiration());
        props.setMessageId(serverMsg.getMessageHeader().getMessageId());
        props.setPriority(serverMsg.getMessageHeader().getPriority());
        props.setReplyTo(serverMsg.getMessageHeader().getReplyTo());
        props.setTimestamp(serverMsg.getMessageHeader().getTimestamp());
        props.setUserId(serverMsg.getMessageHeader().getUserId());

        Map<String,Object> headerProps = new LinkedHashMap<String, Object>();

        if(header.getSubject() != null)
        {
            headerProps.put("qpid.subject", header.getSubject());
        }

        for(String headerName : serverMsg.getMessageHeader().getHeaderNames())
        {
            headerProps.put(headerName, MessageConverter_from_1_0.convertValue(serverMsg.getMessageHeader().getHeader(headerName)));
        }

        props.setHeaders(FieldTable.convertToFieldTable(headerProps));

        final ContentHeaderBody chb = new ContentHeaderBody(props);
        chb.setBodySize(size);

        return new MessageMetaData(publishInfo, chb, serverMsg.getArrivalTime());
    }


    @Override
    public String getType()
    {
        return "v1-0 to v0-8";
    }


}
