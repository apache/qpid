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
package org.apache.qpid.server.failure;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.FileQueueBackingStoreFactory;
import org.apache.qpid.test.utils.QpidTestCase;

import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

import junit.framework.TestCase;

/**
 * The idea behind this is to test how a broker with flow to disk copes when
 * over 31998 queues are created in a single directory.
 *
 * As the Java broker uses a directory per queue as the queueBacking for FtD
 * this test will fail until we do some sort of bin allocation for the queues.
 */
public class Create32kQueueWithoutFailure extends TestCase implements ConnectionListener
{

    static final int QUEUE_COUNT = 32000;

    public void test() throws Exception
    {
        AMQConnection connection = new AMQConnection("amqp://guest:guest@clientid/test?brokerlist='tcp://localhost:5672'");//(AMQConnection) getConnectionFactory("default").createConnection("guest", "guest");

        connection.setConnectionListener(this);

        AMQSession session = (AMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        // Make a small limit just for show
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), 1);

        for (int index = 0; index < QUEUE_COUNT; index++)
        {                                          //getName() +
            System.out.println("Creating:"+index);
            session.createQueue(new AMQShortString( "TempQueue-" + index), false, false, false, arguments);
        }

        connection.close();
    }

    public void bytesSent(long count)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void bytesReceived(long count)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean preFailover(boolean redirect)
    {
        return false; //Veto Failover
        //If we cause a connection failure creating lots of queues
        // then we don't want to attempt to resetup the session on a new
        // connection.
    }

    public boolean preResubscribe()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void failoverComplete()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Simple test app that shows the distribution of 32000 'queues' callled
     * 'TempQueue-<ID>' where ID is 0-32000
     *
     * Using straight Object.hashCode() we get quite an uneven distribution
     * but using the hash function from Harmony's ConcurrentHashMap we smooth
     * things out.
     *
     * @param args
     */
    public static void main(String[] args)
    {

        int[] hit = new int[256];
        String name = "TempQueue-";
        for (int index = 0; index < QUEUE_COUNT; index++)
        {
            int hash = FileQueueBackingStoreFactory.hash(name + index);

            long bin = hash & 0xFFL;

            File dir = new File(System.getProperty("java.io.tmpdir")+File.separator+bin);

            if (dir.exists())
            {
                hit[(int)bin]++;
            }
            else
            {
                dir.mkdirs();
                dir.deleteOnExit();
            }
        }

        for (int index = 0; index < hit.length; index++)
        {
            System.out.println("Bin:" + index + " Hit:" + hit[index]);
        }

    }

}
