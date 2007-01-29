package org.apache.qpid.server.handler;

import org.apache.qpid.framing.*;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;

public class QueuePurgeHandler implements StateAwareMethodListener<QueuePurgeBody>
{
    private static final QueuePurgeHandler _instance = new QueuePurgeHandler();

    public static QueuePurgeHandler getInstance()
    {
        return _instance;
    }

    private final boolean _failIfNotFound;

    public QueuePurgeHandler()
    {
        this(true);
    }

    public QueuePurgeHandler(boolean failIfNotFound)
    {
        _failIfNotFound = failIfNotFound;
    }

    public void methodReceived(AMQProtocolSession session, AMQMethodEvent<QueuePurgeBody> evt) throws AMQException
    {
        QueueRegistry queueRegistry = session.getQueueRegistry();

        QueuePurgeBody body = evt.getMethod();
        AMQQueue queue;
        if(body.queue == null)
        {
            queue = session.getChannel(evt.getChannelId()).getDefaultQueue();
            if(queue == null)
            {
                if(_failIfNotFound)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED.getCode(),"No queue specified.");
                }

            }
        }
        else
        {
            queue = queueRegistry.getQueue(body.queue);
        }

        if(queue == null)
        {
            if(_failIfNotFound)
            {
                throw body.getChannelException(404, "Queue " + body.queue + " does not exist.");
            }
        }
        else
        {
            long purged = queue.clearQueue();
            
            
            if(!body.nowait)
            {
                AMQMethodBody response 
                    = QueuePurgeOkBody.createMethodBody(session.getMajor(), session.getMinor(), purged);
                session.writeResponse(evt, response);

            }
        }
    }
}
