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
package org.apache.qpid.nclient.jms;

import javax.jms.QueueReceiver;
import javax.jms.JMSException;
import javax.jms.Queue;

/**
 * Implements javax.jms.QueueReceiver
 */
public class QueueReceiverImpl extends MessageConsumerImpl implements QueueReceiver
{
    //--- Constructor
    /**
     * create a new QueueReceiverImpl.
     *
     * @param session         The session from which the QueueReceiverImpl is instantiated.
     * @param queue           The default queue for this QueueReceiverImpl.
     * @param messageSelector the message selector for this QueueReceiverImpl.  
     * @throws JMSException If the QueueReceiverImpl cannot be created due to some internal error.
     */
    protected QueueReceiverImpl(SessionImpl session, Queue queue, String messageSelector) throws JMSException
    {
        super(session, (DestinationImpl) queue, messageSelector, false, null);
    }

    //--- Interface  QueueReceiver
    /**
     * Get the Queue associated with this queue receiver.
     *
     * @return this receiver's Queue
     * @throws JMSException If getting the queue for this queue receiver fails due to some internal error.
     */
    public Queue getQueue() throws JMSException
    {
        checkNotClosed();
        return (QueueImpl) _destination;
    }
}
