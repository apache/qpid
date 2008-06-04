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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.testutil.BrokerStartupException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

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

    protected static final String ALL = "org.apache.qpid";
    protected static final String BROKER = "org.apache.qpid.server";
    protected static final String CLIENT = "org.apache.qpid.client";
    protected static final String COMMON = "org.apache.qpid.common";
    protected static final String FRAMING = "org.apache.qpid.framing";
    protected static final String TEST = "org.apache.qpid.test";

    private LinkedList<LogState> _logStates = new LinkedList<LogState>();
    private Map<String, String> _setProperties = new HashMap<String, String>();

    protected void setUp() throws Exception
    {
        super.setUp();
        startVMBroker(1);

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
        //Disabled
//        checkQueuesClean();

        stopVMBroker(1);

        revertLogging();

        revertSystemProperties();

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

    public void startVMBroker(int vmID) throws Exception
    {
        startVMBroker(vmID, (File) null);
    }

    /** FIXME: for now vmID must be unique client is responsible for this. */
    public void startVMBroker(int vmID, File configFile)
    {
        //If we have configuration file then load that
        if (configFile != null)
        {
            if (!configFile.exists())
            {
                System.err.println("Configuration file not found:" + configFile);
                fail("Configuration file not found:" + configFile);
            }

            if (System.getProperty("QPID_HOME") == null)
            {
                fail("QPID_HOME not set");
            }

            try
            {
                ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(configFile);

                //For now disable management on all configured inVM broker.
                config.getConfiguration().setProperty("management.enabled", "false");

                ApplicationRegistry.initialise(config, vmID);
            }
            catch (Exception e)
            {
                throw new BrokerStartupException("Unable to configure broker:" + vmID + " With file:" + configFile, e);
            }
        }

        try
        {
            TransportConnection.createVMBroker(vmID);
        }
        catch (AMQVMBrokerCreationException e)
        {
            throw new BrokerStartupException("Unable to start broker:" + vmID, e);
        }
    }

    public void startVMBroker(int vmID, ConfigurationFileApplicationRegistry config) throws Exception
    {
        ApplicationRegistry.initialise(config, vmID);
        startVMBroker(vmID);
    }

    public void stopVMBroker(int inVMid)
    {
        TransportConnection.killVMBroker(inVMid);
        ApplicationRegistry.remove(inVMid);
    }

    protected void setLoggingLevel(String loggerName, Level level)
    {
        Logger logger = Logger.getLogger(loggerName);

        Level currentLevel = logger.getLevel();

        _logStates.push(new LogState(logger, currentLevel));

        logger.setLevel(level);
    }

    protected void revertLogging()
    {
        for (LogState state : _logStates)
        {
            state.getLogger().setLevel(state.getLevel());
        }

        _logStates.clear();
    }

    protected class LogState
    {
        private Logger _logger;
        private Level _level;

        public LogState(Logger logger, Level level)
        {
            _logger = logger;
            _level = level;
        }

        public Logger getLogger()
        {
            return _logger;
        }

        public Level getLevel()
        {
            return _level;
        }
    }

    protected void setSystemProperty(String property, String value)
    {
        if (!_setProperties.containsKey(property))
        {
            _setProperties.put(property, System.getProperty(property));
        }

        System.setProperty(property, value);
    }

    protected void revertSystemProperties()
    {
        for (String key : _setProperties.keySet())
        {
            String value = _setProperties.get(key);
            if (value != null)
            {
                System.setProperty(key, value);
            }
            else
            {
                System.clearProperty(key);
            }
        }
    }

}
