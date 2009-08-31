package org.apache.qpid.server.txn;

import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.message.ServerMessage;

import java.util.List;
import java.util.ArrayList;

public class LocalTransaction implements Transaction
{
    private final List<Action> _postCommitActions = new ArrayList<Action>();

    public void dequeue(AMQQueue queue, ServerMessage message, Action postCommitAction)
    {
        _postCommitActions.add(postCommitAction);
    }

    public void enqueue(AMQQueue queue, ServerMessage message, Action postCommitAction)
    {
        _postCommitActions.add(postCommitAction);
    }

    public void commit()
    {
        try
        {
            for(Action action : _postCommitActions)
            {
                action.postCommit();
            }
        }
        finally
        {
            _postCommitActions.clear();
        }
    }

    public void rollback()
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
            _postCommitActions.clear();
        }
    }
}
