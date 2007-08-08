/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpidity.jms;

import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;

import org.apache.qpidity.client.MessagePartListener;
import org.apache.qpidity.filter.JMSSelectorFilter;
import org.apache.qpidity.filter.MessageFilter;
import org.apache.qpidity.impl.MessagePartListenerAdapter;

/**
 * Implementation of the JMS QueueBrowser interface
 */
public class QueueBrowserImpl extends MessageActor implements QueueBrowser
{
    /**
     * The browsers MessageSelector.
     */
    private String _messageSelector = null;

    /**
     * The message selector filter associated with this browser
     */
    private MessageFilter _filter = null;

    /**
     * The batch of messages to browse.
     */
    private Message[] _messages;

    /**
     * The number of messages read from current batch.
     */
    private int _browsed = 0;

    /**
     * The number of messages received from current batch.
     */
    private int _received = 0;

    /**
     * Indicates whether the last message has been received.
     */
    private int _batchLength;

    /**
     * The batch max size
     */
    private final int _maxbatchlength = 10;

    //--- constructor

    /**
     * Create a QueueBrowser for a specific queue and a given message selector.
     *
     * @param session         The session of this browser.
     * @param queue           The queue name for this browser
     * @param messageSelector only messages with properties matching the message selector expression are delivered.
     * @throws Exception In case of internal problem when creating this browser.
     */
    protected QueueBrowserImpl(SessionImpl session, Queue queue, String messageSelector) throws Exception
    {
        super(session, (DestinationImpl) queue);
        // this is an array representing a batch of messages for this browser.
        _messages = new Message[_maxbatchlength];
        if (messageSelector != null)
        {
            _messageSelector = messageSelector;
            _filter = new JMSSelectorFilter(messageSelector);
        }
        MessagePartListener messageAssembler = new MessagePartListenerAdapter(new QpidBrowserListener(this));
        // this is a queue we expect that this queue exists
        getSession().getQpidSession()
                .messageSubscribe(queue.getQueueName(), getMessageActorID(),
                                  org.apache.qpidity.client.Session.CONFIRM_MODE_NOT_REQUIRED,
                                  // We do not acquire those messages
                                  org.apache.qpidity.client.Session.ACQUIRE_MODE_NO_ACQUIRE, messageAssembler, null);

    }

    //--- javax.jms.QueueBrowser API
    /**
     * Get an enumeration for browsing the current queue messages in the order they would be received.
     *
     * @return An enumeration for browsing the messages
     * @throws JMSException If  getting the enumeration for this browser fails due to some internal error.
     */
    public Enumeration getEnumeration() throws JMSException
    {
        requestMessages();
        return new MessageEnumeration();
    }


    /**
     * Get the queue associated with this queue browser.
     *
     * @return The queue associated with this queue browser.
     * @throws JMSException If getting the queue associated with this browser failts due to some internal error.
     */
    public Queue getQueue() throws JMSException
    {
        checkNotClosed();
        return (Queue) _destination;
    }

    /**
     * Get this queue browser's message selector expression.
     *
     * @return This queue browser's message selector, or null if no message selector exists.
     * @throws JMSException if getting the message selector for this browser fails due to some internal error.
     */
    public String getMessageSelector() throws JMSException
    {
        checkNotClosed();
        return _messageSelector;
    }

    //-- overwritten methods.  
    /**
     * Closes the browser and deregister it from its session.
     *
     * @throws JMSException if the MessaeActor cannot be closed due to some internal error.
     */
    public void close() throws JMSException
    {
        synchronized (_messages)
        {
            _received = 0;
            _browsed = 0;
            _batchLength = 0;
            _messages.notify();
        }
        super.close();
    }

    //-- nonpublic methods
    /**
     * Request _maxbatchlength messages
     *
     * @throws JMSException If requesting more messages fails due to some internal error.
     */
    private void requestMessages() throws JMSException
    {
        _browsed = 0;
        _received = 0;
        // request messages
        int received = 0;
        getSession().getQpidSession()
        .messageFlow(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE,
                     _maxbatchlength);
        _batchLength = 0; //getSession().getQpidSession().messageFlush(getMessageActorID());
    }

    /**
     * This method is invoked by the listener when a message is dispatched to this browser.
     *
     * @param m A received message
     */
    protected void receiveMessage(Message m)
    {
        synchronized (_messages)
        {
            _messages[_received] = m;
            _received++;
            _messages.notify();
        }
    }

    //-- inner class
    /**
     * This is an implementation of the Enumeration interface.
     */
    private class MessageEnumeration implements Enumeration
    {
        /*
        * Whether this enumeration has any more elements.
        *
        * @return True if there any more elements.
        */
        public boolean hasMoreElements()
        {
            boolean result = false;
            // Try to work out whether there are any more messages available.
            try
            {
                if (_browsed >= _maxbatchlength)
                {
                    requestMessages();
                }
                synchronized (_messages)
                {
                    while (_received == _browsed && _batchLength > _browsed)
                    {
                        // we expect more messages
                        _messages.wait();
                    }
                    if (_browsed < _received && _batchLength != _browsed)
                    {
                        result = true;
                    }
                }
            }
            catch (Exception e)
            {
                // If no batch could be returned, the result should be false, therefore do nothing
            }
            return result;
        }

        /**
         * Get the next message element
         *
         * @return The next element.
         */
        public Object nextElement()
        {
            if (hasMoreElements())
            {
                synchronized (_messages)
                {
                    Message message = _messages[_browsed];
                    _browsed = _browsed + 1;
                    return message;
                }
            }
            else
            {
                throw new NoSuchElementException();
            }
        }
    }

}
