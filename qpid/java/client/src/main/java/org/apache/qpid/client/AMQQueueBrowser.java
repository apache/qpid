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
package org.apache.qpid.client;

import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.client.filter.JMSSelectorFilter;
import org.apache.qpid.protocol.AMQConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AMQQueueBrowser implements QueueBrowser
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQQueueBrowser.class);

    private AtomicBoolean _isClosed = new AtomicBoolean();
    private final AMQSession _session;
    private final Queue _queue;
    private final ArrayList<BasicMessageConsumer> _consumers = new ArrayList<BasicMessageConsumer>();
    private final String _messageSelector;

    AMQQueueBrowser(AMQSession session, Queue queue, String messageSelector) throws JMSException
    {
        _session = session;
        _queue = queue;
        _messageSelector = ((messageSelector == null) || (messageSelector.trim().length() == 0)) ? null : messageSelector;


        validateQueue((AMQDestination) queue);

        if(_messageSelector != null)
        {
            validateSelector(_messageSelector);
        }
    }

    private void validateSelector(String messageSelector) throws InvalidSelectorException
    {
        try
        {
            new JMSSelectorFilter(messageSelector);
        }
        catch (AMQInternalException e)
        {
            throw new InvalidSelectorException(e.getMessage());
        }
    }

    private void validateQueue(AMQDestination queue) throws JMSException
    {
        try
        {
            // Essentially just test the connection/session is still active
            _session.sync();
            // TODO - should really validate queue exists, but we often rely on creating the consumer to create the queue :(
            // _session.declareQueuePassive( queue );
        }
        catch (AMQException e)
        {
            if(e.getErrorCode() == AMQConstant.NOT_FOUND)
            {
                throw new InvalidDestinationException(e.getMessage());
            }
            else
            {
                final JMSException jmsException = new JMSException(e.getMessage(), String.valueOf(e.getErrorCode().getCode()));
                jmsException.setLinkedException(e);
                throw jmsException;
            }
        }
    }

    public Queue getQueue() throws JMSException
    {
        checkState();

        return _queue;
    }

    private void checkState() throws JMSException
    {
        if (_isClosed.get())
        {
            throw new IllegalStateException("Queue Browser");
        }

        if (_session.isClosed())
        {
            throw new IllegalStateException("Session is closed");
        }

    }

    public String getMessageSelector() throws JMSException
    {

        checkState();

        return _messageSelector;
    }

    public Enumeration getEnumeration() throws JMSException
    {
        checkState();
        final BasicMessageConsumer consumer =
                (BasicMessageConsumer) _session.createBrowserConsumer(_queue, _messageSelector, false);

        _consumers.add(consumer);

        return new QueueBrowserEnumeration(consumer);
    }

    public void close() throws JMSException
    {
        for (BasicMessageConsumer consumer : _consumers)
        {
            consumer.close();
        }

        _consumers.clear();
    }

    private class QueueBrowserEnumeration implements Enumeration
    {
        private Message _nextMessage;
        private BasicMessageConsumer _consumer;

        public QueueBrowserEnumeration(BasicMessageConsumer consumer) throws JMSException
        {
            if (consumer != null)
            {
                _consumer = consumer;
                prefetchMessage();
            }
            _logger.debug("QB:created with first element:" + _nextMessage);
        }

        public boolean hasMoreElements()
        {
            _logger.debug("QB:hasMoreElements:" + (_nextMessage != null));
            return (_nextMessage != null);
        }

        public Object nextElement()
        {
            Message msg = _nextMessage;
            if (msg == null)
            {
                throw new NoSuchElementException("No messages") ;
            }
            try
            {
                _logger.debug("QB:nextElement about to receive");
                prefetchMessage();
                _logger.debug("QB:nextElement received:" + _nextMessage);
            }
            catch (JMSException e)
            {
                _logger.warn("Exception caught while queue browsing", e);
                _nextMessage = null;
                try
                {
                    closeConsumer() ;
                }
                catch (final JMSException jmse) {} // ignore
            }
            return msg;
        }

        private void prefetchMessage() throws JMSException
        {
            _nextMessage = _consumer.receiveBrowse();
            if (_nextMessage == null)
            {
                closeConsumer() ;
            }
        }

        private void closeConsumer() throws JMSException
        {
            if (_consumer != null)
            {
                BasicMessageConsumer consumer = _consumer ;
                _consumer = null ;
                consumer.close() ;
            }
        }
    }
}
