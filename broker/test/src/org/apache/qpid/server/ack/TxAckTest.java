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

import junit.framework.JUnit4TestAdapter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.store.TestableMemoryMessageStore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

public class TxAckTest
{
    private Scenario individual;
    private Scenario multiple;
    private Scenario combined;

    @Before
    public void setup() throws Exception
    {   
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

    @Test
    public void prepare() throws AMQException
    {
        individual.prepare();
        multiple.prepare();
        combined.prepare();
    }

    @Test
    public void undoPrepare() throws AMQException
    {
        individual.undoPrepare();
        multiple.undoPrepare();
        combined.undoPrepare();
    }

    @Test
    public void commit() throws AMQException
    {
        individual.commit();
        multiple.commit();
        combined.commit();
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TxAckTest.class);
    }

    private class Scenario
    {
        private final LinkedHashMap<Long, UnacknowledgedMessage> _messages = new LinkedHashMap<Long, UnacknowledgedMessage>();
        private final UnacknowledgedMessageMap _map = new UnacknowledgedMessageMapImpl(_messages, _messages);
        private final TxAck _op = new TxAck(_map);
        private final List<Long> _acked;
        private final List<Long> _unacked;

        Scenario(int messageCount, List<Long> acked, List<Long> unacked)
        {
            for(int i = 0; i < messageCount; i++)
            {
                long deliveryTag = i + 1;
                _messages.put(deliveryTag, new UnacknowledgedMessage(null, new TestMessage(deliveryTag), null, deliveryTag));
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
            for(long tag : tags)
            {
                UnacknowledgedMessage u = _messages.get(tag);
                assertTrue("Message not found for tag " + tag, u != null);
                ((TestMessage) u.message).assertCountEquals(expected);
            }
        }

        void prepare() throws AMQException
        {
            _op.consolidate();
            _op.prepare();

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
            _op.commit();
            

            //check acked messages are removed from map
            HashSet<Long> keys = new HashSet<Long>(_messages.keySet());
            keys.retainAll(_acked);
            assertTrue("Expected messages with following tags to have been removed from map: " + keys, keys.isEmpty());
            //check unacked messages are still in map
            keys = new HashSet<Long>(_unacked);
            keys.removeAll(_messages.keySet());
            assertTrue("Expected messages with following tags to still be in map: " + keys, keys.isEmpty());
        }
    }

    private class TestMessage extends AMQMessage
    {
        private final long _tag;
        private int _count;

        TestMessage(long tag)
        {
            super(new TestableMemoryMessageStore(), null);
            _tag = tag;
        }

        public void incrementReference()
        {
            _count++;
        }

        public void decrementReference()
        {
            _count--;
        }

        void assertCountEquals(int expected)
        {
            assertEquals("Wrong count for message with tag " + _tag, expected, _count);
        }
    }
}
