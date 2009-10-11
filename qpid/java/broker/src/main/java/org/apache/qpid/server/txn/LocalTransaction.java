package org.apache.qpid.server.txn;

import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TransactionLog;
import org.apache.qpid.AMQException;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

public class LocalTransaction implements Transaction
{
    private final List<Action> _postCommitActions = new ArrayList<Action>();

    private volatile StoreContext _storeContext;
    private TransactionLog _transactionLog;

    public LocalTransaction(TransactionLog transactionLog)
    {
        _transactionLog = transactionLog;
    }

    public void addPostCommitAction(Action postCommitAction)
    {
        _postCommitActions.add(postCommitAction);
    }

    public void dequeue(AMQQueue queue, EnqueableMessage message, Action postCommitAction)
    {
        if(message.isPersistent() && queue.isDurable())
        {
            try
            {

                beginTranIfNecessary();
                _transactionLog.dequeueMessage(_storeContext, queue, message.getMessageNumber());

            }
            catch(AMQException e)
            {
                tidyUpOnError(e);
            }
        }
        _postCommitActions.add(postCommitAction);
    }

    public void dequeue(Collection<QueueEntry> queueEntries, Action postCommitAction)
    {
        try
        {

            for(QueueEntry entry : queueEntries)
            {
                ServerMessage message = entry.getMessage();
                AMQQueue queue = entry.getQueue();
                if(message.isPersistent() && queue.isDurable())
                {
                    beginTranIfNecessary();
                    _transactionLog.dequeueMessage(_storeContext, queue, message.getMessageNumber());
                }

            }
        }
        catch(AMQException e)
        {
            tidyUpOnError(e);
        }
        _postCommitActions.add(postCommitAction);
            
    }

    private void tidyUpOnError(AMQException e)
    {
        try
        {
            for(Action action : _postCommitActions)
            {
                action.onRollback();
            }
        }
        finally
        {
            try
            {
                _transactionLog.abortTran(_storeContext);
            }
            catch (AMQException e1)
            {
                // TODO could try to chain the information to the original error
            }
            _storeContext = null;
            _postCommitActions.clear();
        }

        throw new RuntimeException(e);
    }

    private void beginTranIfNecessary()
    {
        if(_storeContext == null)
        {
            _storeContext = new StoreContext();
            try
            {
                _transactionLog.beginTran(_storeContext);
            }
            catch (AMQException e)
            {
                tidyUpOnError(e);
            }
        }
    }

    public void enqueue(AMQQueue queue, EnqueableMessage message, Action postCommitAction)
    {
        if(message.isPersistent() && queue.isDurable())
        {
            beginTranIfNecessary();
            try
            {
                _transactionLog.enqueueMessage(_storeContext, queue, message.getMessageNumber());
            }
            catch (AMQException e)
            {
                tidyUpOnError(e);
            }
        }
        _postCommitActions.add(postCommitAction);


    }

    public void enqueue(List<AMQQueue> queues, EnqueableMessage message, Action postCommitAction)
    {

        
        if(message.isPersistent())
        {
            if(_storeContext == null)
            {
                for(AMQQueue queue : queues)
                {
                    if(queue.isDurable())
                    {
                        beginTranIfNecessary();
                        break;
                    }
                }


            }


            try
            {
                for(AMQQueue queue : queues)
                {
                    if(queue.isDurable())
                    {
                        _transactionLog.enqueueMessage(_storeContext, queue, message.getMessageNumber());
                    }
                }

            }
            catch (AMQException e)
            {
                tidyUpOnError(e);
            }
        }
        _postCommitActions.add(postCommitAction);


    }

    public void commit()
    {
        try
        {
            if(_storeContext != null)
            {

                _transactionLog.commitTran(_storeContext);
            }

            for(Action action : _postCommitActions)
            {
                action.postCommit();
            }
        }
        catch (AMQException e)
        {
            for(Action action : _postCommitActions)
            {
                action.onRollback();
            }
            //TODO
            throw new RuntimeException(e);
        }
        finally
        {
            _storeContext = null;
            _postCommitActions.clear();
        }

    }

    public void rollback()
    {

        try
        {

            if(_storeContext != null)
            {

                _transactionLog.abortTran(_storeContext);
            }
        }
        catch (AMQException e)
        {
            //TODO
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        finally
        {
            try
            {
                for(Action action : _postCommitActions)
                {
                    action.onRollback();
                }
            }
            finally
            {
                _storeContext = null;
                _postCommitActions.clear();
            }
        }
    }
}
