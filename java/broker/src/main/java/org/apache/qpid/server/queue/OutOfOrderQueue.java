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

import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionList;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;
import java.util.UUID;

public abstract class OutOfOrderQueue extends SimpleAMQQueue
{

    protected OutOfOrderQueue(UUID id, String name, boolean durable,
                              String owner, boolean autoDelete, boolean exclusive,
                              VirtualHost virtualHost, QueueEntryListFactory entryListFactory, Map<String, Object> arguments)
    {
        super(id, name, durable, owner, autoDelete, exclusive, virtualHost, entryListFactory, arguments);
    }

    @Override
    protected void checkSubscriptionsNotAheadOfDelivery(final QueueEntry entry)
    {
        // check that all subscriptions are not in advance of the entry
        SubscriptionList.SubscriptionNodeIterator subIter = getSubscriptionList().iterator();
        while(subIter.advance() && !entry.isAcquired())
        {
            final Subscription subscription = subIter.getNode().getSubscription();
            if(!subscription.isClosed())
            {
                QueueContext context = (QueueContext) subscription.getQueueContext();
                if(context != null)
                {
                    QueueEntry released = context.getReleasedEntry();
                    while(!entry.isAcquired() && (released == null || released.compareTo(entry) > 0))
                    {
                        if(QueueContext._releasedUpdater.compareAndSet(context,released,entry))
                        {
                            break;
                        }
                        else
                        {
                            released = context.getReleasedEntry();
                        }
                    }
                }
            }
        }
    }

}
