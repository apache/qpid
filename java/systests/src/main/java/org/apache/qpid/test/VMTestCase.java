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
package org.apache.qpid.test;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.AMQException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

public class VMTestCase extends TestCase
{
    protected long RECEIVE_TIMEOUT = 1000L; // 1 sec
    protected long CLOSE_TIMEOUT = 10000L; // 10 secs

    protected Context _context;
    protected String _clientID;
    protected String _virtualhost;
    protected String _brokerlist;

    protected final Map<String, String> _connections = new HashMap<String, String>();
    protected final Map<String, String> _queues = new HashMap<String, String>();
    protected final Map<String, String> _topics = new HashMap<String, String>();

    protected void setUp() throws Exception
    {
        super.setUp();
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (Exception e)
        {
            fail("Unable to create broker: " + e);
        }

        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        if (_clientID == null)
        {
            _clientID = this.getClass().getName();
        }

        if (_virtualhost == null)
        {
            _virtualhost = "/test";
        }

        if (_brokerlist == null)
        {
            _brokerlist = "vm://:1";
        }

        env.put("connectionfactory.connection", "amqp://guest:guest@" + _clientID + _virtualhost + "?brokerlist='"
                                                + _brokerlist + "'");

        for (Map.Entry<String, String> c : _connections.entrySet())
        {
            env.put("connectionfactory." + c.getKey(), c.getValue());
        }

        _queues.put("queue", "queue");

        for (Map.Entry<String, String> q : _queues.entrySet())
        {
            env.put("queue." + q.getKey(), q.getValue());
        }

        _topics.put("topic", "topic");

        for (Map.Entry<String, String> t : _topics.entrySet())
        {
            env.put("topic." + t.getKey(), t.getValue());
        }

        _context = factory.getInitialContext(env);
    }

    protected void tearDown() throws Exception
    {
        checkQueuesClean();

        TransportConnection.killVMBroker(1);
        ApplicationRegistry.remove(1);

        super.tearDown();
    }

    private void checkQueuesClean() throws NamingException, JMSException
    {
        Connection connection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();

        Iterator<String> queueNames = new HashSet<String>(_queues.values()).iterator();

        assertTrue("QueueNames doesn't have next", queueNames.hasNext());

        while (queueNames.hasNext())
        {
            Queue queue = session.createQueue(queueNames.next());

            //Validate that the queue are  reporting empty.
            long queueDepth = 0;
            try
            {
                queueDepth = ((AMQSession) session).getQueueDepth((AMQDestination) queue);
            }
            catch (AMQException e)
            {
                //ignore
            }

            assertEquals("Session reports Queue depth not as expected", 0, queueDepth);
        }

        connection.close();
    }

    public int getMessageCount(String queueName)
    {
        return ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(_virtualhost.substring(1))
                .getQueueRegistry().getQueue(new AMQShortString(queueName)).getMessageCount();
    }
}
