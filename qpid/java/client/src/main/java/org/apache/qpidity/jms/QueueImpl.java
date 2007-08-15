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

import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;
import org.apache.qpidity.url.BindingURL;
import org.apache.qpidity.exchange.ExchangeDefaults;

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
     * @throws QpidException If the queue name is not valid
     */
    protected QueueImpl(SessionImpl session, String name) throws QpidException
    {
        super(session, name);
        _exchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        _exchangeClass = ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
        _queueName = name;
        // check that this queue exist on the server
        // As pasive is set the server will not create the queue.
        session.getQpidSession().queueDeclare(name, null, null, Option.PASSIVE);
    }

    /**
     * Create a destiantion from a binding URL
     *
     * @param session  The session used to create this queue.
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    protected QueueImpl(SessionImpl session, BindingURL binding) throws QpidException
    {
        super(session, binding);        
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
