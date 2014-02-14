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
package org.apache.qpid.server.virtualhost;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;

import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.test.utils.QpidTestCase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StandardVirtualHostTest extends QpidTestCase
{
    private VirtualHostRegistry _virtualHostRegistry;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHostRegistry != null)
            {
                _virtualHostRegistry.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }

    }

    /**
     * Tests that custom routing keys for the queue specified in the configuration
     * file are correctly bound to the exchange (in addition to the queue name)
     */
    public void testSpecifyingCustomBindings() throws Exception
    {
        customBindingTestImpl(new String[]{"custom1","custom2"});
    }

    /**
     * Tests that a queue specified in the configuration file to be bound to a
     * specified(non-default) direct exchange is a correctly bound to the exchange
     * and the default exchange using the queue name.
     */
    public void testQueueSpecifiedInConfigurationIsBoundToDefaultExchange() throws Exception
    {
        customBindingTestImpl(new String[0]);
    }

    /**
     * Tests that specifying custom routing keys for a queue in the configuration file results in failure
     * to create the vhost (since this is illegal, only queue names are used with the default exchange)
     */
    public void testSpecifyingCustomBindingForDefaultExchangeThrowsException() throws Exception
    {
        final String queueName = getName();
        final String customBinding = "custom-binding";
        File config = writeConfigFile(queueName, queueName, null, false, new String[]{customBinding});

        try
        {
            createVirtualHost(queueName, config);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (ServerScopedRuntimeException e)
        {
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertEquals("Illegal attempt to bind queue '" + queueName + "' to the default exchange with a key other than the queue name: " + customBinding, cause.getMessage());
        }
    }

    public void testVirtualHostBecomesActive() throws Exception
    {
        File config = writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        VirtualHost vhost = createVirtualHost(getName(), config);
        assertNotNull(vhost);
        assertEquals(State.ACTIVE, vhost.getState());
    }

    public void testVirtualHostHavingStoreSetAsTypeBecomesActive() throws Exception
    {
        String virtualHostName = getName();
        VirtualHost host = createVirtualHostUsingStoreType(virtualHostName);
        assertNotNull(host);
        assertEquals(State.ACTIVE, host.getState());
    }

    public void testVirtualHostBecomesStoppedOnClose() throws Exception
    {
        File config = writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        VirtualHost vhost = createVirtualHost(getName(), config);
        assertNotNull(vhost);
        assertEquals(State.ACTIVE, vhost.getState());
        vhost.close();
        assertEquals(State.STOPPED, vhost.getState());
        assertEquals(0, vhost.getHouseKeepingActiveCount());
    }

    public void testVirtualHostHavingStoreSetAsTypeBecomesStoppedOnClose() throws Exception
    {
        String virtualHostName = getName();
        VirtualHost host = createVirtualHostUsingStoreType(virtualHostName);
        assertNotNull(host);
        assertEquals(State.ACTIVE, host.getState());
        host.close();
        assertEquals(State.STOPPED, host.getState());
        assertEquals(0, host.getHouseKeepingActiveCount());
    }

    /**
     * Tests that specifying an unknown exchange to bind the queue to results in failure to create the vhost
     */
    public void testSpecifyingUnknownExchangeThrowsException() throws Exception
    {
        final String queueName = getName();
        final String exchangeName = "made-up-exchange";
        File config = writeConfigFile(queueName, queueName, exchangeName, true, new String[0]);

        try
        {
            createVirtualHost(queueName, config);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (ServerScopedRuntimeException e)
        {
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertEquals("Attempt to bind queue '" + queueName + "' to unknown exchange:" + exchangeName, cause.getMessage());
        }
    }

    public void testCreateVirtualHostWithoutConfigurationInConfigFile() throws Exception
    {
        File config = writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        String hostName = getName() + "-not-existing";
        try
        {
            createVirtualHost(hostName, config);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (RuntimeException e)
        {
            assertEquals("No configuration found for virtual host '" + hostName + "' in " + config.getAbsolutePath(), e.getMessage());
        }
    }

    public void testBindingArguments() throws Exception
    {
        String exchangeName = getName() +".direct";
        String vhostName = getName();
        String queueName = getName();

        Map<String, String[]> bindingArguments = new HashMap<String, String[]>();
        bindingArguments.put("ping", new String[]{"x-filter-jms-selector=select=1", "x-qpid-no-local"});
        bindingArguments.put("pong", new String[]{"x-filter-jms-selector=select='pong'"});
        File config = writeConfigFile(vhostName, queueName, exchangeName, false, new String[]{"ping","pong"}, bindingArguments);
        VirtualHost vhost = createVirtualHost(vhostName, config);

        Exchange exch = vhost.getExchange(getName() +".direct");
        Collection<Binding> bindings = exch.getBindings();
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", 3, bindings.size());

        boolean foundPong = false;
        boolean foundPing = false;
        for (Binding binding : bindings)
        {
            String qn = binding.getQueue().getName();
            assertEquals("Unexpected queue name", getName(), qn);
            Map<String, Object> arguments = binding.getArguments();

            if ("ping".equals(binding.getBindingKey()))
            {
                foundPing = true;
                assertEquals("Unexpected number of binding arguments for ping", 2, arguments.size());
                assertEquals("Unexpected x-filter-jms-selector for ping", "select=1", arguments.get("x-filter-jms-selector"));
                assertTrue("Unexpected x-qpid-no-local for ping", arguments.containsKey("x-qpid-no-local"));
            }
            else if ("pong".equals(binding.getBindingKey()))
            {
                foundPong = true;
                assertEquals("Unexpected number of binding arguments for pong", 1, arguments.size());
                assertEquals("Unexpected x-filter-jms-selector for pong", "select='pong'", arguments.get("x-filter-jms-selector"));
            }
        }

        assertTrue("Pong binding is not found", foundPong);
        assertTrue("Ping binding is not found", foundPing);
    }

    private void customBindingTestImpl(final String[] routingKeys) throws Exception
    {
        String exchangeName = getName() +".direct";
        String vhostName = getName();
        String queueName = getName();

        File config = writeConfigFile(vhostName, queueName, exchangeName, false, routingKeys);
        VirtualHost vhost = createVirtualHost(vhostName, config);
        assertNotNull("virtualhost should exist", vhost);

        AMQQueue queue = vhost.getQueue(queueName);
        assertNotNull("queue should exist", queue);

        Exchange defaultExch = vhost.getDefaultExchange();
        assertTrue("queue should have been bound to default exchange with its name", defaultExch.isBound(queueName, queue));

        Exchange exch = vhost.getExchange(exchangeName);
        assertTrue("queue should have been bound to " + exchangeName + " with its name", exch.isBound(queueName, queue));

        for(String key: routingKeys)
        {
            assertTrue("queue should have been bound to " + exchangeName + " with key " + key, exch.isBound(key, queue));
        }
    }


    private VirtualHost createVirtualHost(String vhostName, File config) throws Exception
    {
        Broker broker = BrokerTestHelper.createBrokerMock();
        _virtualHostRegistry = broker.getVirtualHostRegistry();

        VirtualHostConfiguration configuration = new  VirtualHostConfiguration(vhostName, config, broker);
        VirtualHost host = new StandardVirtualHostFactory().createVirtualHost(_virtualHostRegistry, mock(StatisticsGatherer.class), new SecurityManager(mock(Broker.class), false), configuration,
                mock(org.apache.qpid.server.model.VirtualHost.class));
        _virtualHostRegistry.registerVirtualHost(host);

        return host;
    }

    /**
     * Create a configuration file for testing virtualhost creation
     *
     * @param vhostName name of the virtualhost
     * @param queueName name of the queue
     * @param exchangeName name of a direct exchange to declare (unless dontDeclare = true) and bind the queue to (null = none)
     * @param dontDeclare if true then don't declare the exchange, even if its name is non-null
     * @param routingKeys routingKeys to bind the queue with (empty array = none)
     * @return
     */
    private File writeConfigFile(String vhostName, String queueName, String exchangeName, boolean dontDeclare, String[] routingKeys)
    {
        return writeConfigFile(vhostName, queueName, exchangeName, dontDeclare, routingKeys, null);
    }

    private File writeConfigFile(String vhostName, String queueName, String exchangeName, boolean dontDeclare, String[] routingKeys, Map<String, String[]> bindingArguments)
    {
        File tmpFile = null;
        try
        {
            tmpFile = File.createTempFile(getName(), ".tmp");
            tmpFile.deleteOnExit();

            FileWriter fstream = new FileWriter(tmpFile);
            BufferedWriter writer = new BufferedWriter(fstream);

            //extra outer tag to please Commons Configuration

            writer.write("<virtualhosts>");
            writer.write("  <default>" + vhostName + "</default>");
            writer.write("  <virtualhost>");
            writer.write("      <name>" + vhostName + "</name>");
            writer.write("      <" + vhostName + ">");
            writer.write("          <type>" + StandardVirtualHostFactory.TYPE + "</type>");
            writer.write("              <store>");
            writer.write("                <class>" + TestMemoryMessageStore.class.getName() + "</class>");
            writer.write("              </store>");
            if(exchangeName != null && !dontDeclare)
            {
                writer.write("          <exchanges>");
                writer.write("              <exchange>");
                writer.write("                  <type>direct</type>");
                writer.write("                  <name>" + exchangeName + "</name>");
                writer.write("              </exchange>");
                writer.write("          </exchanges>");
            }
            writer.write("          <queues>");
            writer.write("              <queue>");
            writer.write("                  <name>" + queueName + "</name>");
            writer.write("                  <" + queueName + ">");
            if(exchangeName != null)
            {
                writer.write("                      <exchange>" + exchangeName + "</exchange>");
            }
            for(String routingKey: routingKeys)
            {
                writer.write("                      <routingKey>" + routingKey + "</routingKey>\n");
                if (bindingArguments!= null && bindingArguments.containsKey(routingKey))
                {
                    writer.write("                      <" + routingKey + ">\n");
                    String[] arguments = (String[])bindingArguments.get(routingKey);
                    for (String argument : arguments)
                    {
                        writer.write("                          <bindingArgument>" + argument + "</bindingArgument>\n");
                    }
                    writer.write("                      </" + routingKey + ">\n");
                }
            }
            writer.write("                  </" + queueName + ">");
            writer.write("              </queue>");
            writer.write("          </queues>");
            writer.write("      </" + vhostName + ">");
            writer.write("  </virtualhost>");
            writer.write("</virtualhosts>");

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            fail("Unable to create virtualhost configuration");
        }

        return tmpFile;
    }

    private VirtualHost createVirtualHostUsingStoreType(String virtualHostName) throws ConfigurationException, Exception
    {
        Broker broker = BrokerTestHelper.createBrokerMock();
        _virtualHostRegistry = broker.getVirtualHostRegistry();

        Configuration config = new PropertiesConfiguration();
        VirtualHostConfiguration configuration = new  VirtualHostConfiguration(virtualHostName, config, broker);
        final org.apache.qpid.server.model.VirtualHost virtualHost = mock(org.apache.qpid.server.model.VirtualHost.class);
        when(virtualHost.getAttribute(eq(org.apache.qpid.server.model.VirtualHost.STORE_TYPE))).thenReturn(TestMemoryMessageStore.TYPE);
        VirtualHost host = new StandardVirtualHostFactory().createVirtualHost(_virtualHostRegistry, mock(StatisticsGatherer.class), new SecurityManager(mock(Broker.class), false), configuration,
                virtualHost);
        _virtualHostRegistry.registerVirtualHost(host);
        return host;
    }
}
