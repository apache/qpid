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
package org.apache.qpid.server.ack;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.MessageFactory;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.TransientAMQMessage;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;

import java.util.*;

public class TxAckTest extends TestCase
{
    private Scenario individual;
    private Scenario multiple;
    private Scenario combined;

    protected void setUp() throws Exception
    {
        super.setUp();

        //ack only 5th msg
        individual = new Scenario(10, Arrays.asList(5l), Arrays.asList(1l, 2l, 3l, 4l, 6l, 7l, 8l, 9l, 10l));
        individual.update(5, false);

        //ack all up to and including 5th msg
        multiple = new Scenario(10, Arrays.asList(1l, 2l, 3l, 4l, 5l), Arrays.asList(6l, 7l, 8l, 9l, 10l));
        multiple.update(5, true);

        //leave only 8th and 9th unacked
        combined = new Scenario(10, Arrays.asList(1l, 2l, 3l, 4l, 5l, 6l, 7l, 10l), Arrays.asList(8l, 9l));
        combined.update(3, false);
        combined.update(5, true);
        combined.update(7, true);
        combined.update(2, true);//should be ignored
        combined.update(1, false);//should be ignored
        combined.update(10, false);
    }
    
    @Override
    protected void tearDown() throws Exception
    {
    	individual.stop();
    	multiple.stop();
    	combined.stop();
    }

    public void testPrepare() throws AMQException
    {
        individual.prepare();
        multiple.prepare();
        combined.prepare();
    }

    public void testUndoPrepare() throws AMQException
    {
        individual.undoPrepare();
        multiple.undoPrepare();
        combined.undoPrepare();
    }

    public void testCommit() throws AMQException
    {
        individual.commit();
        multiple.commit();
        combined.commit();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TxAckTest.class);
    }

    private class Scenario
    {
        private final UnacknowledgedMessageMap _map = new UnacknowledgedMessageMapImpl(5000);
        private final TxAck _op = new TxAck(_map);
        private final List<Long> _acked;
        private final List<Long> _unacked;
        private StoreContext _storeContext = new StoreContext();
		private AMQQueue _queue;

        private static final int MESSAGE_SIZE=100;

        Scenario(int messageCount, List<Long> acked, List<Long> unacked) throws Exception
        {
            TransactionalContext txnContext = new NonTransactionalContext(new TestMemoryMessageStore(),
                                                                          _storeContext, null,
                                                                          new LinkedList<RequiredDeliveryException>()
            );
            _queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("test"), false, null, false, new VirtualHost("test", new MemoryMessageStore()),
			                                   null);

            for (int i = 0; i < messageCount; i++)
            {
                long deliveryTag = i + 1;

                MessagePublishInfo info = new MessagePublishInfoImpl();

                AMQMessage message = new TestMessage(deliveryTag, i, info, txnContext.getStoreContext());

                ContentHeaderBody header = new ContentHeaderBody();
                header.bodySize = MESSAGE_SIZE;
                message.setPublishAndContentHeaderBody(_storeContext, info, header);

                _map.add(deliveryTag, _queue.enqueue(new StoreContext(), message));
            }
            _acked = acked;
            _unacked = unacked;
        }

        void update(long deliverytag, boolean multiple)
        {
            _op.update(deliverytag, multiple);
        }

        private void assertCount(List<Long> tags, int expected)
        {
            for (long tag : tags)
            {
                QueueEntry u = _map.get(tag);
                assertTrue("Message not found for tag " + tag, u != null);
                ((TestMessage) u.getMessage()).assertCountEquals(expected);
            }
        }

        void prepare() throws AMQException
        {
            _op.consolidate();
            _op.prepare(_storeContext);

            assertCount(_acked, -1);
            assertCount(_unacked, 0);

        }

        void undoPrepare()
        {
            _op.consolidate();
            _op.undoPrepare();

            assertCount(_acked, 1);
            assertCount(_unacked, 0);
        }

        void commit()
        {
            _op.consolidate();
            _op.commit(_storeContext);

            //check acked messages are removed from map
            Set<Long> keys = new HashSet<Long>(_map.getDeliveryTags());
            keys.retainAll(_acked);
            assertTrue("Expected messages with following tags to have been removed from map: " + keys, keys.isEmpty());
            //check unacked messages are still in map
            keys = new HashSet<Long>(_unacked);
            keys.removeAll(_map.getDeliveryTags());
            assertTrue("Expected messages with following tags to still be in map: " + keys, keys.isEmpty());
        }
        
        public void stop()
        {
        	_queue.stop();
        }
    }

    private static AMQMessage createMessage(final long messageId, final MessagePublishInfo publishBody)
    {
        final AMQMessage amqMessage = (new MessageFactory()).createMessage(messageId,
                                                                           null,
                                                                           false);
        try
        {
            // Safe to use null here as we just created a TransientMessage above
            amqMessage.setPublishAndContentHeaderBody(null, publishBody, new ContentHeaderBody()
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


        return amqMessage;
    }


    private class TestMessage extends TransientAMQMessage
    {
        private final long _tag;
        private int _count;

        TestMessage(long tag, long messageId, MessagePublishInfo publishBody, StoreContext storeContext)
                throws AMQException
        {
            super(createMessage(messageId, publishBody));
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
