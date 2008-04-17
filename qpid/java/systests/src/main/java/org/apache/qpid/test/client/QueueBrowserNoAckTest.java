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

import org.apache.qpid.client.AMQSession;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;

public class QueueBrowserNoAckTest extends QueueBrowserAutoAckTest
{
    public void setUp() throws Exception
    {

        super.setUp();

        _clientConnection.close();
        _clientSession.close();

        _queue = (Queue) _context.lookup("queue");

        //Create Client
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _clientConnection.start();

        _clientSession = _clientConnection.createSession(false, AMQSession.NO_ACKNOWLEDGE);

        //Ensure _queue is created
        _clientSession.createConsumer(_queue).close();
    }
}
