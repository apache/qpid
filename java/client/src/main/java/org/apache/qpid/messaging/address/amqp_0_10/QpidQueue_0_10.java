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
package org.apache.qpid.messaging.address.amqp_0_10;

import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.QpidDestination.CheckMode;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SubscriptionSettings;
import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.AddressHelper;
import org.apache.qpid.messaging.address.Link;
import org.apache.qpid.messaging.address.Link.Reliability;
import org.apache.qpid.messaging.amqp_0_10.Session_0_10;
import org.apache.qpid.messaging.QpidQueue;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.QueueQueryResult;
import org.apache.qpid.transport.SessionException;

public class QpidQueue_0_10 extends QpidQueue 
{
    private Address address;
    private QueueNode queue;
    private Link_0_10 link;
    
    public QpidQueue_0_10(Address address,QueueNode queue,Link_0_10 link) throws Exception
    {
        this.address = address;
        this.queue = queue;
        this.link = link;
        queueName = address.getName();
    }
    
    @Override
    public void checkCreate(Session session,CheckMode mode) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (AddressHelper.isAllowed(queue.getCreatePolicy(), mode))
        {                
            ssn.queueDeclare(queueName, 
                             queue.getAltExchange(), 
                             queue.getDeclareArgs(),
                             queue.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                             queue.isDurable() ? Option.DURABLE : Option.NONE,
                             queue.isExclusive() ? Option.EXCLUSIVE : Option.NONE);
            
            if (queue.getBindings().size() > 0)
            {
                for (Binding binding: queue.getBindings())
                {
                    String queue = binding.getQueue() == null?
                                   queueName: binding.getQueue();
                            
                    String exchange = binding.getExchange() == null ? 
                                      "anq.topic" :
                                      binding.getExchange();
    
                    ssn.exchangeBind(queue, 
                                     exchange,
                                     binding.getBindingKey(),
                                     binding.getArgs()); 
                }
            }
        }
        else
        {
            try
            {
                ssn.queueDeclare(queueName, null, null,Option.PASSIVE);
                ssn.sync();
            }
            catch(SessionException e)
            {
                if (e.getException() != null 
                    && e.getException().getErrorCode() == ExecutionErrorCode.NOT_FOUND)
                {
                    throw new AddressException("The Queue '" + queueName +"' does not exist",e);
                }
                else
                {
                    throw e;
                }
            }    
        }
    }

    @Override
    public void checkAssert(Session session,CheckMode mode) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (AddressHelper.isAllowed(queue.getAssertPolicy(), mode))
        {
            boolean match = false;
            try
            {
                QueueQueryResult result = ssn.queueQuery(queueName, Option.NONE).get();
                
                match = queueName.equals(result.getQueue()) &&
                (result.getDurable() == queue.isDurable()) && 
                (result.getAutoDelete() == queue.isAutoDelete()) &&
                (result.getExclusive() == queue.isExclusive()) &&
                (queue.matchProps(result.getArguments()));
            }
            catch(SessionException e)
            {
                if (e.getException().getErrorCode() == ExecutionErrorCode.RESOURCE_DELETED)
                {
                    match = false;
                }
                else
                {
                    throw new AMQException(AMQConstant.getConstant(e.getException().getErrorCode().getValue()),
                            "Error querying queue",e);
                }
            }        
            if (!match)
            {
                throw new AddressException("The queue described in the address does not exist on the broker");
            }
        }
    }

    @Override
    public void checkDelete(Session session,CheckMode mode) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (AddressHelper.isAllowed(queue.getDeletePolicy(), mode))
        {
            ssn.queueDelete(queueName, Option.NONE);
            ssn.sync();
        }
    }

    @Override
    public void createSubscription(Session session,SubscriptionSettings settings) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (link.getQueueBindings().size() > 0)
        {
            for (Binding binding: link.getQueueBindings())
            {
                String queue = binding.getQueue() == null?
                               queueName: binding.getQueue();
                        
                String exchange = binding.getExchange() == null ? 
                                  "amq.topic" :
                                  binding.getExchange();

                ssn.exchangeBind(queue, 
                                 exchange,
                                 binding.getBindingKey(),
                                 binding.getArgs()); 
            }
        }
        
        SubscriptionSettings_0_10 settings_0_10 = (SubscriptionSettings_0_10)settings;
        Map<String,Object> arguments = settings_0_10.getArgs();
        arguments.putAll((Map<? extends String, ? extends Object>)link.getSubscription().getArgs()); 
                
        if (link.getReliability() == Reliability.UNRELIABLE || 
            link.getReliability() == Reliability.AT_MOST_ONCE)
        {
            settings_0_10.setAcceptMode(MessageAcceptMode.NONE);
        }
        
        // for queues subscriptions are non exclusive by default.
        boolean exclusive = 
            (link.getSubscription().isExclusive() == null) ? false : link.getSubscription().isExclusive(); 
        
        ssn.messageSubscribe(queueName, 
                             settings_0_10.getSubscriptionTag(),
                             settings_0_10.getAcceptMode(),
                             settings_0_10.getAccquireMode(),
                             null,  // resume id
                             0, // resume ttl
                             arguments,
                             exclusive ? Option.EXCLUSIVE : Option.NONE);
    }

    @Override
    public void deleteSubscription(Session session) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (link.getQueueBindings().size() > 0)
        {
            for (Binding binding: link.getQueueBindings())
            {
                String queue = binding.getQueue() == null?
                               queueName: binding.getQueue();
                        
                String exchange = binding.getExchange() == null ? 
                                  "amq.topic" :
                                  binding.getExchange();

                ssn.exchangeUnbind(queue, 
                                   exchange,
                                   binding.getBindingKey(),
                                   Option.NONE); 
            }
        }        
        
        // ideally we should cancel the subscription here 
    }
    
    public String getSubscriptionQueue()
    {
        return queueName;
    }
    
    public long getConsumerCapacity()
    {
        return link.getConsumerCapacity();
    }
    
    public long getProducerCapacity()
    {
        return link.getProducerCapacity();
    }
    
    public String toString()
    {
        return address.toString();
    }    
}
