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
package org.apache.qpid.server.queue;

import org.apache.qpid.test.utils.QpidTestCase;

/**
 *
 * Tests QueueEntry
 *
 */
public class QueueEntryTest extends QpidTestCase
{
    private QueueEntryImpl _queueEntry1 = null;
    private QueueEntryImpl _queueEntry2 = null;
    private QueueEntryImpl _queueEntry3 = null;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        int i = 0;

        SimpleQueueEntryList queueEntryList = new SimpleQueueEntryList(null);
        _queueEntry1 = (QueueEntryImpl) queueEntryList.add(new MockAMQMessage(i++));
        _queueEntry2 = (QueueEntryImpl) queueEntryList.add(new MockAMQMessage(i++));
        _queueEntry3 = (QueueEntryImpl) queueEntryList.add(new MockAMQMessage(i++));
    }

    public void testCompareTo()
    {
        assertTrue(_queueEntry1.compareTo(_queueEntry2) < 0);
        assertTrue(_queueEntry2.compareTo(_queueEntry1) > 0);
        assertTrue(_queueEntry1.compareTo(_queueEntry1) == 0);
    }

    /**
     * Tests that the getNext() can be used to traverse the list.
     */
    public void testTraverseWithNoDeletedEntries()
    {
        QueueEntryImpl current = _queueEntry1;

        current = current.getNext();
        assertSame("Unexpected current entry",_queueEntry2, current);

        current = current.getNext();
        assertSame("Unexpected current entry",_queueEntry3, current);

        current = current.getNext();
        assertNull(current);

    }

    /**
     * Tests that the getNext() can be used to traverse the list but deleted
     * entries are skipped and de-linked from the chain of entries.
     */
    public void testTraverseWithDeletedEntries()
    {
        // Delete 2nd queue entry
        _queueEntry2.delete();
        assertTrue(_queueEntry2.isDeleted());


        QueueEntryImpl current = _queueEntry1;

        current = current.getNext();
        assertSame("Unexpected current entry",_queueEntry3, current);

        current = current.getNext();
        assertNull(current);

        // Assert the side effects of getNext()
        assertSame("Next node of entry 1 should now be entry 3",
                _queueEntry3, _queueEntry1.nextNode());
    }
}
