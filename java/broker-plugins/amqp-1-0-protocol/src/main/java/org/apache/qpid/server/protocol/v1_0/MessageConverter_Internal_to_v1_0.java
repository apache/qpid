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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;

@PluggableService
public class MessageConverter_Internal_to_v1_0 extends MessageConverter_to_1_0<InternalMessage>
{
    private static final Charset UTF_8 = Charset.forName("UTF-8");


    public Class<InternalMessage> getInputClass()
    {
        return InternalMessage.class;
    }


    @Override
    protected MessageMetaData_1_0 convertMetaData(final InternalMessage serverMessage,
                                                  final SectionEncoder sectionEncoder)
    {
        List<Section> sections = new ArrayList<Section>(3);
        Header header = new Header();

        header.setDurable(serverMessage.isPersistent());
        header.setPriority(UnsignedByte.valueOf(serverMessage.getMessageHeader().getPriority()));
        if(serverMessage.getExpiration() != 0l && serverMessage.getArrivalTime() !=0l && serverMessage.getExpiration() >= serverMessage.getArrivalTime())
        {
            header.setTtl(UnsignedInteger.valueOf(serverMessage.getExpiration()-serverMessage.getArrivalTime()));
        }

        sections.add(header);

        Properties properties = new Properties();
        properties.setCorrelationId(serverMessage.getMessageHeader().getCorrelationId());
        properties.setCreationTime(new Date(serverMessage.getMessageHeader().getTimestamp()));
        properties.setMessageId(serverMessage.getMessageHeader().getMessageId());
        final String userId = serverMessage.getMessageHeader().getUserId();
        if(userId != null)
        {
            properties.setUserId(new Binary(userId.getBytes(UTF_8)));
        }
        properties.setReplyTo(serverMessage.getMessageHeader().getReplyTo());

        sections.add(properties);

        if(!serverMessage.getMessageHeader().getHeaderNames().isEmpty())
        {
            ApplicationProperties applicationProperties = new ApplicationProperties(serverMessage.getMessageHeader().getHeaderMap() );
            sections.add(applicationProperties);
        }
        return new MessageMetaData_1_0(sections, sectionEncoder);

    }

    protected Section getBodySection(final InternalMessage serverMessage, final String mimeType)
    {
        return convertToBody(serverMessage.getMessageBody());
    }


    @Override
    public String getType()
    {
        return "Internal to v1-0";
    }


    public Section convertToBody(Object object)
    {
        if(object instanceof String)
        {
            return new AmqpValue(object);
        }
        else if(object instanceof byte[])
        {
            return new Data(new Binary((byte[])object));
        }
        else if(object instanceof Map)
        {
            return new AmqpValue(MessageConverter_to_1_0.fixMapValues((Map)object));
        }
        else if(object instanceof List)
        {
            return new AmqpValue(MessageConverter_to_1_0.fixListValues((List)object));
        }
        else
        {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            try
            {
                ObjectOutputStream os = new ObjectOutputStream(bytesOut);
                os.writeObject(object);
                return new Data(new Binary(bytesOut.toByteArray()));
            }
            catch (IOException e)
            {
                throw new ConnectionScopedRuntimeException(e);
            }
        }
    }

}
