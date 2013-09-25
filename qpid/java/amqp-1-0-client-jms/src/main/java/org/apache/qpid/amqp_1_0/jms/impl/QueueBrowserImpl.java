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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import org.apache.qpid.amqp_1_0.client.AcknowledgeMode;
import org.apache.qpid.amqp_1_0.client.ConnectionErrorException;
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.jms.QueueBrowser;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;

public class QueueBrowserImpl implements QueueBrowser
{
    private static final String JMS_SELECTOR = "jms-selector";
    private QueueImpl _queue;
    private String _selector;
    private final SessionImpl _session;
    private Map<Symbol, Filter> _filters;
    private HashSet<MessageEnumeration> _enumerations = new HashSet<MessageEnumeration>();
    private boolean _closed;

    QueueBrowserImpl(final QueueImpl queue, final String selector, SessionImpl session) throws JMSException
    {
        _queue = queue;
        _selector = selector;
        _session = session;


        if(selector == null || selector.trim().equals(""))
        {
            _filters = null;
        }
        else
        {
            _filters = Collections.singletonMap(Symbol.valueOf(JMS_SELECTOR),(Filter) new JMSSelectorFilter(_selector));
            // We do this just to have the server validate the filter..
            new MessageEnumeration().close();
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
        if(_closed)
        {
            throw new IllegalStateException("Browser has been closed");
        }
        return new MessageEnumeration();
    }

    public void close() throws JMSException
    {
        _closed = true;
        for(MessageEnumeration me : new ArrayList<MessageEnumeration>(_enumerations))
        {
            me.close();
        }
    }

    private final class MessageEnumeration implements Enumeration<MessageImpl>
    {
        private Receiver _receiver;
        private MessageImpl _nextElement;
        private boolean _needNext = true;

        MessageEnumeration() throws JMSException
        {
            try
            {
                _receiver = _session.getClientSession().createReceiver(_session.toAddress(_queue),
                        StdDistMode.COPY,
                        AcknowledgeMode.AMO, null,
                        false,
                        _filters, null);
                _receiver.setCredit(UnsignedInteger.valueOf(100), true);
            }
            catch(ConnectionErrorException e)
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
            _enumerations.add(this);

        }

        public void close()
        {
            _enumerations.remove(this);
            _receiver.close();
            _receiver = null;
        }

        @Override
        public boolean hasMoreElements()
        {
            if( _receiver == null )
            {
                return false;
            }
            if( _needNext )
            {
                _needNext = false;
                _nextElement = createJMSMessage(_receiver.receive(0L));
                if( _nextElement == null )
                {
                    // Drain to verify there really are no more messages.
                    _receiver.drain();
                    _receiver.drainWait();
                    _nextElement = createJMSMessage(_receiver.receive(0L));
                    if( _nextElement == null )
                    {
                        close();
                    }
                    else
                    {
                        // there are still more messages, open up the credit window again..
                        _receiver.clearDrain();
                    }
                }
            }
            return _nextElement != null;
        }

        @Override
        public MessageImpl nextElement()
        {
            if( hasMoreElements() )
            {
                MessageImpl message = _nextElement;
                _nextElement = null;
                _needNext = true;
                return message;
            }
            else
            {
                throw new NoSuchElementException();
            }
        }
    }

    MessageImpl createJMSMessage(final Message msg)
    {
        if(msg != null)
        {
            final MessageImpl message = _session.getMessageFactory().createMessage(_queue, msg);
            message.setFromQueue(true);
            message.setFromTopic(false);
            return message;
        }
        else
        {
            return null;
        }
    }

}
