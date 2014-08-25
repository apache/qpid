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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public abstract class AbstractServerMessageImpl<X extends AbstractServerMessageImpl<X,T>, T extends StorableMessageMetaData> implements ServerMessage<T>
{

    private static final AtomicIntegerFieldUpdater<AbstractServerMessageImpl> _refCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractServerMessageImpl.class, "_referenceCount");

    private static final AtomicReferenceFieldUpdater<AbstractServerMessageImpl, Collection> _resourcesUpdater =
            AtomicReferenceFieldUpdater.newUpdater(AbstractServerMessageImpl.class, Collection.class,"_resources");


    private volatile int _referenceCount = 0;
    private final StoredMessage<T> _handle;
    private final Object _connectionReference;
    private volatile Collection<UUID> _resources;


    public AbstractServerMessageImpl(StoredMessage<T> handle, Object connectionReference)
    {
        _handle = handle;
        _connectionReference = connectionReference;
    }

    public StoredMessage<T> getStoredMessage()
    {
        return _handle;
    }

    private boolean incrementReference()
    {
        if(_refCountUpdater.incrementAndGet(this) <= 0)
        {
            _refCountUpdater.decrementAndGet(this);
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * Thread-safe. This will decrement the reference count and when it reaches zero will remove the message from the
     * message store.
     *
     */
    private void decrementReference()
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
                throw new ServerScopedRuntimeException("Reference count for message id " + debugIdentity()
                                                        + " has gone below 0.");
            }
        }
    }

    public String debugIdentity()
    {
        return "(HC:" + System.identityHashCode(this) + " ID:" + getMessageNumber() + " Ref:" + getReferenceCount() + ")";
    }

    private int getReferenceCount()
    {
        return _referenceCount;
    }

    @Override
    final public MessageReference<X> newReference()
    {
        return new Reference(this);
    }

    @Override
    final public MessageReference<X> newReference(TransactionLogResource object)
    {
        return new Reference(this, object);
    }

    @Override
    final public boolean isReferenced(TransactionLogResource resource)
    {
        Collection<UUID> resources = _resources;
        return resources != null && resources.contains(resource.getId());
    }

    @Override
    final public boolean isReferenced()
    {
        Collection<UUID> resources = _resources;
        return resources != null && !resources.isEmpty();
    }

    @Override
    final public boolean isPersistent()
    {
        return _handle.getMetaData().isPersistent();
    }

    @Override
    final public long getMessageNumber()
    {
        return getStoredMessage().getMessageNumber();
    }

    @Override
    final public int getContent(ByteBuffer buf, int offset)
    {
        return getStoredMessage().getContent(offset, buf);
    }

    @Override
    final public ByteBuffer getContent(int offset, int size)
    {
        return getStoredMessage().getContent(offset, size);
    }

    final public Object getConnectionReference()
    {
        return _connectionReference;
    }public String toString()
    {
        return "Message[" + debugIdentity() + "]";
    }

    private static class Reference<X extends AbstractServerMessageImpl<X,T>, T extends StorableMessageMetaData>
            implements MessageReference<X>
    {

        private static final AtomicIntegerFieldUpdater<Reference> _releasedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(Reference.class, "_released");

        private AbstractServerMessageImpl<X, T> _message;
        private final UUID _resourceId;
        private volatile int _released;

        private Reference(final AbstractServerMessageImpl<X, T> message)
        {
            this(message, null);
        }
        private Reference(final AbstractServerMessageImpl<X, T> message, TransactionLogResource resource)
        {
            _message = message;
            if(resource != null)
            {
                Collection<UUID> currentValue;
                Collection<UUID> newValue;
                _resourceId = resource.getId();
                do
                {
                    currentValue = _message._resources;

                    if(currentValue == null)
                    {
                        newValue = Collections.singleton(_resourceId);
                    }
                    else
                    {
                        if(currentValue.contains(_resourceId))
                        {
                            throw new MessageAlreadyReferencedException(_message.getMessageNumber(), resource);
                        }
                        newValue = new ArrayList<>(currentValue.size()+1);
                        newValue.addAll(currentValue);
                        newValue.add(_resourceId);
                    }

                }
                while(!_resourcesUpdater.compareAndSet(_message, currentValue, newValue));
            }
            else
            {
                _resourceId = null;
            }
            if(!_message.incrementReference())
            {
                throw new MessageDeletedException(message.getMessageNumber());
            }

        }

        public X getMessage()
        {
            return (X) _message;
        }

        public synchronized void release()
        {
            if(_releasedUpdater.compareAndSet(this,0,1))
            {
                if(_resourceId != null)
                {
                    Collection<UUID> currentValue;
                    Collection<UUID> newValue;
                    do
                    {
                        currentValue = _message._resources;
                        if(currentValue.size() == 1)
                        {
                            newValue = null;
                        }
                        else
                        {
                            UUID[] array = new UUID[currentValue.size()-1];
                            int pos = 0;
                            for(UUID uuid : currentValue)
                            {
                                if(!_resourceId.equals(uuid))
                                {
                                    array[pos++] = uuid;
                                }
                            }
                            newValue = Arrays.asList(array);
                        }
                    }
                    while(!_resourcesUpdater.compareAndSet(_message, currentValue, newValue));

                }
                _message.decrementReference();
            }
        }

    }

}
