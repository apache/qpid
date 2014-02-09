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
package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.amqp_1_0.messaging.SectionDecoderImpl;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpSequence;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.typedmessage.TypedBytesContentReader;
import org.apache.qpid.typedmessage.TypedBytesFormatException;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class MessageConverter_v1_0_to_Internal implements MessageConverter<Message_1_0, InternalMessage>
{

    static final AMQPDescribedTypeRegistry TYPE_REGISTRY = AMQPDescribedTypeRegistry.newInstance();
    static
    {
        TYPE_REGISTRY.registerTransportLayer();
        TYPE_REGISTRY.registerMessagingLayer();
        TYPE_REGISTRY.registerTransactionLayer();
        TYPE_REGISTRY.registerSecurityLayer();
    }

    @Override
    public Class<Message_1_0> getInputClass()
    {
        return Message_1_0.class;
    }

    @Override
    public Class<InternalMessage> getOutputClass()
    {
        return InternalMessage.class;
    }

    @Override
    public InternalMessage convert(Message_1_0 serverMessage, VirtualHost vhost)
    {
        final String mimeType = serverMessage.getMessageHeader().getMimeType();




        byte[] data = new byte[(int) serverMessage.getSize()];
        serverMessage.getStoredMessage().getContent(0,ByteBuffer.wrap(data));

        SectionDecoderImpl sectionDecoder = new SectionDecoderImpl(TYPE_REGISTRY);

        try
        {
            List<Section> sections = sectionDecoder.parseAll(ByteBuffer.wrap(data));
            ListIterator<Section> iterator = sections.listIterator();
            Section previousSection = null;
            while(iterator.hasNext())
            {
                Section section = iterator.next();
                if(!(section instanceof AmqpValue  || section instanceof Data || section instanceof AmqpSequence))
                {
                    iterator.remove();
                }
                else
                {
                    if(previousSection != null && (previousSection.getClass() != section.getClass() || section instanceof AmqpValue))
                    {
                        throw new RuntimeException("Message is badly formed and has multiple body section which are not all Data or not all AmqpSequence");
                    }
                    else
                    {
                        previousSection = section;
                    }
                }
            }

            Object bodyObject;

            if(sections.isEmpty())
            {
                // should actually be illegal
                bodyObject = new byte[0];
            }
            else
            {
                Section firstBodySection = sections.get(0);
                if(firstBodySection instanceof AmqpValue)
                {
                    bodyObject = fixObject(((AmqpValue)firstBodySection).getValue());
                }
                else if(firstBodySection instanceof Data)
                {
                    int totalSize = 0;
                    for(Section section : sections)
                    {
                        totalSize += ((Data)section).getValue().getLength();
                    }
                    byte[] bodyData = new byte[totalSize];
                    ByteBuffer buf = ByteBuffer.wrap(bodyData);
                    for(Section section : sections)
                    {
                        buf.put(((Data)section).getValue().asByteBuffer());
                    }
                    bodyObject = bodyData;
                }
                else
                {
                    ArrayList totalSequence = new ArrayList();
                    for(Section section : sections)
                    {
                        totalSequence.addAll(((AmqpSequence)section).getValue());
                    }
                    bodyObject = fixObject(totalSequence);
                }
            }
            return InternalMessage.convert(serverMessage.getMessageNumber(), serverMessage.isPersistent(), serverMessage.getMessageHeader(), bodyObject);

        }
        catch (AmqpErrorException e)
        {
            throw new RuntimeException(e);
        }




    }

    private Object fixObject(final Object value)
    {
        if(value instanceof Binary)
        {
            final Binary binaryValue = (Binary) value;
            byte[] data = new byte[binaryValue.getLength()];
            binaryValue.asByteBuffer().get(data);
            return data;
        }
        else if(value instanceof List)
        {
            List listValue = (List) value;
            List fixedValue = new ArrayList(listValue.size());
            for(Object o : listValue)
            {
                fixedValue.add(fixObject(o));
            }
            return fixedValue;
        }
        else if(value instanceof Map)
        {
            Map<?,?> mapValue = (Map) value;
            Map fixedValue = new LinkedHashMap(mapValue.size());
            for(Map.Entry<?,?> entry : mapValue.entrySet())
            {
                fixedValue.put(fixObject(entry.getKey()),fixObject(entry.getValue()));
            }
            return fixedValue;
        }
        else
        {
            return value;
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
                    throw new RuntimeException(e);  // TODO - Implement
                }
                catch (EOFException e)
                {
                    throw new RuntimeException(e);  // TODO - Implement
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
        return "v0-8 to Internal";
    }
}
