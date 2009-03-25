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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class QueueEntryImplThreadingTest extends TestCase
{
    private volatile boolean _running;
    private volatile Error _error;
    ReentrantLock _waitLock = new ReentrantLock();
    private final String _testName = this.getName();

    /** Test the Redelivered state of a QueueEntryImpl */
    public void test() throws AMQException, InterruptedException
    {
        final MockAMQMessage message = new MockAMQMessage(1L);

        ((BasicContentHeaderProperties) message.getContentHeaderBody().properties).setAppId(_testName);

        final QueueEntry entry = new MockQueueEntry(message);

        Runnable unloader = new Runnable()
        {
            public void run()
            {
                try
                {

                    while (_running)
                    {
                        entry.unload();
                    }
                }
                catch (Error e)
                {
                    signalError("Error whilst Unloading:", e);
                }
            }
        };

        Runnable loader = new Runnable()
        {
            public void run()
            {
                try
                {
                    while (_running)
                    {
                        AMQMessage loaded = entry.load();
                        assertNotNull("Entry Returned Null Message.", loaded);
                        assertEquals("Message ID not correct on loaded message.", message.getMessageId(),
                                     loaded.getMessageId());
                        assertEquals("Message Arrival time not correctly retrieved.", message.getArrivalTime(),
                                     loaded.getArrivalTime());
                        assertEquals("Custom AppId not correctly retrieved.", _testName,
                                     ((BasicContentHeaderProperties) loaded.getContentHeaderBody().properties).
                                             getAppIdAsString());
                    }
                }
                catch (Error e)
                {
                    signalError("Error whilst Loading:", e);
                }
            }
        };

        _running = true;
        //Start a couple of unloaders
        Thread unloadThread = new Thread(unloader);
        unloadThread.start();
        Thread unloadThread2 = new Thread(unloader);
        unloadThread2.start();

        //Start a couple of loaders
        Thread loadThread = new Thread(loader);
        loadThread.start();
        Thread loadThread2 = new Thread(loader);
        loadThread2.start();

        //Run for 3 seconds.
        long sleep = 3000;
        Condition wait = _waitLock.newCondition();
        while (sleep > 0 && _error == null)
        {
            try
            {
                _waitLock.lock();

                sleep = wait.awaitNanos(TimeUnit.MILLISECONDS.toNanos(sleep));
            }
            catch (InterruptedException e)
            {
                //Stop if we are interrupted
                fail(e.getMessage());
            }
            finally
            {
                _waitLock.unlock();
            }

        }

        _running = false;

        unloadThread.join();
        unloadThread2.join();

        loadThread.join();
        loadThread2.join();

        if (_error != null)
        {
            throw _error;
        }

    }

    private void signalError(String message, Error e)
    {
        System.err.println(message + e.getMessage());
        e.printStackTrace();
        _error = e;
        try
        {
            _waitLock.lock();
            _waitLock.notify();
        }
        finally
        {
            _waitLock.unlock();
        }

    }

}
