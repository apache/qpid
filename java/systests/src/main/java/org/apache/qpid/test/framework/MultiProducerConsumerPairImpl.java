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
package org.apache.qpid.test.framework;

import javax.jms.*;

/**
 * Multiple producers and consumers made to look like a single producer and consumer. All methods repeated accross
 * all producers and consumers.
 */
public class MultiProducerConsumerPairImpl implements CircuitEnd
{

    /**
     * Gets the message producer at this circuit end point.
     *
     * @return The message producer at with this circuit end point.
     */
    public MessageProducer getProducer()
    {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Gets the message consumer at this circuit end point.
     *
     * @return The message consumer at this circuit end point.
     */
    public MessageConsumer getConsumer()
    {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Send the specified message over the producer at this end point.
     *
     * @param message The message to send.
     * @throws javax.jms.JMSException Any JMS exception occuring during the send is allowed to fall through.
     */
    public void send(Message message) throws JMSException
    {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Gets the JMS Session associated with this circuit end point.
     *
     * @return The JMS Session associated with this circuit end point.
     */
    public Session getSession()
    {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Closes the message producers and consumers and the sessions, associated with this circuit end point.
     *
     * @throws javax.jms.JMSException Any JMSExceptions occurring during the close are allowed to fall through.
     */
    public void close() throws JMSException
    {
        throw new RuntimeException("Not implemented.");
    }
}
