package org.apache.qpid.server.queue;

import java.util.Map;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class SortedQueue extends OutOfOrderQueue
{
    private String _sortedPropertyName;

    protected SortedQueue(final String name, final boolean durable,
                            final String owner, final boolean autoDelete, final boolean exclusive,
                            final VirtualHost virtualHost, Map<String, Object> arguments, String sortedPropertyName)
    {
        super(name, durable, owner, autoDelete, exclusive, virtualHost,
                new SortedQueueEntryListFactory(sortedPropertyName), arguments);
        this._sortedPropertyName = sortedPropertyName;
    }

    public String getSortedPropertyName()
    {
        return _sortedPropertyName;
    }

    public synchronized void enqueue(ServerMessage message, PostEnqueueAction action) throws AMQException
    {
        super.enqueue(message, action);
    }
}