package org.apache.qpid.server.exchange;

import static org.mockito.Mockito.mock;

import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.queue.AMQQueue;

public class FanoutExchangeTest extends TestCase
{
    private FanoutExchange _exchange;

    public void setUp()
    {
        _exchange = new FanoutExchange();
    }

    public void testIsBoundAMQShortStringFieldTableAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQShortString,FieldTable,AMQQueue) with null queue should return false",
                _exchange.isBound((AMQShortString) null, (FieldTable) null, (AMQQueue) null));
    }

    public void testIsBoundAMQShortStringAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQShortString,AMQQueue) with null queue should return false",
                _exchange.isBound((AMQShortString) null, (AMQQueue) null));
    }

    public void testIsBoundAMQQueueWhenQueueIsNull()
    {
        assertFalse("calling isBound(AMQQueue) with null queue should return false", _exchange.isBound((AMQQueue) null));
    }

    public void testIsBoundAMQShortStringFieldTableAMQQueue()
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound((AMQShortString) null, (FieldTable) null, queue));
    }

    public void testIsBoundAMQShortStringAMQQueue()
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound((AMQShortString) null, queue));
    }

    public void testIsBoundAMQQueue()
    {
        AMQQueue queue = bindQueue();
        assertTrue("Should return true for a bound queue",
                _exchange.isBound(queue));
    }

    private AMQQueue bindQueue()
    {
        AMQQueue queue = mock(AMQQueue.class);
        _exchange.addBinding(new Binding(UUID.randomUUID(), "does not matter", queue, _exchange, null));
        return queue;
    }
}
