package org.apache.qpid.server.queue;

import java.util.List;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import junit.framework.TestCase;

public class SimpleAMQQueueTest extends TestCase
{

    private SimpleAMQQueue _queue;
    private MessageStore store = new TestableMemoryMessageStore();
    private TransactionalContext ctx = new NonTransactionalContext(store, new StoreContext(), null, null);
    private MessageHandleFactory factory = new MessageHandleFactory();

    MessagePublishInfo info = new MessagePublishInfo()
    {

        public AMQShortString getExchange()
        {
            return null;
        }

        public void setExchange(AMQShortString exchange)
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isImmediate()
        {
            return false;
        }

        public boolean isMandatory()
        {
            return false;
        }

        public AMQShortString getRoutingKey()
        {
            return null;
        }
    };

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        AMQShortString qname = new AMQShortString("qname");
        AMQShortString owner = new AMQShortString("owner");
        _queue = new SimpleAMQQueue(qname, false, owner, false, new VirtualHost("vhost", store));
    }

    public void testGetFirstMessageId() throws Exception
    {
        // Create message
        Long messageId = new Long(23);
        AMQMessage message = new TestMessage(messageId, messageId, info, new StoreContext());

        // Put message on queue
        _queue.enqueue(null, message);
        // Get message id
        Long testmsgid = _queue.getMessagesOnTheQueue(1).get(0);

        // Check message id
        assertEquals("Message ID was wrong", messageId, testmsgid);
    }

    public void testGetFirstFiveMessageIds() throws Exception
    {
        for (int i = 0 ; i < 5; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = new TestMessage(messageId, messageId, info, new StoreContext());
            // Put message on queue
            _queue.enqueue(null, message);
        }
        // Get message ids
        List<Long> msgids = _queue.getMessagesOnTheQueue(5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(i);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }

    public void testGetLastFiveMessageIds() throws Exception
    {
        for (int i = 0 ; i < 10; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = new TestMessage(messageId, messageId, info, new StoreContext());
            // Put message on queue
            _queue.enqueue(null, message);
        }
        // Get message ids
        List<Long> msgids = _queue.getMessagesOnTheQueue(5, 5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(i+5);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }


    // FIXME: move this to somewhere useful
    private static AMQMessageHandle createMessageHandle(final long messageId, final MessagePublishInfo publishBody)
    {
        final AMQMessageHandle amqMessageHandle = (new MessageHandleFactory()).createMessageHandle(messageId,
                                                                                                   null,
                                                                                                   false);
        try
        {
            amqMessageHandle.setPublishAndContentHeaderBody(new StoreContext(),
                                                              publishBody,
                                                              new ContentHeaderBody()
            {
                public int getSize()
                {
                    return 1;
                }
            });
        }
        catch (AMQException e)
        {
            // won't happen
        }


        return amqMessageHandle;
    }

    public class TestMessage extends AMQMessage
    {
        private final long _tag;
        private int _count;

        TestMessage(long tag, long messageId, MessagePublishInfo publishBody, StoreContext storeContext)
                throws AMQException
        {
            super(createMessageHandle(messageId, publishBody), storeContext, publishBody);
            _tag = tag;
        }


        public boolean incrementReference()
        {
            _count++;
            return true;
        }

        public void decrementReference(StoreContext context)
        {
            _count--;
        }

        void assertCountEquals(int expected)
        {
            assertEquals("Wrong count for message with tag " + _tag, expected, _count);
        }
    }
}
