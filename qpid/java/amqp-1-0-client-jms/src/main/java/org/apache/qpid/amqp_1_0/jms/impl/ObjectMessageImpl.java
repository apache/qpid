/*
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
 */

package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.ObjectMessage;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

import javax.jms.MessageNotWriteableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

public class ObjectMessageImpl extends MessageImpl implements ObjectMessage
{
    static final Symbol CONTENT_TYPE = Symbol.valueOf("application/x-java-serialized-object");

    private Serializable _object;

    protected ObjectMessageImpl(Header header,
                                MessageAnnotations messageAnnotations,
                                Properties properties,
                                ApplicationProperties appProperties,
                                Serializable object,
                                Footer footer,
                                SessionImpl session)
    {
        super(header, messageAnnotations, properties, appProperties, footer, session);
        getProperties().setContentType(CONTENT_TYPE);
        _object = object;
    }

    protected ObjectMessageImpl(final SessionImpl session)
    {
        super(new Header(), new MessageAnnotations(new HashMap()),
              new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        getProperties().setContentType(CONTENT_TYPE);
    }

    public void setObject(final Serializable serializable) throws MessageNotWriteableException
    {
        checkWritable();

        _object = serializable;
    }

    public Serializable getObject()
    {
        return _object;
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        if(getMessageAnnotations() != null && getMessageAnnotations().getValue() != null && !getMessageAnnotations().getValue().isEmpty())
        {
            sections.add(getMessageAnnotations());
        }
        sections.add(getProperties());
        sections.add(getApplicationProperties());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(_object);
            oos.flush();
            oos.close();
            sections.add(new Data(new Binary(baos.toByteArray())));

        }
        catch (IOException e)
        {
            e.printStackTrace();  //TODO
        }

        sections.add(getFooter());
        return sections;
    }
}
