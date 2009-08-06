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
package org.apache.qpid.server.logging.actors;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

/**
 * Test : CurrentActorTest
 * Summary:
 * Validate ThreadLocal operation.
 *
 * Test creates THREADS number of threads which all then execute the same test
 * together ( as close as looping Thread.start() will allow).
 *
 * Test:
 * Test sets the CurrentActor then proceeds to retrieve the value and use it.
 *
 * The test also validates that it is the same LogActor that this thread set.
 *
 * Finally the LogActor is removed and tested to make sure that it was
 * successfully removed.
 *
 * By having a higher number of threads than would normally be used in the
 * Poolling filter we aim to catch the race condition where a ThreadLocal remove
 * is called before one or more threads call get(). This way we can ensure that
 * the remove does not affect more than the Thread it was called in.
 */
public class CurrentActorTest extends TestCase
{
    //Set this to be a reasonably large number
    int THREADS = 10;

    // Record any exceptions that are thrown by the threads
    final Exception[] _errors = new Exception[THREADS];

    // Create a single session for this test.
    AMQProtocolSession _session;

    public void setUp() throws Exception
    {
        super.setUp();
        // Create a single session for this test.
        VirtualHost virtualHost = ApplicationRegistry.getInstance().
                getVirtualHostRegistry().getVirtualHosts().iterator().next();

        // Create a single session for this test.
        _session = new InternalTestProtocolSession(virtualHost);
    }


    @Override
    public void tearDown() throws Exception
    {
        // Correctly Close the AR we created
        ApplicationRegistry.remove();
        super.tearDown();
    }


    public void testFIFO() throws AMQException
    {
        // Create a new actor using retrieving the rootMessageLogger from
        // the default ApplicationRegistry.
        //fixme reminder that we need a better approach for broker testing.
        AMQPConnectionActor connectionActor = new AMQPConnectionActor(_session,
                                                                      ApplicationRegistry.getInstance().
                                                                              getRootMessageLogger());

        CurrentActor.set(connectionActor);

        //Use the Actor to send a simple message
        CurrentActor.get().message(new LogSubject()
        {
            public String toString()
            {
                return "[CurrentActorTest] ";
            }

        }, new LogMessage()
        {
            public String toString()
            {
                return "Connection Log Msg";
            }
        });

        // Verify it was the same actor as we set earlier
        assertEquals("Retrieved actor is not as expected ",
                     connectionActor, CurrentActor.get());

        /**
         * Set the actor to nwo be the Channel actor so testing the ability
         * to push the actor on to the stack
         */

        AMQChannel channel = new AMQChannel(_session, 1, _session.getVirtualHost().getMessageStore());

        AMQPChannelActor channelActor = new AMQPChannelActor(channel,
                                                             ApplicationRegistry.getInstance().
                                                                     getRootMessageLogger());

        CurrentActor.set(channelActor);

        //Use the Actor to send a simple message
        CurrentActor.get().message(new LogSubject()
        {
            public String toString()
            {
                return "[CurrentActorTest] ";
            }

        }, new LogMessage()
        {
            public String toString()
            {
                return "Channel Log Msg";
            }
        });

        // Verify it was the same actor as we set earlier
        assertEquals("Retrieved actor is not as expected ",
                     channelActor, CurrentActor.get());

        // Remove the ChannelActor from the stack
        CurrentActor.remove();

        // Verify we now have the same connection actor as we set earlier
        assertEquals("Retrieved actor is not as expected ",
                     connectionActor, CurrentActor.get());

        // Verify that removing the our last actor it returns us to the test
        // default that the ApplicationRegistry sets.
        CurrentActor.remove();

        assertEquals("CurrentActor not the Test default", TestLogActor.class ,CurrentActor.get().getClass());
    }

    public void testThreadLocal()
    {

        // Setup the threads
        Thread[] threads = new Thread[THREADS];
        for (int count = 0; count < THREADS; count++)
        {
            Runnable test = new Test(count);
            threads[count] = new Thread(test);
        }

        //Run the threads
        for (int count = 0; count < THREADS; count++)
        {
            threads[count].start();
        }

        // Wait for them to finish
        for (int count = 0; count < THREADS; count++)
        {
            try
            {
                threads[count].join();
            }
            catch (InterruptedException e)
            {
                //if we are interrupted then we will exit shortly.
            }
        }

        // Verify that none of the tests threw an exception
        for (int count = 0; count < THREADS; count++)
        {
            if (_errors[count] != null)
            {
                _errors[count].printStackTrace();
                fail("Error occured in thread:" + count);
            }
        }
    }

    public class Test implements Runnable
    {
        int count;

        Test(int count)
        {
            this.count = count;
        }

        public void run()
        {

            // Create a new actor using retrieving the rootMessageLogger from
            // the default ApplicationRegistry.
            //fixme reminder that we need a better approach for broker testing.
            AMQPConnectionActor actor = new AMQPConnectionActor(_session,
                                                                ApplicationRegistry.getInstance().
                                                                        getRootMessageLogger());

            CurrentActor.set(actor);

            try
            {
                //Use the Actor to send a simple message
                CurrentActor.get().message(new LogSubject()
                {
                    public String toString()
                    {
                        return "[CurrentActorTest] ";
                    }

                }, new LogMessage()
                {
                    public String toString()
                    {
                        return "Running Thread:" + count;
                    }
                });

                // Verify it was the same actor as we set earlier
                assertEquals("Retrieved actor is not as expected ",
                             actor, CurrentActor.get());

                // Verify that removing the actor works for this thread
                CurrentActor.remove();

                assertNull("CurrentActor should be null", CurrentActor.get());
            }
            catch (Exception e)
            {
                _errors[count] = e;
            }

        }
    }

}
