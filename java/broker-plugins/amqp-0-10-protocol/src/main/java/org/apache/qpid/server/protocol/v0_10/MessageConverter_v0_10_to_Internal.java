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

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.typedmessage.TypedBytesContentReader;
import org.apache.qpid.typedmessage.TypedBytesFormatException;

@PluggableService
public class MessageConverter_v0_10_to_Internal implements MessageConverter<MessageTransferMessage, InternalMessage>
{
    @Override
    public Class<MessageTransferMessage> getInputClass()
    {
        return MessageTransferMessage.class;
    }

    @Override
    public Class<InternalMessage> getOutputClass()
    {
        return InternalMessage.class;
    }

    @Override
    public InternalMessage convert(MessageTransferMessage serverMessage, VirtualHostImpl vhost)
    {
        final String mimeType = serverMessage.getMessageHeader().getMimeType();
        byte[] data = new byte[(int) serverMessage.getSize()];
        serverMessage.getContent(ByteBuffer.wrap(data), 0);

        Object body = convertMessageBody(mimeType, data);
        MessageProperties messageProps = serverMessage.getHeader().getMessageProperties();
        AMQMessageHeader fixedHeader = new DelegatingMessageHeader(serverMessage.getMessageHeader(), messageProps == null ? null : messageProps.getReplyTo());
        return InternalMessage.convert(serverMessage.getMessageNumber(), serverMessage.isPersistent(), fixedHeader, body);
    }

    private static class DelegatingMessageHeader implements AMQMessageHeader
    {
        private final AMQMessageHeader _delegate;
        private final ReplyTo _replyTo;


        private DelegatingMessageHeader(final AMQMessageHeader delegate, final ReplyTo replyTo)
        {
            _delegate = delegate;
            _replyTo = replyTo;
        }

        @Override
        public String getCorrelationId()
        {
            return _delegate.getCorrelationId();
        }

        @Override
        public long getExpiration()
        {
            return _delegate.getExpiration();
        }

        @Override
        public String getUserId()
        {
            return _delegate.getUserId();
        }

        @Override
        public String getAppId()
        {
            return _delegate.getAppId();
        }

        @Override
        public String getMessageId()
        {
            return _delegate.getMessageId();
        }

        @Override
        public String getMimeType()
        {
            return _delegate.getMimeType();
        }

        @Override
        public String getEncoding()
        {
            return _delegate.getEncoding();
        }

        @Override
        public byte getPriority()
        {
            return _delegate.getPriority();
        }

        @Override
        public long getTimestamp()
        {
            return _delegate.getTimestamp();
        }

        @Override
        public String getType()
        {
            return _delegate.getType();
        }

        @Override
        public String getReplyTo()
        {
            return _replyTo == null
                    ? null
                    : _replyTo.getExchange() == null || _replyTo.getExchange().equals("")
                        ? _replyTo.getRoutingKey()
                        : _replyTo.getRoutingKey() == null || _replyTo.getRoutingKey().equals("")
                            ? _replyTo.getExchange()
                            : _replyTo.getExchange() + "/" + _replyTo.getRoutingKey();
        }

        @Override
        public Object getHeader(final String name)
        {
            return _delegate.getHeader(name);
        }

        @Override
        public boolean containsHeaders(final Set<String> names)
        {
            return _delegate.containsHeaders(names);
        }

        @Override
        public boolean containsHeader(final String name)
        {
            return _delegate.containsHeader(name);
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return _delegate.getHeaderNames();
        }
    }

    private static Object convertMessageBody(String mimeType, byte[] data)
    {
        if("text/plain".equals(mimeType) || "text/xml".equals(mimeType))
        {
            String text = new String(data);
            return text;
        }
        else if("jms/map-message".equals(mimeType))
        {
            TypedBytesContentReader reader = new TypedBytesContentReader(ByteBuffer.wrap(data));

            LinkedHashMap map = new LinkedHashMap();
            final int entries = reader.readIntImpl();
            for (int i = 0; i < entries; i++)
            {
                try
                {
                    String propName = reader.readStringImpl();
                    Object value = reader.readObject();

                    map.put(propName, value);
                }
                catch (EOFException e)
                {
                    throw new IllegalArgumentException(e);
                }
                catch (TypedBytesFormatException e)
                {
                    throw new IllegalArgumentException(e);
                }

            }

            return map;

        }
        else if("amqp/map".equals(mimeType))
        {
            BBDecoder decoder = new BBDecoder();
            decoder.init(ByteBuffer.wrap(data));
            final Map<String,Object> map = decoder.readMap();

            return map;

        }
        else if("amqp/list".equals(mimeType))
        {
            BBDecoder decoder = new BBDecoder();
            decoder.init(ByteBuffer.wrap(data));
            return decoder.readList();
        }
        else if("jms/stream-message".equals(mimeType))
        {
            TypedBytesContentReader reader = new TypedBytesContentReader(ByteBuffer.wrap(data));

            List list = new ArrayList();
            while (reader.remaining() != 0)
            {
                try
                {
                    list.add(reader.readObject());
                }
                catch (TypedBytesFormatException e)
                {
                    throw new ConnectionScopedRuntimeException(e);
                }
                catch (EOFException e)
                {
                    throw new ConnectionScopedRuntimeException(e);  // TODO - Implement
                }
            }
            return list;
        }
        else
        {
            return data;

        }
    }

    @Override
    public String getType()
    {
        return "v0-10 to Internal";
    }
}
