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

import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;

public abstract class OutOfOrderQueue<E extends QueueEntryImpl<E,Q,L>, Q extends OutOfOrderQueue<E,Q,L>, L extends SimpleQueueEntryList<E,Q,L>> extends SimpleAMQQueue<E,Q,L>
{

    protected OutOfOrderQueue(VirtualHost virtualHost,
                              Map<String, Object> attributes,
                              QueueEntryListFactory<E, Q, L> entryListFactory)
    {
        super(virtualHost, attributes, entryListFactory);
    }

    @Override
    protected void checkConsumersNotAheadOfDelivery(final E entry)
    {
        // check that all consumers are not in advance of the entry
        QueueConsumerList.ConsumerNodeIterator<E,Q,L> subIter = getConsumerList().iterator();
        while(subIter.advance() && !entry.isAcquired())
        {
            final QueueConsumer<?,E,Q,L> consumer = subIter.getNode().getConsumer();
            if(!consumer.isClosed())
            {
                QueueContext<E,Q,L> context = consumer.getQueueContext();
                if(context != null)
                {
                    E released = context.getReleasedEntry();
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
