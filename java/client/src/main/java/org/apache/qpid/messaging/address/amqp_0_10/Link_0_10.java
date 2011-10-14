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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.Link;

public class Link_0_10 extends Link 
{
    private Subscription subscription;
    private List<Binding> bindings;
    
    private Boolean autoDelete = null;    
    private String altExchange;
    private Boolean exclusive = null;
    private Map<String,Object> declareArgs;    
    
    @SuppressWarnings("unchecked")
    public Link_0_10(AddressHelper_0_10 helper) throws AddressException
    {
        super(helper);
        autoDelete = helper.isLinkQueueAutoDelete();
        exclusive = helper.isLinkQueueExclusive();
        altExchange = helper.getLinkQueueAltExchange();
        declareArgs = helper.getLinkQueueDeclareArgs();
        bindings = helper.getLinkQueueBindings();
        subscription = new Subscription();
        subscription.setExclusive(helper.isSubscriptionExclusive());
        subscription.setArgs(helper.getSubscriptionArguments());
    }
    
    public List<Binding> getQueueBindings() 
    {
        return bindings;
    }

    public Boolean isQueueAutoDelete() 
    {
        return autoDelete;
    }

    public String getQueueAltExchange() 
    {
        return altExchange;
    }

    public Boolean isQueueExclusive() 
    {
        return exclusive;
    }

    public Map<String, Object> getQueueDeclareArgs() 
    {
        return declareArgs;
    }

    
    public Subscription getSubscription()
    {
        return this.subscription;
    }    
    
    public static class Subscription
    {
        private Map<String,Object> args = new HashMap<String,Object>();        
        private Boolean exclusive = null;
        
        public Map<String, Object> getArgs()
        {
            return args;
        }
        
        public void setArgs(Map<String, Object> args)
        {
            this.args = args;
        }
        
        public Boolean isExclusive()
        {
            return exclusive;
        }
        
        public void setExclusive(Boolean exclusive)
        {
            this.exclusive = exclusive;
        }
    }
}
