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
package org.apache.qpid.server.queue;

import org.apache.log4j.Logger;
import org.apache.qpid.server.cluster.util.LogMessage;

import java.util.List;

class ClusteredSubscriptionManager extends SubscriptionSet
{
    private static final Logger _logger = Logger.getLogger(ClusteredSubscriptionManager.class);
    private final NestedSubscriptionManager _all;

    ClusteredSubscriptionManager()
    {
        this(new NestedSubscriptionManager());
    }

    private ClusteredSubscriptionManager(NestedSubscriptionManager all)
    {
        _all = all;
        _all.addSubscription(new Parent());
    }

    NestedSubscriptionManager getAllSubscribers()
    {
        return _all;
    }

    public boolean hasActiveSubscribers()
    {
        return _all.hasActiveSubscribers();
    }

    public Subscription nextSubscriber(AMQMessage msg)
    {
        if(ClusteredQueue.isFromBroker(msg))
        {
            //if message is from another broker, it should only be delivered
            //to another client to meet ordering constraints
            Subscription s = super.nextSubscriber(msg);
            _logger.info(new LogMessage("Returning next *client* subscriber {0}", s));
            if(s == null)
            {
                //TODO: deliver to another broker, but set the redelivered flag on the msg
                //(this should be policy based)

                //for now just don't deliver it
                return null;
            }
            else
            {
                return s;
            }
        }
        Subscription s = _all.nextSubscriber(msg);
        _logger.info(new LogMessage("Returning next subscriber {0}", s));
        return s;
    }

    private class Parent implements WeightedSubscriptionManager
    {
        public int getWeight()
        {
            return ClusteredSubscriptionManager.this.getWeight();
        }

        public List<Subscription> getSubscriptions()
        {
            return ClusteredSubscriptionManager.super.getSubscriptions();
        }

        public boolean hasActiveSubscribers()
        {
            return ClusteredSubscriptionManager.super.hasActiveSubscribers();
        }

        public Subscription nextSubscriber(AMQMessage msg)
        {
            return ClusteredSubscriptionManager.super.nextSubscriber(msg);
        }
    }
}
