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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;


public class QueueEntry
{

    /**
     * Used for debugging purposes.
     */
    private static final Logger _log = Logger.getLogger(QueueEntry.class);

    private final AMQQueue _queue;
    private final AMQMessage _message;

    private Set<Subscription> _rejectedBy = null;

    private AtomicReference<Object> _owner = new AtomicReference<Object>();


    public QueueEntry(AMQQueue queue, AMQMessage message)
    {
        _queue = queue;
        _message = message;
    }


    public AMQQueue getQueue()
    {
        return _queue;
    }

    public AMQMessage getMessage()
    {
        return _message;
    }

    public long getSize()
    {
        return getMessage().getSize();
    }

    public boolean getDeliveredToConsumer()
    {
        return getMessage().getDeliveredToConsumer();
    }

    public boolean expired() throws AMQException
    {
        return getMessage().expired(_queue);
    }

    public boolean isTaken()
    {
        return _owner.get() != null;
    }

    public boolean taken(Subscription sub)
    {
        return !(_owner.compareAndSet(null, sub == null ? this : sub));
    }

    public void setDeliveredToConsumer()
    {
        getMessage().setDeliveredToConsumer();
    }

    public void release()
    {
        _owner.set(null);
    }

    public String debugIdentity()
    {
        return getMessage().debugIdentity();
    }

    public void process(StoreContext storeContext, boolean deliverFirst) throws AMQException
    {
        _queue.process(storeContext, this, deliverFirst);
    }

    public void checkDeliveredToConsumer() throws NoConsumersException
    {
        _message.checkDeliveredToConsumer();
    }

    public void setRedelivered(boolean b)
    {
        getMessage().setRedelivered(true);
    }

    public Subscription getDeliveredSubscription()
    {
        synchronized (this)
        {
            Object owner = _owner.get();
            if (owner instanceof Subscription)
            {
                return (Subscription) owner;
            }
            else
            {
                return null;
            }
        }
    }

    public void reject()
    {
        reject(getDeliveredSubscription());
    }

    public void reject(Subscription subscription)
    {
        if (subscription != null)
        {
            if (_rejectedBy == null)
            {
                _rejectedBy = new HashSet<Subscription>();
            }

            _rejectedBy.add(subscription);
        }
        else
        {
            _log.warn("Requesting rejection by null subscriber:" + debugIdentity());
        }
    }

    public boolean isRejectedBy(Subscription subscription)
    {
        boolean rejected = _rejectedBy != null;

        if (rejected) // We have subscriptions that rejected this message
        {
            return _rejectedBy.contains(subscription);
        }
        else // This messasge hasn't been rejected yet.
        {
            return rejected;
        }
    }


}
