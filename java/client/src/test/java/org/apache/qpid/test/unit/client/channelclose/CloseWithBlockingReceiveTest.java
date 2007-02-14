/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.test.unit.client.channelclose;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;

import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.MessageConsumer;

/**
 * @author Apache Software Foundation
 */
public class CloseWithBlockingReceiveTest extends TestCase
{


    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }


    public void testReceiveReturnsNull() throws Exception
    {
        final Connection connection = new AMQConnection("vm://:1", "guest", "guest",
                                                  "fred", "test");
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(new AMQTopic("banana"));
        connection.start();

        Runnable r = new Runnable()
        {

            public void run()
            {
                try
                {
                    Thread.sleep(1000);
                    connection.close();
                }
                catch (Exception e)
                {
                }
            }
        };
        long startTime = System.currentTimeMillis();
        new Thread(r).start();
        consumer.receive(10000);
        assertTrue(System.currentTimeMillis() - startTime < 10000);
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(CloseWithBlockingReceiveTest.class);
    }

}
