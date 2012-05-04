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
package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Message
{
    private Binary _deliveryTag;
    private List<Section> _payload = new ArrayList<Section>();
    private Boolean _resume;
    private boolean _settled;
    private DeliveryState _deliveryState;
    private Receiver _receiver;


    public Message()
    {
    }

    public Message(Collection<Section> sections)
    {
        _payload.addAll(sections);
    }

    public Message(Section section)
    {
        this(Collections.singletonList(section));
    }

    public Message(String message)
    {
        this(new AmqpValue(message));
    }


    public Binary getDeliveryTag()
    {
        return _deliveryTag;
    }

    public void setDeliveryTag(Binary deliveryTag)
    {
        _deliveryTag = deliveryTag;
    }

    public List<Section> getPayload()
    {
        return Collections.unmodifiableList(_payload);
    }

    private <T extends Section> T getSection(Class<T> clazz)
    {
        for(Section s : _payload)
        {
            if(clazz.isAssignableFrom(s.getClass()))
            {
                return (T) s;
            }
        }
        return null;
    }

    public ApplicationProperties getApplicationProperties()
    {
        return getSection(ApplicationProperties.class);
    }

    public Properties getProperties()
    {
        return getSection(Properties.class);
    }

    public Header getHeader()
    {
        return getSection(Header.class);
    }


    public void setResume(final Boolean resume)
    {
        _resume = resume;
    }

    public boolean isResume()
    {
        return Boolean.TRUE.equals(_resume);
    }

    public void setDeliveryState(DeliveryState state)
    {
        _deliveryState = state;
    }

    public DeliveryState getDeliveryState()
    {
        return _deliveryState;
    }

    public void setSettled(boolean settled)
    {
        _settled = settled;
    }

    public boolean getSettled()
    {
        return _settled;
    }

    public void setReceiver(final Receiver receiver)
    {
        _receiver = receiver;
    }

    public Receiver getReceiver()
    {
        return _receiver;
    }
}
