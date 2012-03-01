package org.apache.qpid.server.message;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.server.store.StoredMessage;

public abstract class AbstractServerMessageImpl implements ServerMessage
{
    private final AtomicInteger _referenceCount = new AtomicInteger(0);
    private final StoredMessage<?> _handle;

    public AbstractServerMessageImpl(StoredMessage<?> handle)
    {
        _handle = handle;
    }

    public boolean incrementReference()
    {
        return incrementReference(1);
    }

    public boolean incrementReference(int count)
    {
        if(_referenceCount.addAndGet(count) <= 0)
        {
            _referenceCount.addAndGet(-count);
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
        int count = _referenceCount.decrementAndGet();

        // note that the operation of decrementing the reference count and then removing the message does not
        // have to be atomic since the ref count starts at 1 and the exchange itself decrements that after
        // the message has been passed to all queues. i.e. we are
        // not relying on the all the increments having taken place before the delivery manager decrements.
        if (count == 0)
        {
            // set the reference count way below 0 so that we can detect that the message has been deleted
            // this is to guard against the message being spontaneously recreated (from the mgmt console)
            // by copying from other queues at the same time as it is being removed.
            _referenceCount.set(Integer.MIN_VALUE/2);

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
        return _referenceCount.get();
    }
}