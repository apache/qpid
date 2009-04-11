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
package org.apache.qpid.test.unit.ack;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQQueueFactory;

import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;

public class AcknowledgeWithFlowtoDiskTest extends AcknowledgeTest
{
    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        //Incresae the number of messages to send
        NUM_MESSAGES = 100;

        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        // each message in the AckTest is 98 bytes each so only give space for half
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), 49 * NUM_MESSAGES);

        //Create the FlowToDisk Queue
        AMQSession session = ((AMQSession) _con.createSession(false, Session.AUTO_ACKNOWLEDGE));
        session.createQueue(new AMQShortString(getName()), false, false, false, arguments);

        // Get a JMS reference to the new queue
        _queue = session.createQueue(getName());
    }

}
