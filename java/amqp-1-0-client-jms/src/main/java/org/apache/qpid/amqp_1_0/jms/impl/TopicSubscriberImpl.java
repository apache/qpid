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

import java.util.Map;
import java.util.UUID;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import org.apache.qpid.amqp_1_0.client.AcknowledgeMode;
import org.apache.qpid.amqp_1_0.client.ConnectionErrorException;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.jms.Topic;
import org.apache.qpid.amqp_1_0.jms.TopicSubscriber;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;

public class TopicSubscriberImpl extends MessageConsumerImpl implements TopicSubscriber
{

    TopicSubscriberImpl(String name,
                        boolean durable,
                        final Topic destination,
                        final SessionImpl session,
                        final String selector,
                        final boolean noLocal)
            throws JMSException
    {
        super(destination, session, selector, noLocal, name, durable);
        setTopicSubscriber(true);
    }

    TopicSubscriberImpl(final Topic destination,
                        final SessionImpl session,
                        final String selector,
                        final boolean noLocal)
            throws JMSException
    {
        super(destination, session, selector, noLocal);
        setTopicSubscriber(true);
    }

    public TopicImpl getTopic() throws JMSException
    {
        return (TopicImpl) getDestination();
    }


    protected Receiver createClientReceiver() throws JMSException
    {
        try
        {
            String address = getSession().toAddress(getDestination());
            String targetAddress = getDestination().getLocalTerminus() != null ? getDestination().getLocalTerminus() : UUID.randomUUID().toString();

            Receiver receiver = getSession().getClientSession().createReceiver(address, targetAddress,
                                                                               StdDistMode.COPY, AcknowledgeMode.ALO,
                                                                               getLinkName(), isDurable(), getFilters(),
                                                                               null);
            String actualAddress = receiver.getAddress();

            @SuppressWarnings("unchecked")
            Map<Symbol, Filter> actualFilters  = (Map<Symbol, Filter>) receiver.getFilter();

            if(!address.equals(actualAddress) || !filtersEqual(getFilters(), actualFilters))
            {
                receiver.close();
                if(isDurable())
                {
                    receiver = getSession().getClientSession().createReceiver(address,
                            StdDistMode.COPY, AcknowledgeMode.ALO,
                            getLinkName(), false, getFilters(),
                            null);
                    receiver.close();
                }
                receiver = getSession().getClientSession().createReceiver(address,
                                                                          StdDistMode.COPY, AcknowledgeMode.ALO,
                                                                          getLinkName(), isDurable(), getFilters(),
                                                                          null);
            }


            return receiver;
        }
        catch (ConnectionErrorException e)
        {
            org.apache.qpid.amqp_1_0.type.transport.Error error = e.getRemoteError();
            if(AmqpError.INVALID_FIELD.equals(error.getCondition()))
            {
                throw new InvalidSelectorException(e.getMessage());
            }
            else
            {
                throw new JMSException(e.getMessage(), error.getCondition().getValue().toString());

            }

        }
    }

    private boolean filtersEqual(Map<Symbol, Filter> filters, Map<Symbol, Filter> actualFilters)
    {
        if(filters == null || filters.isEmpty())
        {
            return actualFilters == null || actualFilters.isEmpty();
        }
        else
        {
            return actualFilters != null && filters.equals(actualFilters);
        }

    }


    protected void closeUnderlyingReceiver(Receiver receiver)
    {
        if(isDurable())
        {
            receiver.detach();
        }
        else
        {
            receiver.close();
        }
    }
}
