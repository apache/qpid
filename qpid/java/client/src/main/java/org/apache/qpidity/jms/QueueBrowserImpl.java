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

import javax.jms.QueueBrowser;
import javax.jms.JMSException;
import javax.jms.Queue;
import java.util.Enumeration;

/**
 * Implementation of the JMS QueueBrowser interface
 */
public class QueueBrowserImpl extends MessageActor implements QueueBrowser
{
    /**
     * The browsers MessageSelector.
     */
    private String _messageSelector = null;

    //--- constructor

    /**
     * Create a QueueBrowser for a specific queue and a given message selector.
     *
     * @param session         The session of this browser.
     * @param queue           The queue name for this browser
     * @param messageSelector only messages with properties matching the message selector expression are delivered.
     * @throws JMSException In case of internal problem when creating this browser.
     */
    protected QueueBrowserImpl(SessionImpl session, Queue queue, String messageSelector) throws JMSException
    {
        super(session, (DestinationImpl) queue);
        _messageSelector = messageSelector;
        //-- TODO: Create the QPid browser
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
        // TODO
        return null;
    }

    /**
     * Get the queue associated with this queue browser.
     *
     * @return The queue associated with this queue browser.
     * @throws JMSException If getting the queue associated with this browser failts due to some internal error.
     */
    public Queue getQueue() throws JMSException
    {
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
        return _messageSelector;
    }
}
