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

import javax.jms.Queue;
import javax.jms.JMSException;

/**
 * Implementation of the JMS Queue interface
 */
public class QueueImpl extends DestinationImpl implements Queue
{

    //--- Constructor
    /**
     * Create a new QueueImpl with a given name.
     *
     * @param name    The name of this queue.
     * @param session The session used to create this queue.
     * @throws JMSException If the queue name is not valid
     */
    protected QueueImpl(SessionImpl session, String name) throws JMSException
    {
        super(session, name);
    }

    //---- Interface javax.jms.Queue

    /**
     * Gets the name of this queue.
     *
     * @return This queue's name.
     */
    public String getQueueName() throws JMSException
    {
        return super.getName();
    }
}
