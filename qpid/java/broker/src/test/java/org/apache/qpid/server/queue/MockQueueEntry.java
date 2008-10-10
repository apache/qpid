package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;

public class MockQueueEntry implements QueueEntry
{

    private AMQMessage _message;

    public boolean acquire()
    {
        return false;
    }

    public boolean acquire(Subscription sub)
    {
        return false;
    }

    public boolean acquiredBySubscription()
    {
        return false;
    }

    public void addStateChangeListener(StateChangeListener listener)
    {

    }

    public String debugIdentity()
    {
        return null;
    }

    public boolean delete()
    {
        return false;
    }

    public void dequeue(StoreContext storeContext) throws FailedDequeueException
    {

    }

    public void discard(StoreContext storeContext) throws FailedDequeueException, MessageCleanupException
    {

    }

    public void dispose(StoreContext storeContext) throws MessageCleanupException
    {

    }

    public boolean expired() throws AMQException
    {
        return false;
    }

    public Subscription getDeliveredSubscription()
    {
        return null;
    }

    public boolean getDeliveredToConsumer()
    {
        return false;
    }

    public AMQMessage getMessage()
    {
        return _message;
    }

    public AMQQueue getQueue()
    {
        return null;
    }

    public long getSize()
    {
        return 0;
    }

    public boolean immediateAndNotDelivered()
    {
        return false;
    }

    public boolean isAcquired()
    {
        return false;
    }

    public boolean isDeleted()
    {
        return false;
    }

    
    public boolean isQueueDeleted()
    {

        return false;
    }

    
    public boolean isRejectedBy(Subscription subscription)
    {

        return false;
    }

    
    public void reject()
    {


    }

    
    public void reject(Subscription subscription)
    {


    }

    
    public void release()
    {


    }

    
    public boolean removeStateChangeListener(StateChangeListener listener)
    {

        return false;
    }

    
    public void requeue(StoreContext storeContext) throws AMQException
    {


    }

    
    public void setDeliveredToSubscription()
    {


    }

    
    public void setRedelivered(boolean b)
    {


    }

    
    public int compareTo(QueueEntry o)
    {

        return 0;
    }

    public void setMessage(AMQMessage msg)
    {
        _message = msg;
    }

}
