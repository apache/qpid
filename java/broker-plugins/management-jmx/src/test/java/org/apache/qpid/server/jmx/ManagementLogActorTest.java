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
package org.apache.qpid.server.jmx;

import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.SecurityPlugin;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.test.utils.QpidTestCase;

public class ManagementLogActorTest extends QpidTestCase
{
    private ApplicationRegistry _registry;
    private JMXManagedObjectRegistry _objectRegistry;
    private int _registryPort;
    private int _connectorPort;
    private TestPlugin _plugin;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _registryPort = findFreePort();
        _connectorPort = getNextAvailable(_registryPort + 1);
        XMLConfiguration config = new XMLConfiguration();
        config.addProperty(ServerConfiguration.MGMT_JMXPORT_REGISTRYSERVER, _registryPort + "");
        config.addProperty(ServerConfiguration.MGMT_JMXPORT_CONNECTORSERVER, _connectorPort + "");
        _registry = new TestApplicationRegistry(new ServerConfiguration(config));
        ApplicationRegistry.initialise(_registry);

        _plugin = new TestPlugin();
        _registry.getSecurityManager().addHostPlugin(_plugin);

        _objectRegistry = new JMXManagedObjectRegistry();
        new TestMBean(_objectRegistry);
        _objectRegistry.start();
    }

    public void tearDown() throws Exception
    {
        _objectRegistry.close();
        ApplicationRegistry.remove();
        super.tearDown();
    }

    public void testPrincipalInLogMessage() throws Throwable
    {
        Map<String, Object> environment = new HashMap<String, Object>();
        environment.put(JMXConnector.CREDENTIALS, new String[] { "admin", "admin" });
        String urlString = "service:jmx:rmi:///jndi/rmi://localhost:" + _registryPort + "/jmxrmi";
        JMXServiceURL url = new JMXServiceURL(urlString);
        JMXConnector jmxConnector = JMXConnectorFactory.connect(url, environment);
        MBeanServerConnection mbsc = jmxConnector.getMBeanServerConnection();
        ObjectName mbeanObject = new ObjectName("org.apache.qpid:type=TestMBean,name=test");

        String actorLogMessage = (String) mbsc.getAttribute(mbeanObject, "ActorLogMessage");

        assertTrue("Unexpected log principal in security plugin", _plugin.getLogMessage().startsWith("[mng:admin"));
        assertTrue("Unexpected log principal in MBean", actorLogMessage.startsWith("[mng:admin"));
    }

    public static class TestMBean extends DefaultManagedObject implements CurrentActorRetriever
    {

        public TestMBean(ManagedObjectRegistry registry) throws JMException
        {
            super(CurrentActorRetriever.class, "TestMBean", registry);
            register();
        }

        @Override
        public String getObjectInstanceName()
        {
            return "test";
        }

        @Override
        public ManagedObject getParentObject()
        {
            return null;
        }

        @Override
        public String getActorLogMessage()
        {
            return CurrentActor.get().getLogMessage();
        }

    }

    public static interface CurrentActorRetriever
    {
        String getActorLogMessage();
    }

    public static class TestPlugin implements SecurityPlugin
    {
        private String _logMessage;

        @Override
        public void configure(ConfigurationPlugin config) throws ConfigurationException
        {
        }

        @Override
        public Result getDefault()
        {
            return Result.ALLOWED;
        }

        @Override
        public Result access(ObjectType objectType, Object instance)
        {
            return Result.ALLOWED;
        }

        @Override
        public Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties)
        {
            // set thread name to work around logic in MangementActor
            Thread.currentThread().setName("RMI TCP Connection(1)-" + System.currentTimeMillis());
            _logMessage = CurrentActor.get().getLogMessage();
            return Result.ALLOWED;
        }

        public String getLogMessage()
        {
            return _logMessage;
        }

    }

}
