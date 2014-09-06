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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpSequence;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

class MessageFactory
{
    private final SessionImpl _session;


    MessageFactory(final SessionImpl session)
    {
        _session = session;
    }

    public MessageImpl createMessage(final DestinationImpl destination, final Message msg)
    {
        MessageImpl message;
        List<Section> payload = msg.getPayload();
        Header header = null;
        MessageAnnotations messageAnnotations = null;
        DeliveryAnnotations deliveryAnnotations = null;

        Properties properties = null;
        ApplicationProperties appProperties = null;
        Footer footer;

        Iterator<Section> iter = payload.iterator();
        List<Section> body = new ArrayList<Section>();

        Section section = iter.hasNext() ? iter.next() : null;

        if(section instanceof Header)
        {
            header = (Header) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        if(section instanceof DeliveryAnnotations)
        {
            deliveryAnnotations = (DeliveryAnnotations) section;
            section = iter.hasNext() ? iter.next() : null;
        }
        
        if(section instanceof MessageAnnotations)
        {
            messageAnnotations = (MessageAnnotations) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        if(section instanceof Properties)
        {
            properties = (Properties) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        if(section instanceof ApplicationProperties)
        {
            appProperties = (ApplicationProperties) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        while(section != null && !(section instanceof Footer))
        {
            body.add(section);
            section = iter.hasNext() ? iter.next() : null;
        }

        footer = (Footer) section;

        if(body.size() == 1)
        {
            Section bodySection = body.get(0);
            if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof Map)
            {
                message = new MapMessageImpl(header, deliveryAnnotations, messageAnnotations, properties, appProperties, (Map) ((AmqpValue)bodySection).getValue(), footer, _session);
            }
            else if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof List)
            {
                message = new StreamMessageImpl(header,
                                                deliveryAnnotations,
                                                messageAnnotations, properties, appProperties,
                                                (List) ((AmqpValue)bodySection).getValue(), footer, _session
                );
            }
            else if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof String)
            {
                message = new TextMessageImpl(header, deliveryAnnotations, messageAnnotations, properties, appProperties,
                                                (String) ((AmqpValue)bodySection).getValue(), footer, _session);
            }
            else if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof Binary)
            {

                Binary value = (Binary) ((AmqpValue) bodySection).getValue();
                message = new BytesMessageImpl(header,
                                               deliveryAnnotations,
                                               messageAnnotations, properties, appProperties,
                                               new Data(value), footer, _session);
            }
            else if(bodySection instanceof Data)
            {
                if(properties != null && ObjectMessageImpl.CONTENT_TYPE.equals(properties.getContentType()))
                {


                    message = new ObjectMessageImpl(header,
                                                    deliveryAnnotations,
                                                    messageAnnotations, properties, appProperties,
                                                    (Data) bodySection,
                                                    footer,
                                                    _session);
                }
                else
                {
                    message = new BytesMessageImpl(header,
                                                   deliveryAnnotations,
                                                   messageAnnotations, properties, appProperties, (Data) bodySection, footer, _session);
                }
            }
            else if(bodySection instanceof AmqpSequence)
            {
                message = new StreamMessageImpl(header,
                                                deliveryAnnotations,
                                                messageAnnotations, properties, appProperties, ((AmqpSequence) bodySection).getValue(), footer, _session
                );
            }

            /*else if(bodySection instanceof AmqpDataSection)
            {
                AmqpDataSection dataSection = (AmqpDataSection) bodySection;

                List<Object> data = new ArrayList<Object>();

                ListIterator<Object> dataIter = dataSection.iterator();

                while(dataIter.hasNext())
                {
                    data.add(dataIter.next());
                }

                if(data.size() == 1)
                {
                    final Object obj = data.get(0);
                    if( obj instanceof String)
                    {
                        message = new TextMessageImpl(header,properties,appProperties,(String) data.get(0),footer, _session);
                    }
                    else if(obj instanceof JavaSerializable)
                    {
                        // TODO - ObjectMessage
                        message = new AmqpMessageImpl(header,properties,appProperties,body,footer, _session);
                    }
                    else if(obj instanceof Serializable)
                    {
                        message = new ObjectMessageImpl(header,properties,footer,appProperties,(Serializable)obj, _session);
                    }
                    else
                    {
                        message = new AmqpMessageImpl(header,properties,appProperties,body,footer, _session);
                    }
                }
                else
                {
                    // not a text message
                    message = new AmqpMessageImpl(header,properties,appProperties,body,footer, _session);
                }
            }*/
            else
            {
                message = new AmqpMessageImpl(header,
                                              deliveryAnnotations,
                                              messageAnnotations, properties,appProperties,body,footer, _session);
            }
        }
        else
        {
            message = new AmqpMessageImpl(header,
                                          deliveryAnnotations,
                                          messageAnnotations, properties,appProperties,body,footer, _session);
        }

        message.setReadOnly();

        return message;
    }
}
