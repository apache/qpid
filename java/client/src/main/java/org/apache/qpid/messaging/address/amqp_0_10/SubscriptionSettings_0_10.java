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

import org.apache.qpid.messaging.SubscriptionSettings;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;

public class SubscriptionSettings_0_10 implements SubscriptionSettings 
{
    String messageSelector;
    String subscriptionTag;
    MessageAcceptMode acceptMode;
    MessageAcquireMode accquireMode;
    Map<String,Object> args;
    
    public String getMessageSelector() 
    {
        return messageSelector;
    }
    
    public void setMessageSelector(String messageSelector) 
    {
        this.messageSelector = messageSelector;
    }
    
    public String getSubscriptionTag() 
    {
        return subscriptionTag;
    }
    
    public void setSubscriptionTag(String subscriptionTag) 
    {
        this.subscriptionTag = subscriptionTag;
    }
    
    public MessageAcceptMode getAcceptMode() 
    {
        return acceptMode;
    }
    
    public void setAcceptMode(MessageAcceptMode acceptMode) 
    {
        this.acceptMode = acceptMode;
    }
    
    public MessageAcquireMode getAccquireMode() 
    {
        return accquireMode;
    }
    
    public void setAccquireMode(MessageAcquireMode accquireMode) 
    {
        this.accquireMode = accquireMode;
    }
    
    public Map<String, Object> getArgs() 
    {
        return args;
    }
    
    public void setArgs(Map<String, Object> args) 
    {
        this.args = args;
    }
}
