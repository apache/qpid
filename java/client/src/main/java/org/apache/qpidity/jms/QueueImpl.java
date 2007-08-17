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
        super(name);
        _queueName = name;
        _destinationName = name;
        _exchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        _exchangeType = ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
        _isAutoDelete = false;
        _isDurable = true;
        _isExclusive = false;
        registerQueue(session, false);
    }

    /**
     * Create a destiantion from a binding URL
     *
     * @param session The session used to create this queue.
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    protected QueueImpl(SessionImpl session, BindingURL binding) throws QpidException
    {
        super(binding);
        registerQueue(session, false);
    }

    /**
     * Create a destiantion from a binding URL
     *
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    public QueueImpl(BindingURL binding) throws QpidException
    {
        super(binding);
    }

    /**
     * Create a new QueueImpl with a given name.
     *
     * @param name The name of this queue.
     * @throws QpidException If the queue name is not valid
     */
    public QueueImpl(String name) throws QpidException
    {
        super(name);
        _queueName = name;
        _destinationName = name;
        _exchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        _exchangeType = ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
        _isAutoDelete = false;
        _isDurable = true;
        _isExclusive = false;
    }

    //---- Interface javax.jms.Queue
    /**
     * Gets the name of this queue.
     *
     * @return This queue's name.
     */
    public String getQueueName() throws JMSException
    {
        return _queueName;
    }

    //---Private methods
    /**
     * Check that this queue exists and declare it if required.
     *
     * @param session The session used to create this destination
     * @param declare Specify whether the queue should be declared
     * @throws QpidException If this queue does not exists on the broker.
     */
    protected void registerQueue(SessionImpl session, boolean declare) throws QpidException
    {
        // test if this exchange exist on the broker
        //todo we can also specify if the excahnge is autodlete and durable
        session.getQpidSession().exchangeDeclare(_exchangeName, _exchangeType, null, null, Option.PASSIVE);
        // wait for the broker response
        session.getQpidSession().sync();
        // If this exchange does not exist then we will get an Expection 404 does notexist
        //todo check for an execption
        // now check if the queue exists
        session.getQpidSession().queueDeclare(_queueName, null, null, _isDurable ? Option.DURABLE : Option.NO_OPTION,
                                              _isAutoDelete ? Option.AUTO_DELETE : Option.NO_OPTION,
                                              _isExclusive ? Option.EXCLUSIVE : Option.NO_OPTION,
                                              declare ? Option.PASSIVE : Option.NO_OPTION);
        // wait for the broker response
        session.getQpidSession().sync();
        // If this queue does not exist then we will get an Expection 404 does notexist
        session.getQpidSession().queueBind(_queueName, _exchangeName, _destinationName, null);
        // we don't have to sync as we don't expect an error
    }

}
