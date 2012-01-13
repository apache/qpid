/*
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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.ServerMessage;

public class SortedQueueEntryImplTest extends QueueEntryImplTestBase {

    public final static String keys[] = { "CCC", "AAA", "BBB" };

    private SelfValidatingSortedQueueEntryList queueEntryList = new SelfValidatingSortedQueueEntryList(new MockAMQQueue("test"),"KEY");

    public QueueEntryImpl getQueueEntryImpl(int msgId) throws AMQException {
        final ServerMessage message = new MockAMQMessage(msgId, "KEY", keys[msgId-1]);
        return queueEntryList.add(message);
    }

    public void testCompareTo()
    {
        assertTrue(_queueEntry.compareTo(_queueEntry2) > 0);
        assertTrue(_queueEntry.compareTo(_queueEntry3) > 0);

        assertTrue(_queueEntry2.compareTo(_queueEntry3) < 0);
        assertTrue(_queueEntry2.compareTo(_queueEntry) < 0);

        assertTrue(_queueEntry3.compareTo(_queueEntry2) > 0);
        assertTrue(_queueEntry3.compareTo(_queueEntry) < 0);

        assertTrue(_queueEntry.compareTo(_queueEntry) == 0);
        assertTrue(_queueEntry2.compareTo(_queueEntry2) == 0);
        assertTrue(_queueEntry3.compareTo(_queueEntry3) == 0);
    }

    public void testTraverseWithNoDeletedEntries()
    {
        QueueEntry current = _queueEntry2;

        current = current.getNextValidEntry();
        assertSame("Unexpected current entry",_queueEntry3, current);

        current = current.getNextValidEntry();
        assertSame("Unexpected current entry",_queueEntry, current);

        current = current.getNextValidEntry();
        assertNull(current);

    }

    public void testTraverseWithDeletedEntries()
    {
        // Delete 2nd queue entry
        _queueEntry3.delete();
        assertTrue(_queueEntry3.isDeleted());

        QueueEntry current = _queueEntry2;

        current = current.getNextValidEntry();
        assertSame("Unexpected current entry",_queueEntry, current);

        current = current.getNextValidEntry();
        assertNull(current);
    }
}
