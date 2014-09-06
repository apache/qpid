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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.apache.qpid.amqp_1_0.jms.AmqpMessage;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class AmqpMessageImpl extends MessageImpl implements AmqpMessage
{
    private List<Section> _sections;

    protected AmqpMessageImpl(Header header,
                              DeliveryAnnotations deliveryAnnotations,
                              MessageAnnotations messageAnnotations,
                              Properties properties,
                              ApplicationProperties appProperties,
                              List<Section> sections,
                              Footer footer,
                              SessionImpl session)
    {
        super(header, deliveryAnnotations, messageAnnotations, properties, appProperties, footer, session);
        _sections = sections;
    }

    protected AmqpMessageImpl(final SessionImpl session)
    {
        super(new Header(),
              new DeliveryAnnotations(new HashMap()),
              new MessageAnnotations(new HashMap()), new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        _sections = new ArrayList<Section>();
    }

    public int getSectionCount()
    {
        return _sections.size();
    }

    public Section getSection(final int position)
    {
        return _sections.get(position);
    }

    public ListIterator<Section> sectionIterator()
    {
        return _sections.listIterator();
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
        sections.addAll(_sections);
        sections.add(getFooter());
        return sections;
    }
}
