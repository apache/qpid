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
package org.apache.qpid.management.jmx;

import org.apache.qpid.commands.objects.AllObjects;
import org.apache.qpid.management.common.JMXConnnectionFactory;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.server.logging.AbstractTestLogging;
import org.apache.qpid.server.logging.subjects.AbstractTestLogSubject;

import javax.jms.Connection;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Test class to test if any change in the broker JMX code is affesting the management console
 * There are some hardcoding of management feature names and parameter names to create a customized
 * look in the console.
 */
public class ManagementActorLoggingTest extends AbstractTestLogging
{
    MBeanServerConnection _mbsc;
    JMXConnector _jmxc;
    private static final String USER = "admin";

    @Override
    public void setUp() throws Exception
    {
        setConfigurationProperty("management.enabled", "true");
        super.setUp();

        if (isExternalBroker())
        {
            _jmxc = JMXConnnectionFactory.getJMXConnection(
                    5000, "127.0.0.1",
                    getManagementPort(getPort()), USER, USER);

            _mbsc = _jmxc.getMBeanServerConnection();
        }
    }

    @Override
    public void tearDown() throws Exception
    {
        if (isExternalBroker())
        {
            _jmxc.close();
        }
        super.tearDown();
    }

    /**
     * Description:
     * When a JMX Management connection is made then this will be logged out.
     *
     * Input:
     *
     * 1. Running Broker
     * 2. Connect Management client via JMX
     * Output:
     *
     * <date> MNG-1007 : Open <user>
     *
     * Validation Steps:
     * 1. The MNG ID is correct
     * 2. The user is correct
     *
     * On connection close a MNG-1008 is expected
     *
     * * <date> MNG-1008 : Close
     *
     * Validation Steps:
     * 1. The MNG ID is correct
     *
     * @throws java.io.IOException - if there is a problem reseting the log monitor
     */
    public void testJMXManagementConsoleConnection() throws IOException
    {
        if (isExternalBroker())
        {
            List<String> results = _monitor.findMatches("MNG-1007");

            assertEquals("Unexpected Management Connection count", 1, results.size());

            String log = getLog(results.get(0));

            validateMessageID("MNG-1007", log);

            assertTrue("User not in log message:" + log, log.endsWith(USER));
            // Extract the id from the log string
            //  MESSAGE [mng:1(rmi://169.24.29.116)] MNG-1007 : Open : User admin
            int connectionID = Integer.parseInt(fromActor(getLog(results.get(0))).charAt(4) + "");

            results = _monitor.findMatches("MNG-1008");

            assertEquals("Unexpected Management Connection close count", 0, results.size());

            _jmxc.close();

            results = _monitor.findMatches("MNG-1008");

            assertEquals("Unexpected Management Connection count", 1, results.size());

            assertEquals("Close does not have same id as open,", connectionID,
                         Integer.parseInt(fromActor(getLog(results.get(0))).charAt(4) + ""));
        }
    }

    /**
     * Description:
     * When a connected client has its connection closed via the Management Console this will be logged as a CON-1002 message.
     * Input:
     *
     * 1. Running Broker
     * 2. Connected Client
     * 3. Connection is closed via Management Console
     * Output:
     *
     * <date> CON-1002 : Close
     *
     * Validation Steps:
     * 4. The CON ID is correct
     * 5. This must be the last CON message for the Connection
     * 6. It must be preceded by a CON-1001 for this Connection
     *
     * @throws Exception           - {@see ManagedConnection.closeConnection and #getConnection}
     * @throws java.io.IOException - if there is a problem reseting the log monitor
     */
    public void testConnectionCloseViaManagement() throws IOException, Exception
    {
        if (isExternalBroker())
        {

            //Create a connection to the broker
            Connection connection = getConnection();

            // Get all active AMQP connections
            AllObjects allObject = new AllObjects(_mbsc);
            allObject.querystring = "org.apache.qpid:type=VirtualHost.Connection,*";

            Set<ObjectName> objectNames = allObject.returnObjects();

            assertEquals("More than one test connection returned", 1, objectNames.size());

            ObjectName connectionName = objectNames.iterator().next();

            ManagedConnection mangedConnection = MBeanServerInvocationHandler.
                    newProxyInstance(_mbsc, connectionName,
                                     ManagedConnection.class, false);

            //Remove the connection close from any 0-10 connections 
           _monitor.reset();

            //Close the connection
            mangedConnection.closeConnection();

            //Validate results
            List<String> results = _monitor.findMatches("CON-1002");


            assertEquals("Unexpected Connection Close count", 1, results.size());
        }
    }

    /**
     * Description:
     * Exchange creation is possible from the Management Console.
     * When an exchanged is created in this way then a EXH-1001 create message
     * is expected to be logged.
     * Input:
     *
     * 1. Running broker
     * 2. Connected Management Console
     * 3. Exchange Created via Management Console
     * Output:
     *
     * EXH-1001 : Create : [Durable] Type:<value> Name:<value>
     *
     * Validation Steps:
     * 4. The EXH ID is correct
     * 5. The correct tags are present in the message based on the create options
     *
     * @throws java.io.IOException          - if there is a problem reseting the log monitor
     * @throws javax.management.JMException - {@see #createQueue and ManagedExchange.deleteQueue}
     */
    public void testCreateExchangeDirectTransientViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous exchange declares
            _monitor.reset();

            createExchange("direct");

            // Validate

            //1 - ID is correct
            List<String> results = _monitor.findMatches("EXH-1001");

            assertEquals("More than one exchange creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct exchange name
            assertTrue("Incorrect exchange name created:" + log, log.endsWith(getName()));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));

        }
    }

    public void testCreateExchangeTopicTransientViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous exchange declares
            _monitor.reset();

            createExchange("topic");

            // Validate

            //1 - ID is correct
            List<String> results = _monitor.findMatches("EXH-1001");

            assertEquals("More than one exchange creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct exchange name
            assertTrue("Incorrect exchange name created:" + log, log.endsWith(getName()));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));

        }
    }

    public void testCreateExchangeFanoutTransientViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous exchange declares
            _monitor.reset();

            createExchange("fanout");

            // Validate

            //1 - ID is correct
            List<String> results = _monitor.findMatches("EXH-1001");

            assertEquals("More than one exchange creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct exchange name
            assertTrue("Incorrect exchange name created:" + log, log.endsWith(getName()));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));

        }
    }

    public void testCreateExchangeHeadersTransientViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous exchange declares
            _monitor.reset();

            createExchange("headers");

            // Validate

            //1 - ID is correct
            List<String> results = _monitor.findMatches("EXH-1001");

            assertEquals("More than one exchange creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct exchange name
            assertTrue("Incorrect exchange name created:" + log, log.endsWith(getName()));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));

        }
    }

    /**
     * Description:
     * Queue creation is possible from the Management Console. When a queue is created in this way then a QUE-1001 create message is expected to be logged.
     * Input:
     *
     * 1. Running broker
     * 2. Connected Management Console
     * 3. Queue Created via Management Console
     * Output:
     *
     * <date> QUE-1001 : Create : Transient Owner:<name>
     *
     * Validation Steps:
     * 4. The QUE ID is correct
     * 5. The correct tags are present in the message based on the create options
     *
     * @throws java.io.IOException          - if there is a problem reseting the log monitor
     * @throws javax.management.JMException - {@see #createQueue and ManagedExchange.deleteQueue}
     */
    public void testCreateQueueTransientViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous queue declares
            _monitor.reset();

            createQueue();

            // Validate

            List<String> results = _monitor.findMatches("QUE-1001");

            assertEquals("More than one queue creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct queue name
            String subject = fromSubject(log);
            assertEquals("Incorrect queue name created", getName(), AbstractTestLogSubject.getSlice("qu", subject));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));
        }
    }

    /**
     * Description:
     * The ManagementConsole can be used to delete a queue. When this is done a QUE-1002 Deleted message must be logged.
     * Input:
     *
     * 1. Running Broker
     * 2. Queue created on the broker with no subscribers
     * 3. Management Console connected
     * 4. Queue is deleted via Management Console
     * Output:
     *
     * <date> QUE-1002 : Deleted
     *
     * Validation Steps:
     * 5. The QUE ID is correct
     *
     * @throws java.io.IOException          - if there is a problem reseting the log monitor
     * @throws javax.management.JMException - {@see #createQueue and ManagedExchange.deleteQueue}
     */
    public void testQueueDeleteViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous queue declares
            _monitor.reset();

            createQueue();

            ManagedBroker managedBroker = MBeanServerInvocationHandler.
                    newProxyInstance(_mbsc, getVirtualHostManagerObjectName(),
                                     ManagedBroker.class, false);

            managedBroker.deleteQueue(getName());

            List<String> results = _monitor.findMatches("QUE-1002");

            assertEquals("More than one queue deletion found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct binding
            String subject = fromSubject(log);
            assertEquals("Incorrect queue named in delete", getName(), AbstractTestLogSubject.getSlice("qu", subject));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));

        }
    }

    /**
     * Description:
     * The binding of a Queue and an Exchange is done via a Binding. When this Binding is created via the Management Console a BND-1001 Create message will be logged.
     * Input:
     *
     * 1. Running Broker
     * 2. Connected Management Console
     * 3. Use Management Console to perform binding
     * Output:
     *
     * <date> BND-1001 : Create
     *
     * Validation Steps:
     * 4. The BND ID is correct
     * 5. This will be the first message for the given binding
     *
     * @throws java.io.IOException          - if there is a problem reseting the log monitor
     * @throws javax.management.JMException - {@see #createQueue and ManagedExchange.createNewBinding}
     */
    public void testBindingCreateOnDirectViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous queue declares
            _monitor.reset();

            createQueue();

            ManagedExchange managedExchange = MBeanServerInvocationHandler.
                    newProxyInstance(_mbsc, getExchange("amq.direct"),
                                     ManagedExchange.class, false);

            managedExchange.createNewBinding(getName(), getName());

            List<String> results = _monitor.findMatches("BND-1001");

            assertEquals("More than one bind creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct binding
            String subject = fromSubject(log);
            assertEquals("Incorrect queue named in create", getName(), AbstractTestLogSubject.getSlice("qu", subject));
            assertEquals("Incorrect routing key in create", getName(), AbstractTestLogSubject.getSlice("rk", subject));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));
        }
    }

    public void testBindingCreateOnTopicViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous queue declares
            _monitor.reset();

            createQueue();

            ManagedExchange managedExchange = MBeanServerInvocationHandler.
                    newProxyInstance(_mbsc, getExchange("amq.topic"),
                                     ManagedExchange.class, false);

            managedExchange.createNewBinding(getName(), getName());

            List<String> results = _monitor.findMatches("BND-1001");

            assertEquals("More than one bind creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct binding
            String subject = fromSubject(log);
            assertEquals("Incorrect queue named in create", getName(), AbstractTestLogSubject.getSlice("qu", subject));
            assertEquals("Incorrect routing key in create", getName(), AbstractTestLogSubject.getSlice("rk", subject));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));
        }
    }

    public void testBindingCreateOnFanoutViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {
            //Remove any previous queue declares
            _monitor.reset();

            createQueue();

            ManagedExchange managedExchange = MBeanServerInvocationHandler.
                    newProxyInstance(_mbsc, getExchange("amq.fanout"),
                                     ManagedExchange.class, false);

            managedExchange.createNewBinding(getName(), getName());

            List<String> results = _monitor.findMatches("BND-1001");

            assertEquals("More than one bind creation found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct binding
            String subject = fromSubject(log);
            assertEquals("Incorrect queue named in create", getName(), AbstractTestLogSubject.getSlice("qu", subject));
            assertEquals("Incorrect routing key in create", "*", AbstractTestLogSubject.getSlice("rk", subject));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));
        }
    }

    /**
     * Description:
     * Bindings can be deleted so that a queue can be rebound with a different set of values. This can be performed via the Management Console
     * Input:
     *
     * 1. Running Broker
     * 2. Management Console connected
     * 3. Management Console is used to perform unbind.
     * Output:
     *
     * <date> BND-1002 : Deleted
     *
     * Validation Steps:
     * 4. The BND ID is correct
     * 5. There must have been a BND-1001 Create message first.
     * 6. This will be the last message for the given binding
     *
     * @throws java.io.IOException          - if there is a problem reseting the log monitor or an issue with the JMX Connection
     * @throws javax.management.JMException - {@see #createExchange and ManagedBroker.unregisterExchange}
     */
    public void testUnRegisterExchangeViaManagementConsole() throws IOException, JMException
    {
        if (isExternalBroker())
        {

            //Remove any previous queue declares
            _monitor.reset();

            createExchange("direct");

            ManagedBroker managedBroker = MBeanServerInvocationHandler.
                    newProxyInstance(_mbsc, getVirtualHostManagerObjectName(),
                                     ManagedBroker.class, false);

            managedBroker.unregisterExchange(getName());

            List<String> results = _monitor.findMatches("EXH-1002");

            assertEquals("More than one exchange deletion found", 1, results.size());

            String log = getLog(results.get(0));

            // Validate correct binding
            String subject = fromSubject(log);
            assertEquals("Incorrect exchange named in delete", "direct/" + getName(), AbstractTestLogSubject.getSlice("ex", subject));

            // Validate it was a management actor.
            String actor = fromActor(log);
            assertTrue("Actor is not a manangement actor:" + actor, actor.startsWith("mng"));
        }
    }

    /**
     * Create a non-durable test exchange with the current test name
     *
     * @throws JMException - is thrown if a exchange with this testName already exists
     * @throws IOException - if there is a problem with the JMX Connection
     * @throws javax.management.MBeanException
     *                     - if there is another problem creating the exchange
     */
    private void createExchange(String type)
            throws JMException, IOException, MBeanException
    {
        ManagedBroker managedBroker = MBeanServerInvocationHandler.
                newProxyInstance(_mbsc, getVirtualHostManagerObjectName(),
                                 ManagedBroker.class, false);

        managedBroker.createNewExchange(getName(), type, false);
    }

    /**
     * Create a non-durable queue (with no owner) that is named after the
     * creating test.
     *
     * @throws JMException - is thrown if a queue with this testName already exists
     * @throws IOException - if there is a problem with the JMX Connection
     */
    private void createQueue()
            throws JMException, IOException
    {
        ManagedBroker managedBroker = MBeanServerInvocationHandler.
                newProxyInstance(_mbsc, getVirtualHostManagerObjectName(),
                                 ManagedBroker.class, false);

        managedBroker.createNewQueue(getName(), null, false);
    }

    /**
     * Retrive the ObjectName for the test Virtualhost.
     *
     * This is then use to create aproxy to the ManagedBroker MBean.
     *
     * @return the ObjectName for the 'test' VirtualHost.
     */
    private ObjectName getVirtualHostManagerObjectName()
    {
        // Get the name of the test manager
        AllObjects allObject = new AllObjects(_mbsc);
        allObject.querystring = "org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=test,*";

        Set<ObjectName> objectNames = allObject.returnObjects();

        assertEquals("Incorrect number test vhosts returned", 1, objectNames.size());

        // We have verified we have only one value in objectNames so return it
        return objectNames.iterator().next();
    }

    /**
     * Retrive the ObjectName for the given Exchange on the test Virtualhost.
     *
     * This is then use to create aproxy to the ManagedExchange MBean.
     *
     * @param exchange The exchange to retireve e.g. 'direct'
     *
     * @return the ObjectName for the given exchange on the test VirtualHost.
     */
    private ObjectName getExchange(String exchange)
    {
        // Get the name of the test manager
        AllObjects allObject = new AllObjects(_mbsc);
        allObject.querystring = "org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=test,name=" + exchange + ",*";

        Set<ObjectName> objectNames = allObject.returnObjects();

        assertEquals("Incorrect number of exchange with name '" + exchange +
                     "' returned", 1, objectNames.size());

        // We have verified we have only one value in objectNames so return it
        return objectNames.iterator().next();
    }

}
