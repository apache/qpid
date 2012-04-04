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

import org.apache.qpid.amqp_1_0.client.AcknowledgeMode;
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.jms.QueueBrowser;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DistributionMode;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;

import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public class QueueBrowserImpl implements QueueBrowser
{
    private static final String JMS_SELECTOR = "jms-selector";
    private QueueImpl _queue;
    private String _selector;
    private Receiver _receiver;
    private Message _nextElement;
    private MessageEnumeration _enumeration;

    QueueBrowserImpl(final QueueImpl queue, final String selector, SessionImpl session) throws JMSException
    {
        _queue = queue;
        _selector = selector;


        Map<Symbol, Filter> filters;
        if(selector == null || selector.trim().equals(""))
        {
            filters = null;
        }
        else
        {
            filters = Collections.singletonMap(Symbol.valueOf(JMS_SELECTOR),(Filter) new JMSSelectorFilter(_selector));
        }


        try
        {
            _receiver = session.getClientSession().createReceiver(queue.getAddress(),
                                                                  StdDistMode.COPY,
                                                                  AcknowledgeMode.AMO,null,
                                                                  false,
                                                                  filters, null);
            _nextElement = _receiver.receive(0L);
            _enumeration = new MessageEnumeration();
        }
        catch(AmqpErrorException e)
        {
            org.apache.qpid.amqp_1_0.type.transport.Error error = e.getError();
            if(AmqpError.INVALID_FIELD.equals(error.getCondition())
               &&  error.getInfo() != null && Symbol.valueOf("filter").equals(error.getInfo().get(Symbol.valueOf
                    ("field"))))
            {
                throw new InvalidSelectorException(e.getMessage());
            }
            else
            {
                throw new JMSException(e.getMessage(), error.getCondition().getValue().toString());
            }

        }
    }

    public QueueImpl getQueue()
    {
        return _queue;
    }

    public String getMessageSelector()
    {
        return _selector;
    }

    public Enumeration getEnumeration() throws JMSException
    {
        if(_enumeration == null)
        {
            throw new IllegalStateException("Browser has been closed");
        }
        return _enumeration;
    }

    public void close() throws JMSException
    {
        _receiver.close();
        _enumeration = null;
    }

    private final class MessageEnumeration implements Enumeration<Message>
    {

        @Override
        public boolean hasMoreElements()
        {
            return _nextElement != null;
        }

        @Override
        public Message nextElement()
        {

            Message message = _nextElement;
            if(message == null)
            {
                message = _receiver.receive(0l);
            }
            if(message != null)
            {
                _nextElement = _receiver.receive(0l);
            }
            return message;
        }
    }
}
