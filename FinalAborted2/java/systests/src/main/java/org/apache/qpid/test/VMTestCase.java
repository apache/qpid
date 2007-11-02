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

import junit.extensions.TestSetup;

import junit.framework.Test;
import junit.framework.TestCase;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
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

        env.put("queue.queue", "queue");

        for (Map.Entry<String, String> q : _queues.entrySet())
        {
            env.put("queue." + q.getKey(), q.getValue());
        }

        env.put("topic.topic", "topic");

        for (Map.Entry<String, String> t : _topics.entrySet())
        {
            env.put("topic." + t.getKey(), t.getValue());
        }

        _context = factory.getInitialContext(env);
    }

    protected void tearDown() throws Exception
    {
        TransportConnection.killVMBroker(1);
        ApplicationRegistry.remove(1);

        super.tearDown();
    }

    public void testDummyinVMTestCase()
    {
        // keep maven happy
    }
}
