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
package org.apache.qpid.test.client;

import org.apache.qpid.test.utils.*;
import javax.jms.*;


/**
 * RollbackOrderTest
 *
 */

public class RollbackOrderTest extends QpidTestCase
{

    private Connection conn;
    private Queue queue;
    private Session ssn;
    private MessageProducer prod;
    private MessageConsumer cons;

    @Override public void setUp() throws Exception
    {
        super.setUp();
        conn = getConnection();
        conn.start();
        ssn = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
        queue = ssn.createQueue("rollback-order-test-queue");
        prod = ssn.createProducer(queue);
        cons = ssn.createConsumer(queue);
        for (int i = 0; i < 5; i++)
        {
            TextMessage msg = ssn.createTextMessage("message " + (i+1));
            prod.send(msg);
        }
        ssn.commit();
    }

    public void testOrderingAfterRollback() throws Exception
    {
        for (int i = 0; i < 10; i++)
        {
            TextMessage msg = (TextMessage) cons.receive();
            assertEquals("message 1", msg.getText());
            ssn.rollback();
        }
    }

    @Override public void tearDown() throws Exception
    {
        while (true)
        {
            Message msg = cons.receiveNoWait();
            if (msg == null)
            {
                break;
            }
            else
            {
                msg.acknowledge();
            }
        }
        ssn.commit();
        super.tearDown();
    }

}
