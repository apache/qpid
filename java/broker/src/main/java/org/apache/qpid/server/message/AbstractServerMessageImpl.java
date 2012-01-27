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
package org.apache.qpid.server.message;

import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public abstract class AbstractServerMessageImpl<T extends StorableMessageMetaData> implements ServerMessage<T>
{

    private static final AtomicIntegerFieldUpdater<AbstractServerMessageImpl> _refCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractServerMessageImpl.class, "_referenceCount");

    private volatile int _referenceCount = 0;
    private final StoredMessage<T> _handle;

    public AbstractServerMessageImpl(StoredMessage<T> handle)
    {
        _handle = handle;
    }

    public StoredMessage<T> getStoredMessage()
    {
        return _handle;
    }

    public boolean incrementReference()
    {
        return incrementReference(1);
    }

    public boolean incrementReference(int count)
    {
        if(_refCountUpdater.addAndGet(this, count) <= 0)
        {
            _refCountUpdater.addAndGet(this, -count);
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * Threadsafe. This will decrement the reference count and when it reaches zero will remove the message from the
     * message store.
     *
     *
     * @throws org.apache.qpid.server.queue.MessageCleanupException when an attempt was made to remove the message from the message store and that
     *                                 failed
     */
    public void decrementReference()
    {
        int count = _refCountUpdater.decrementAndGet(this);

        // note that the operation of decrementing the reference count and then removing the message does not
        // have to be atomic since the ref count starts at 1 and the exchange itself decrements that after
        // the message has been passed to all queues. i.e. we are
        // not relying on the all the increments having taken place before the delivery manager decrements.
        if (count == 0)
        {
            // set the reference count way below 0 so that we can detect that the message has been deleted
            // this is to guard against the message being spontaneously recreated (from the mgmt console)
            // by copying from other queues at the same time as it is being removed.
            _refCountUpdater.set(this,Integer.MIN_VALUE/2);

            // must check if the handle is null since there may be cases where we decide to throw away a message
            // and the handle has not yet been constructed
            if (_handle != null)
            {
                _handle.remove();
            }
        }
        else
        {
            if (count < 0)
            {
                throw new RuntimeException("Reference count for message id " + debugIdentity()
                                                  + " has gone below 0.");
            }
        }
    }

    public String debugIdentity()
    {
        return "(HC:" + System.identityHashCode(this) + " ID:" + getMessageNumber() + " Ref:" + getReferenceCount() + ")";
    }

    protected int getReferenceCount()
    {
        return _referenceCount;
    }
}