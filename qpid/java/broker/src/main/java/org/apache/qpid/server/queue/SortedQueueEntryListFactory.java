package org.apache.qpid.server.queue;

public class SortedQueueEntryListFactory implements QueueEntryListFactory
{

    private final String _propertyName;

    public SortedQueueEntryListFactory(final String propertyName)
    {
        _propertyName = propertyName;
    }

    @Override
    public QueueEntryList<SortedQueueEntryImpl> createQueueEntryList(final AMQQueue queue)
    {
        return new SortedQueueEntryList(queue, _propertyName);
    }

}
