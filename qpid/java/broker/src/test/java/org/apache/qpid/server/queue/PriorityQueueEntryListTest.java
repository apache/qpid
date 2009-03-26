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

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;

public class PriorityQueueEntryListTest extends TestCase
{

    PriorityQueueEntryList _priorityList;
    private static final int PRIORITIES = 10;
    private static final int MAXIMUM_MEMORY_USAGE = 10 * PRIORITIES;

    public void setUp()
    {
        AMQQueue queue = new MockAMQQueue(this.getName());
        _priorityList = new PriorityQueueEntryList(queue, PRIORITIES);

        //Allow 10 bytes per priority level.
        _priorityList.setMemoryUsageMaximum(MAXIMUM_MEMORY_USAGE);
    }

    class Adder implements Runnable
    {
        private int _instance;

        Adder(int instance)
        {
            _instance = instance;
            System.err.println("New Adder:" + instance);
        }

        public void run()
        {
            AMQMessage message;

            //Send enough messages to fill all levels of the queue
            for (int count = 0; count < MAXIMUM_MEMORY_USAGE / PRIORITIES*2; count++)
            {
                try
                {
                    message = new MockAMQMessage(count * _instance);

                    //Set the priority level
                    ((BasicContentHeaderProperties) message.getContentHeaderBody().properties).setPriority((byte) (count % PRIORITIES));

                    //Set the size of the body
                    message.getContentHeaderBody().bodySize = 1L;

                    _priorityList.add(message);
                }
                catch (AMQException e)
                {
                    // Should not occur
                }
            }
        }
    }

    public void test() throws AMQException, InterruptedException
    {
        Thread[] adders = new Thread[PRIORITIES];

        // Create Asynchrounous adders
        for (int count = 0; count < PRIORITIES; count++)
        {
            adders[count] = new Thread(new Adder(count + 1));
        }

        // Create Asynchrounous adders
        for (int count = 0; count < PRIORITIES; count++)
        {
            adders[count].start();
        }

        // Wait for completion
        for (int count = 0; count < PRIORITIES; count++)
        {
            try
            {
                adders[count].join();
            }
            catch (InterruptedException e)
            {
                //ignore
            }
        }

        _priorityList.showUsage("Done Threads");

        // Give the purger time to run.
        Thread.yield();
        Thread.sleep(500);

        _priorityList.showUsage("After Sleep");

        assertTrue("Queue should now be flowed", _priorityList.isFlowed());
        //+1 for the extra message
        assertEquals(MAXIMUM_MEMORY_USAGE * 2, _priorityList.dataSize());
        assertEquals("Queue should not contain more memory than the maximum.",MAXIMUM_MEMORY_USAGE , _priorityList.memoryUsed());

    }
}
