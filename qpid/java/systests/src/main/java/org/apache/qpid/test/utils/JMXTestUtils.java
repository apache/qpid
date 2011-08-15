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
package org.apache.qpid.test.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;
import javax.management.remote.JMXConnector;

import junit.framework.TestCase;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.management.common.JMXConnnectionFactory;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.management.common.mbeans.LoggingManagement;
import org.apache.qpid.management.common.mbeans.ConfigurationManagement;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.management.common.mbeans.ServerInformation;
import org.apache.qpid.management.common.mbeans.UserManagement;

/**
 * JMX access for tests.
 */
public class JMXTestUtils
{
    QpidBrokerTestCase _test;
    MBeanServerConnection _mbsc;
    JMXConnector _jmxc;

    private String USER;
    private String PASSWORD;

    public JMXTestUtils(QpidBrokerTestCase test, String user, String password)
    {
        _test = test;
        USER = user;
        PASSWORD = password;
    }

    public void setUp() throws IOException, ConfigurationException, Exception
    {
        _test.setConfigurationProperty("management.enabled", "true");       
    }

    public void open() throws Exception
    {
        _jmxc = JMXConnnectionFactory.getJMXConnection(5000, "127.0.0.1",
                    _test.getManagementPort(_test.getPort()), USER, PASSWORD);

        _mbsc = _jmxc.getMBeanServerConnection();
    }

    public void close() throws IOException
    {
        if(_jmxc != null)
        {
            _jmxc.close();
        }
    }

    /**
     * Create a non-durable exchange with the requested name
     *
     * @throws JMException if a exchange with this name already exists
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException if there is another problem creating the exchange
     */
    public void createExchange(String virtualHostName, String name, String type, boolean durable)
            throws JMException, IOException, MBeanException
    {
        ManagedBroker managedBroker = getManagedBroker(virtualHostName);

        managedBroker.createNewExchange(name, type, durable);
    }

    /**
     * Create a non-durable queue (with no owner) that is named after the
     * creating test.
     *
     * @throws JMException if a queue with this name already exists
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException if there is another problem creating the exchange
     */
    public void createQueue(String virtualHostName, String name, String owner, boolean durable)
            throws JMException, MBeanException, IOException
    {
        ManagedBroker managedBroker = getManagedBroker(virtualHostName);

        managedBroker.createNewQueue(name, owner, durable);
    }
    
    /**
     * Unregisters all the channels, queuebindings etc and unregisters
     * this exchange from managed objects.
     *
     * @throws JMException if an exchange with this name does not exist
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException if there is another problem creating the exchange
     */
    public void unregisterExchange(String virtualHostName, String exchange)
            throws IOException, JMException, MBeanException
    {
        ManagedBroker managedBroker = getManagedBroker(virtualHostName);

        managedBroker.unregisterExchange(exchange);
    }
    
    /**
     * Unregisters the Queue bindings, removes the subscriptions and unregisters
     * from the managed objects.
     *
     * @throws JMException if a queue with this name does not exist
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException if there is another problem creating the exchange
     */
    public void deleteQueue(String virtualHostName, String queueName)
            throws IOException, JMException, MBeanException
    {
        ManagedBroker managedBroker = getManagedBroker(virtualHostName);

        managedBroker.deleteQueue(queueName);
    }
    
    /**
	 * Sets the logging level.
     *
     * @throws JMException
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException
     */
    public void setRuntimeLoggerLevel(String logger, String level)
		throws IOException, JMException, MBeanException
    {
        LoggingManagement loggingManagement = getLoggingManagement();
		
        loggingManagement.setRuntimeLoggerLevel(logger, level);
    }
    
    /**
	 * Reload logging config file.
     *
     * @throws JMException
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException
     */
    public void reloadConfigFile()
		throws IOException, JMException, MBeanException
    {
        LoggingManagement loggingManagement = getLoggingManagement();
		
        loggingManagement.reloadConfigFile();
    }
    
    /**
	 * Get list of available logger levels.
     *
     * @throws JMException
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException
     */
    public String[] getAvailableLoggerLevels()
		throws IOException, JMException, MBeanException
    {
        LoggingManagement loggingManagement = getLoggingManagement();
		
        return loggingManagement.getAvailableLoggerLevels();
    }
    
    /**
	 * Set root logger level.
     *
     * @throws JMException
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException
     */
    public void setRuntimeRootLoggerLevel(String level)
		throws IOException, JMException, MBeanException
    {
        LoggingManagement loggingManagement = getLoggingManagement();
		
        loggingManagement.setRuntimeRootLoggerLevel(level);
    }
    
    /**
	 * Get root logger level.
     *
     * @throws JMException
     * @throws IOException if there is a problem with the JMX Connection
     * @throws MBeanException
     */
    public String getRuntimeRootLoggerLevel()
		throws IOException, JMException, MBeanException
    {
        LoggingManagement loggingManagement = getLoggingManagement();
		
        return loggingManagement.getRuntimeRootLoggerLevel();
    }
    
    /**
     * Retrive the ObjectName for a Virtualhost.
     *
     * This is then used to create a proxy to the ManagedBroker MBean.
     *
     * @param virtualHostName the VirtualHost to retrieve
     * @return the ObjectName for the VirtualHost
     */
    @SuppressWarnings("static-access")
    public ObjectName getVirtualHostManagerObjectName(String vhostName)
    {
        // Get the name of the test manager
        String query = "org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost="
                       + ObjectName.quote(vhostName) + ",*";

        Set<ObjectName> objectNames = queryObjects(query);

        _test.assertNotNull("Null ObjectName Set returned", objectNames);
        _test.assertEquals("Incorrect number test vhosts returned", 1, objectNames.size());

        // We have verified we have only one value in objectNames so return it
        ObjectName objectName = objectNames.iterator().next();
		_test.getLogger().info("Loading: " + objectName);
        return objectName;
    }

    /**
     * Retrive the ObjectName for the given Queue on a Virtualhost.
     *
     * This is then used to create a proxy to the ManagedQueue MBean.
     *
     * @param virtualHostName the VirtualHost the Queue is on
     * @param queue The Queue to retireve
     * @return the ObjectName for the given queue on the VirtualHost
     */
    @SuppressWarnings("static-access")
    public ObjectName getQueueObjectName(String virtualHostName, String queue)
    {
        // Get the name of the test manager
        String query = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost="
                       + ObjectName.quote(virtualHostName) + ",name="
                       + ObjectName.quote(queue) + ",*";

        Set<ObjectName> objectNames = queryObjects(query);

        _test.assertNotNull("Null ObjectName Set returned", objectNames);
        _test.assertEquals("Incorrect number of queues with name '" + queue + "' returned", 1, objectNames.size());

        // We have verified we have only one value in objectNames so return it
        ObjectName objectName = objectNames.iterator().next();
		_test.getLogger().info("Loading: " + objectName);
        return objectName;
    }

    /**
     * Retrive the ObjectName for the given Exchange on a VirtualHost.
     *
     * This is then used to create a proxy to the ManagedExchange MBean.
     *
     * @param virtualHostName the VirtualHost the Exchange is on
     * @param exchange the Exchange to retireve e.g. 'direct'
     * @return the ObjectName for the given Exchange on the VirtualHost
     */
    @SuppressWarnings("static-access")
    public ObjectName getExchangeObjectName(String virtualHostName, String exchange)
    {
        // Get the name of the test manager
        String query = "org.apache.qpid:type=VirtualHost.Exchange,VirtualHost="
                       + ObjectName.quote(virtualHostName) + ",name="
                       + ObjectName.quote(exchange) + ",*";

        Set<ObjectName> objectNames = queryObjects(query);

        _test.assertNotNull("Null ObjectName Set returned", objectNames);
        _test.assertEquals("Incorrect number of exchange with name '" + exchange + "' returned", 1, objectNames.size());

        // We have verified we have only one value in objectNames so return it
        ObjectName objectName = objectNames.iterator().next();
		_test.getLogger().info("Loading: " + objectName);
        return objectName;
    }

    @SuppressWarnings("static-access")
    public <T> T getManagedObject(Class<T> managedClass, String query)
    {
        Set<ObjectName> objectNames = queryObjects(query);

        _test.assertNotNull("Null ObjectName Set returned", objectNames);
        _test.assertEquals("More than one " + managedClass + " returned", 1, objectNames.size());

        ObjectName objectName = objectNames.iterator().next();
		_test.getLogger().info("Loading: " + objectName);
        return getManagedObject(managedClass, objectName);
    }

    public <T> T getManagedObject(Class<T> managedClass, ObjectName objectName)
    {
        return MBeanServerInvocationHandler.newProxyInstance(_mbsc, objectName, managedClass, false);
    }

    public <T> List<T> getManagedObjectList(Class<T> managedClass, Set<ObjectName> objectNames)
    {
        List<T> objects = new ArrayList<T>();
        for (ObjectName name : objectNames)
        {
            objects.add(getManagedObject(managedClass, name));
        }
        return objects;
    }

    public ManagedBroker getManagedBroker(String virtualHost)
    {
        return getManagedObject(ManagedBroker.class, getVirtualHostManagerObjectName(virtualHost));
    }
	
    public ManagedExchange getManagedExchange(String exchangeName)
    {
		ObjectName objectName = getExchangeObjectName("test", exchangeName);
        return MBeanServerInvocationHandler.newProxyInstance(_mbsc, objectName, ManagedExchange.class, false);
    }
    
    public ManagedQueue getManagedQueue(String queueName)
    {
        ObjectName objectName = getQueueObjectName("test", queueName);
        return getManagedObject(ManagedQueue.class, objectName);
    }

	public LoggingManagement getLoggingManagement() throws MalformedObjectNameException
    {
		ObjectName objectName = new ObjectName("org.apache.qpid:type=LoggingManagement,name=LoggingManagement");
        return getManagedObject(LoggingManagement.class, objectName);
    }
	
	public ConfigurationManagement getConfigurationManagement() throws MalformedObjectNameException
    {
		ObjectName objectName = new ObjectName("org.apache.qpid:type=ConfigurationManagement,name=ConfigurationManagement");
        return getManagedObject(ConfigurationManagement.class, objectName);
    }
	
	public UserManagement getUserManagement() throws MalformedObjectNameException
    {
		ObjectName objectName = new ObjectName("org.apache.qpid:type=UserManagement,name=UserManagement");
        return getManagedObject(UserManagement.class, objectName);
    }

    /**
     * Retrive {@link ServerInformation} JMX MBean.
     */
    public ServerInformation getServerInformation()
    {
        // Get the name of the test manager
        String query = "org.apache.qpid:type=ServerInformation,name=ServerInformation,*";

        Set<ObjectName> objectNames = queryObjects(query);

        TestCase.assertNotNull("Null ObjectName Set returned", objectNames);
        TestCase.assertEquals("Incorrect number of objects returned", 1, objectNames.size());

        // We have verified we have only one value in objectNames so return it
        return getManagedObject(ServerInformation.class, objectNames.iterator().next());
    }

    /**
     * Retrive all {@link ManagedConnection} objects.
     */
    public List<ManagedConnection> getAllManagedConnections()
    {
        // Get the name of the test manager
        String query = "org.apache.qpid:type=VirtualHost.Connection,VirtualHost=*,name=*";

        Set<ObjectName> objectNames = queryObjects(query);

        TestCase.assertNotNull("Null ObjectName Set returned", objectNames);

        return getManagedObjectList(ManagedConnection.class, objectNames);
    }

    /**
     * Retrive all {@link ManagedConnection} objects for a particular virtual host.
     */
    public List<ManagedConnection> getManagedConnections(String vhost)
    {
        // Get the name of the test manager
        String query = "org.apache.qpid:type=VirtualHost.Connection,VirtualHost=" + ObjectName.quote(vhost) + ",name=*";

        Set<ObjectName> objectNames = queryObjects(query);

        TestCase.assertNotNull("Null ObjectName Set returned", objectNames);

        return getManagedObjectList(ManagedConnection.class, objectNames);
    }

    /**
     * Returns the Set of ObjectNames returned by the broker for the given query,
     * or null if there is problem while performing the query.
     */
    private Set<ObjectName> queryObjects(String query)
    {
        try
        {
            return _mbsc.queryNames(new ObjectName(query), null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
