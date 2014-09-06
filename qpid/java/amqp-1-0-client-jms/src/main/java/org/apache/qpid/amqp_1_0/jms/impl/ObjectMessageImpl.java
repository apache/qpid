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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;

import org.apache.qpid.amqp_1_0.jms.ObjectMessage;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class ObjectMessageImpl extends MessageImpl implements ObjectMessage
{
    static final Symbol CONTENT_TYPE = Symbol.valueOf("application/x-java-serialized-object");

    static final Data NULL_OBJECT_DATA;
    static 
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(null);
            oos.flush();
            oos.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        NULL_OBJECT_DATA = new Data(new Binary(baos.toByteArray()));
    }

    private Data _objectData = NULL_OBJECT_DATA;

    protected ObjectMessageImpl(Header header,
                                DeliveryAnnotations deliveryAnnotations,
                                MessageAnnotations messageAnnotations,
                                Properties properties,
                                ApplicationProperties appProperties,
                                Data dataSection,
                                Footer footer,
                                SessionImpl session)
    {
        super(header, deliveryAnnotations, messageAnnotations, properties, appProperties, footer, session);
        getProperties().setContentType(CONTENT_TYPE);
        Serializable serializable = null;
        _objectData = dataSection;

    }

    protected ObjectMessageImpl(final SessionImpl session)
    {
        super(new Header(), new DeliveryAnnotations(new HashMap()), new MessageAnnotations(new HashMap()),
              new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        getProperties().setContentType(CONTENT_TYPE);
    }

    public void setObject(final Serializable serializable) throws MessageNotWriteableException
    {
        checkWritable();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(serializable);
            oos.flush();
            oos.close();

            _objectData = new Data(new Binary(baos.toByteArray()));

        }
        catch (IOException e)
        {
            e.printStackTrace();  //TODO
        }
    }

    public Serializable getObject() throws JMSException
    {

        if(_objectData == null)
        {
            return null;
        }

        Binary data = _objectData.getValue();

        try
        {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data.getArray(), data.getArrayOffset(), data.getLength()));
            return (Serializable) ois.readObject();
        }
        catch (IOException e)
        {
            JMSException jmsException = new JMSException(e.getMessage());
            jmsException.setLinkedException(e);
            throw jmsException;
        }
        catch (ClassNotFoundException e)
        {

            JMSException jmsException = new JMSException(e.getMessage());
            jmsException.setLinkedException(e);
            throw jmsException;
        }

    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _objectData = null;
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        if(getDeliveryAnnotations() != null && getDeliveryAnnotations().getValue() != null && !getDeliveryAnnotations().getValue().isEmpty())
        {
            sections.add(getDeliveryAnnotations());
        }
        if(getMessageAnnotations() != null && getMessageAnnotations().getValue() != null && !getMessageAnnotations().getValue().isEmpty())
        {
            sections.add(getMessageAnnotations());
        }
        sections.add(getProperties());
        sections.add(getApplicationProperties());

        sections.add(_objectData);

        sections.add(getFooter());
        return sections;
    }
}
