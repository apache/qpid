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
import java.util.UUID;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SubscriptionSettings;
import org.apache.qpid.messaging.Address.PolicyType;
import org.apache.qpid.messaging.QpidDestination.CheckMode;
import org.apache.qpid.messaging.QpidTopic;
import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.AddressHelper;
import org.apache.qpid.messaging.address.Link;
import org.apache.qpid.messaging.address.Link.Reliability;
import org.apache.qpid.messaging.amqp_0_10.Session_0_10;
import org.apache.qpid.transport.ExchangeQueryResult;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.SessionException;

public class QpidTopic_0_10 extends QpidTopic 
{
    private String exchangeName;
    private Session ssn;
    private Address address;
    private ExchangeNode exchange;
    private Link_0_10 link;
    private String subscriptionQueue;
    
    public QpidTopic_0_10(Address address,ExchangeNode exchange,Link_0_10 link) throws Exception
    {
        if (Reliability.AT_LEAST_ONCE == link.getReliability())
        {
            throw new Exception("AT-LEAST-ONCE is not yet supported for Topics");
        }
        
        this.address = address;
        this.exchange = exchange;
        this.link = link;
        this.exchangeName = address.getName();
        topicName = retrieveTopicName();        
    }

    @Override
    public void checkCreate(Session session,CheckMode mode) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (AddressHelper.isAllowed(exchange.getCreatePolicy(), mode))
        {
            ssn.exchangeDeclare(exchangeName, 
                                exchange.getExchangeType(),
                                exchange.getAltExchange(), 
                                exchange.getDeclareArgs(),
                                exchangeName.toString().startsWith("amq.") ? 
                                Option.PASSIVE : Option.NONE);
        }
        else
        {
            try
            {
                ssn.exchangeDeclare(exchangeName, null, null, null, Option.PASSIVE);
                ssn.sync();
            }
            catch(SessionException e)
            {
                if (e.getException() != null 
                    && e.getException().getErrorCode() == ExecutionErrorCode.NOT_FOUND)
                {
                    throw new AddressException("The exchange '" + exchangeName +"' does not exist",e);
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
        
        if (AddressHelper.isAllowed(exchange.getAssertPolicy(), mode))
        {
            ExchangeQueryResult result = ssn.exchangeQuery(exchangeName, Option.NONE).get();
            boolean match = !result.getNotFound() &&        
            (result.getDurable() == exchange.isDurable()) && 
            (exchange.getExchangeType() != null && exchange.getExchangeType().equals(result.getType())) &&
            (exchange.matchProps(result.getArguments()));
            
            if (!match)
            {
                throw new AddressException("The exchange described by the address does not exist on the broker");
            }
        }
    }

    @Override
    public void checkDelete(Session session,CheckMode mode) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        
        if (AddressHelper.isAllowed(exchange.getDeletePolicy(), mode) &&
           !exchangeName.toString().startsWith("amq."))
        {
            ssn.exchangeDelete(exchangeName, Option.NONE);
            ssn.sync();
        }
    }

    @Override
    public void createSubscription(Session session,SubscriptionSettings settings) throws Exception 
    {
        org.apache.qpid.transport.Session ssn = ((Session_0_10)session).getProtocolSession();
        subscriptionQueue = getSubscriptionQueueName();
        
        // for topics subscriptions queues are exclusive by default.
        boolean exclusive = 
            (link.isQueueExclusive() == null) ? true : link.getSubscription().isExclusive();
        
        // for topics subscriptions are autoDelete by default.
        boolean autoDelete = 
            (link.getSubscription().isExclusive() == null) ? true : link.getSubscription().isExclusive();
        
        ssn.queueDeclare(subscriptionQueue,
                        link.getQueueAltExchange(),
                        link.getQueueDeclareArgs(),
                        autoDelete ? Option.AUTO_DELETE : Option.NONE,
                        link.isDurable() ? Option.DURABLE : Option.NONE,
                        exclusive ? Option.EXCLUSIVE : Option.NONE);
        
        if (link.getQueueBindings().size() > 0)
        {
            for (Binding binding: link.getQueueBindings())
            {
                String queue = binding.getQueue() == null?
                               subscriptionQueue: binding.getQueue();
                        
                String exchange = binding.getExchange() == null ? 
                                  address.getName() :
                                  binding.getExchange();

                ssn.exchangeBind(queue, 
                                 exchange,
                                 binding.getBindingKey(),
                                 binding.getArgs()); 
            }
        }
        else
        {
            String subject = address.getSubject();
            ssn.exchangeBind(subscriptionQueue, 
                             address.getName(),
                             subject == null || subject.trim().equals("") ? "#" : subject,
                             null); 
        }
        
        SubscriptionSettings_0_10 settings_0_10 = (SubscriptionSettings_0_10)settings;
        Map<String,Object> arguments = settings_0_10.getArgs();
        arguments.putAll((Map<? extends String, ? extends Object>)link.getSubscription().getArgs()); 
        
        if (link.getReliability() == Reliability.UNRELIABLE || 
            link.getReliability() == Reliability.AT_MOST_ONCE)
        {
            settings_0_10.setAcceptMode(MessageAcceptMode.NONE);
        }
        
        // for topics subscriptions are exclusive by default.
        boolean exclusiveConsume = 
            (link.getSubscription().isExclusive() == null) ? true : link.getSubscription().isExclusive();
        
        ssn.messageSubscribe(subscriptionQueue, 
                             settings_0_10.getSubscriptionTag(),
                             settings_0_10.getAcceptMode(),
                             settings_0_10.getAccquireMode(),
                             null,  // resume id
                             0, // resume ttl
                             arguments,
                             exclusiveConsume ? Option.EXCLUSIVE : Option.NONE);
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
                        subscriptionQueue: binding.getQueue();
                        
                String exchange = binding.getExchange() == null ? 
                                  "anq.topic" :
                                  binding.getExchange();

                ssn.exchangeUnbind(queue, 
                                   exchange,
                                   binding.getBindingKey(),
                                   Option.NONE); 
            }
        }
        ssn.queueDelete(subscriptionQueue, Option.NONE);
    }
    
    private String getSubscriptionQueueName() 
    {
        if (link.getName() == null)
        {
            return "TempQueue" + UUID.randomUUID();
        }
        else
        {
            return link.getName();
        }
    }

    private String retrieveTopicName() 
    {
        if (address.getSubject() != null && !address.getSubject().trim().equals(""))
        {
            return address.getSubject();
        }
        else if ("topic".equals(exchange.getExchangeType()))
        {
            return "#";
        }
        else
        {
            return "";
        }
    }
    
    public String getSubscriptionQueue()
    {
        return subscriptionQueue;
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
