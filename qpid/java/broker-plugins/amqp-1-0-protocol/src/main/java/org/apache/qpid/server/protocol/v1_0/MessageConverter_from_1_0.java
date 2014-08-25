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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.amqp_1_0.messaging.SectionDecoderImpl;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpSequence;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.typedmessage.TypedBytesContentWriter;
import org.apache.qpid.typedmessage.TypedBytesFormatException;

public class MessageConverter_from_1_0
{
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public static Object convertBodyToObject(final Message_1_0 serverMessage)
    {
        byte[] data = new byte[(int) serverMessage.getSize()];
        serverMessage.getStoredMessage().getContent(0, ByteBuffer.wrap(data));

        SectionDecoderImpl sectionDecoder = new SectionDecoderImpl(MessageConverter_v1_0_to_Internal.TYPE_REGISTRY);

        Object bodyObject;
        try
        {
            List<Section> sections = sectionDecoder.parseAll(ByteBuffer.wrap(data));
            ListIterator<Section> iterator = sections.listIterator();
            Section previousSection = null;
            while(iterator.hasNext())
            {
                Section section = iterator.next();
                if(!(section instanceof AmqpValue || section instanceof Data || section instanceof AmqpSequence))
                {
                    iterator.remove();
                }
                else
                {
                    if(previousSection != null && (previousSection.getClass() != section.getClass() || section instanceof AmqpValue))
                    {
                        throw new ConnectionScopedRuntimeException("Message is badly formed and has multiple body section which are not all Data or not all AmqpSequence");
                    }
                    else
                    {
                        previousSection = section;
                    }
                }
            }


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
                    bodyObject = convertValue(((AmqpValue)firstBodySection).getValue());
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
                    bodyObject = convertValue(totalSequence);
                }
            }

        }
        catch (AmqpErrorException e)
        {
            throw new ConnectionScopedRuntimeException(e);
        }
        return bodyObject;
    }

    private static final Set<Class> STANDARD_TYPES = new HashSet<>(Arrays.<Class>asList(Boolean.class,
                                                                                        Byte.class,
                                                                                        Short.class,
                                                                                        Integer.class,
                                                                                        Long.class,
                                                                                        Float.class,
                                                                                        Double.class,
                                                                                        Character.class,
                                                                                        String.class,
                                                                                        byte[].class,
                                                                                        UUID.class));

    private static Map convertMap(final Map map)
    {
        Map resultMap = new LinkedHashMap();
        Iterator<Map.Entry> iterator = map.entrySet().iterator();
        while(iterator.hasNext())
        {
            Map.Entry entry = iterator.next();
            resultMap.put(convertValue(entry.getKey()), convertValue(entry.getValue()));

        }
        return resultMap;
    }

    public static Object convertValue(final Object value)
    {
        if(value != null && !STANDARD_TYPES.contains(value))
        {
            if(value instanceof Map)
            {
                return convertMap((Map)value);
            }
            else if(value instanceof List)
            {
                return convertList((List)value);
            }
            else if(value instanceof UnsignedByte)
            {
                return ((UnsignedByte)value).shortValue();
            }
            else if(value instanceof UnsignedShort)
            {
                return ((UnsignedShort)value).intValue();
            }
            else if(value instanceof UnsignedInteger)
            {
                return ((UnsignedInteger)value).longValue();
            }
            else if(value instanceof UnsignedLong)
            {
                return ((UnsignedLong)value).longValue();
            }
            else if(value instanceof Symbol)
            {
                return value.toString();
            }
            else if(value instanceof Date)
            {
                return ((Date)value).getTime();
            }
            else if(value instanceof Binary)
            {
                Binary binary = (Binary)value;
                byte[] data = new byte[binary.getLength()];
                binary.asByteBuffer().get(data);
                return data;
            }
            else
            {
                // Throw exception instead?
                return value.toString();
            }
        }
        else
        {
            return value;
        }
    }

    private static List convertList(final List list)
    {
        List result = new ArrayList(list.size());
        for(Object entry : list)
        {
            result.add(convertValue(entry));
        }
        return result;
    }

    public static byte[] convertToBody(Object object)
    {
        if(object instanceof String)
        {
            return ((String)object).getBytes(UTF_8);
        }
        else if(object instanceof byte[])
        {
            return (byte[]) object;
        }
        else if(object instanceof Map)
        {
            BBEncoder encoder = new BBEncoder(1024);
            encoder.writeMap((Map)object);
            ByteBuffer buf = encoder.segment();
            int remaining = buf.remaining();
            byte[] data = new byte[remaining];
            buf.get(data);
            return data;

        }
        else if(object instanceof List)
        {
            try
            {
                ByteBuffer buf;
                if(onlyPrimitiveTypes((List)object))
                {
                    TypedBytesContentWriter writer = new TypedBytesContentWriter();
                    for(Object value : (List)object)
                    {
                        writer.writeObject(value);
                    }
                    buf = writer.getData();

                }
                else
                {
                    BBEncoder encoder = new BBEncoder(1024);
                    encoder.writeList((List) object);
                    buf = encoder.segment();
                }
                int remaining = buf.remaining();
                byte[] data = new byte[remaining];
                buf.get(data);
                return data;
            }
            catch (TypedBytesFormatException e)
            {
                throw new ConnectionScopedRuntimeException(e);
            }
        }
        else
        {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            try
            {
                ObjectOutputStream os = new ObjectOutputStream(bytesOut);
                os.writeObject(object);
                return bytesOut.toByteArray();
            }
            catch (IOException e)
            {
                throw new ConnectionScopedRuntimeException(e);
            }
        }
    }

    public static boolean onlyPrimitiveTypes(final List list)
    {
        for(Object value : list)
        {
            if(!(value instanceof String
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Double
                || value instanceof Float
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Character
                || value instanceof Boolean
                || value instanceof byte[]))
            {
                return false;
            }
        }
        return true;
    }

    public static String getBodyMimeType(Object object)
    {
        if(object instanceof String)
        {
            return "text/plain";
        }
        else if(object instanceof byte[])
        {
            return "application/octet-stream";
        }
        else if(object instanceof Map)
        {
            return "amqp/map";
        }
        else if(object instanceof List)
        {
            return onlyPrimitiveTypes((List)object) ? "jms/stream-message" : "amqp/list";
        }
        else
        {
            return "application/java-object-stream";
        }
    }
}
