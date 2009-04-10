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
package org.apache.qpid.test.unit.close;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.FileQueueBackingStoreFactory;
import org.apache.qpid.test.utils.QpidTestCase;

import javax.jms.Connection;
import javax.jms.Session;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FlowToDiskBackingQueueDeleteTest extends QpidTestCase
{

    public void test() throws Exception
    {

        //Incresae the number of messages to send

        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        // each message in the AckTest is 98 bytes each so only give space for half
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), 1);

        Connection connection = getConnection();
        //Create the FlowToDisk Queue
        AMQSession session = ((AMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE));

        //Make the queue Autodelete and exclusive so we can check it is gone.
        session.createQueue(new AMQShortString(getName()), true, false, true, arguments);

        //Check the backing store exists.
        String workDir = System.getProperty("QPID_WORK", System.getProperty("java.io.tmpdir"));

        long binDir = FileQueueBackingStoreFactory.hash(getName()) & 0xFFL;

        //This is a little bit of an ugly method to find the backing store location
        File backing = new File(workDir + File.separator
                                + FileQueueBackingStoreFactory.QUEUE_BACKING_DIR
                                + File.separator + "test" + File.separator
                                + binDir + File.separator + getName());

        System.err.println(backing.toString());

        assertTrue("QueueBacking Store not created.", backing.exists());

        connection.close();

        assertFalse("QueueBacking Store not deleted.", backing.exists());

    }

}
