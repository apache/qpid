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
package org.apache.qpid.server.protocol.v0_8;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;

import junit.framework.TestCase;

import org.apache.qpid.server.message.MessageInstance;

public class UnacknowledgedMessageMapTest extends TestCase
{
    public void testDeletedMessagesCantBeAcknowledged()
    {
        UnacknowledgedMessageMap map = new UnacknowledgedMessageMapImpl(100);
        final int expectedSize = 5;
        MessageInstance[] msgs = populateMap(map,expectedSize);
        assertEquals(expectedSize,map.size());
        Collection<MessageInstance> acknowledged = map.acknowledge(100, true);
        assertEquals(expectedSize, acknowledged.size());
        assertEquals(0,map.size());
        for(int i = 0; i < expectedSize; i++)
        {
            assertTrue("Message " + i + " is missing", acknowledged.contains(msgs[i]));
        }

        map = new UnacknowledgedMessageMapImpl(100);
        msgs = populateMap(map,expectedSize);
        // simulate some messages being ttl expired
        when(msgs[2].lockAcquisition()).thenReturn(Boolean.FALSE);
        when(msgs[4].lockAcquisition()).thenReturn(Boolean.FALSE);

        assertEquals(expectedSize,map.size());


        acknowledged = map.acknowledge(100, true);
        assertEquals(expectedSize-2, acknowledged.size());
        assertEquals(0,map.size());
        for(int i = 0; i < expectedSize; i++)
        {
            assertEquals(i != 2 && i != 4, acknowledged.contains(msgs[i]));
        }

    }

    public MessageInstance[] populateMap(final UnacknowledgedMessageMap map, int size)
    {
        MessageInstance[] msgs = new MessageInstance[size];
        for(int i = 0; i < size; i++)
        {
            msgs[i] = createMessageInstance(i);
            map.add((long)i,msgs[i]);
        }
        return msgs;
    }

    private MessageInstance createMessageInstance(final int id)
    {
        MessageInstance instance = mock(MessageInstance.class);
        when(instance.lockAcquisition()).thenReturn(Boolean.TRUE);
        return instance;
    }
}
