/*
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
package org.apache.qpid.server.store.berkeleydb;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.URLSyntaxException;

import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.monitor.GroupChangeEvent;
import com.sleepycat.je.rep.monitor.JoinGroupEvent;
import com.sleepycat.je.rep.monitor.LeaveGroupEvent;
import com.sleepycat.je.rep.monitor.Monitor;
import com.sleepycat.je.rep.monitor.MonitorChangeListener;
import com.sleepycat.je.rep.monitor.MonitorConfig;
import com.sleepycat.je.rep.monitor.NewMasterEvent;

public class HATestClusterCreator
{
    protected static final Logger LOGGER = Logger.getLogger(HATestClusterCreator.class);

    private static final String MANY_BROKER_URL_FORMAT = "amqp://guest:guest@/%s?brokerlist='%s'&failover='roundrobin?cyclecount='%d''";
    private static final String BROKER_PORTION_FORMAT = "tcp://localhost:%d?connectdelay='%d',retries='%d'";
    private static final String SINGLE_BROKER_URL_FORMAT = "amqp://guest:guest@/%s?brokerlist='tcp://localhost:%d?connectdelay='%d',retries='%d''";

    private static final int CYCLECOUNT = 2;
    private static final int RETRIES = 2;
    private static final int CONNECTDELAY = 1000;

    private final QpidBrokerTestCase _testcase;
    private final Map<Integer, Integer> _brokerPortToBdbPortMap = new HashMap<Integer, Integer>();
    private final Map<Integer, BrokerConfigHolder> _brokerConfigurations = new TreeMap<Integer, BrokerConfigHolder>();
    private final String _virtualHostName;
    private final String _configKeyPrefix;

    private final String _ipAddressOfBroker;
    private final String _groupName ;
    private final int _numberOfNodes;
    private int _bdbHelperPort;
    private int _primaryBrokerPort;
    private Monitor _monitor;

    public HATestClusterCreator(QpidBrokerTestCase testcase, String virtualHostName, int numberOfNodes)
    {
        _testcase = testcase;
        _virtualHostName = virtualHostName;
        _groupName = "group" + _testcase.getName();
        _ipAddressOfBroker = getIpAddressOfBrokerHost();
        _numberOfNodes = numberOfNodes;
        _configKeyPrefix = "virtualhosts.virtualhost." + _virtualHostName + ".store.";
        _bdbHelperPort = 0;
    }

    public void configureClusterNodes() throws Exception
    {
        int brokerPort = _testcase.findFreePort();

        for (int i = 0; i < _numberOfNodes; i++)
        {
            int bdbPort = _testcase.getNextAvailable(brokerPort + 1);
            _brokerPortToBdbPortMap.put(brokerPort, bdbPort);

            LOGGER.debug("Cluster broker port " + brokerPort + ", bdb replication port " + bdbPort);
            if (_bdbHelperPort == 0)
            {
                _bdbHelperPort = bdbPort;
            }

            configureClusterNode(brokerPort, bdbPort);
            collectConfig(brokerPort, _testcase.getTestConfiguration(), _testcase.getTestVirtualhosts());

            brokerPort = _testcase.getNextAvailable(bdbPort + 1);
        }
    }

    public void setAutoDesignatedPrimary(boolean autoDesignatedPrimary) throws Exception
    {
        if (_numberOfNodes != 2)
        {
            throw new IllegalArgumentException("Only two nodes groups have the concept of primary");
        }

        final Entry<Integer, BrokerConfigHolder> brokerConfigEntry = _brokerConfigurations.entrySet().iterator().next();
        final String configKey = getConfigKey("highAvailability.autoDesignatedPrimary");
        brokerConfigEntry.getValue().getTestVirtualhosts().setProperty(configKey, Boolean.toString(autoDesignatedPrimary));
        _primaryBrokerPort = brokerConfigEntry.getKey();
    }

    /**
     * @param configKeySuffix "highAvailability.designatedPrimary", for example
     * @return "virtualhost.test.store.highAvailability.designatedPrimary", for example
     */
    private String getConfigKey(String configKeySuffix)
    {
        final String configKey = StringUtils.substringAfter(_configKeyPrefix + configKeySuffix, "virtualhosts.");
        return configKey;
    }

    public void startNode(final int brokerPortNumber) throws Exception
    {
        final BrokerConfigHolder brokerConfigHolder = _brokerConfigurations.get(brokerPortNumber);

        _testcase.setTestConfiguration(brokerConfigHolder.getTestConfiguration());
        _testcase.setTestVirtualhosts(brokerConfigHolder.getTestVirtualhosts());

        _testcase.startBroker(brokerPortNumber);
    }

    public void startCluster() throws Exception
    {
        for (final Integer brokerPortNumber : _brokerConfigurations.keySet())
        {
            startNode(brokerPortNumber);
        }
    }

    public void startClusterParallel() throws Exception
    {
        final ExecutorService executor = Executors.newFixedThreadPool(_brokerConfigurations.size());
        try
        {
            List<Future<Object>> brokers = new CopyOnWriteArrayList<Future<Object>>();
            for (final Integer brokerPortNumber : _brokerConfigurations.keySet())
            {
                final BrokerConfigHolder brokerConfigHolder = _brokerConfigurations.get(brokerPortNumber);
                Future<Object> future = executor.submit(new Callable<Object>()
                {
                    public Object call()
                    {
                        try
                        {
                            _testcase.startBroker(brokerPortNumber, brokerConfigHolder.getTestConfiguration(),
                                    brokerConfigHolder.getTestVirtualhosts());
                            return "OK";
                        }
                        catch (Exception e)
                        {
                            return e;
                        }
                    }
                });
                brokers.add(future);
            }
            for (Future<Object> future : brokers)
            {
                Object result = future.get(30, TimeUnit.SECONDS);
                LOGGER.debug("Node startup result:" + result);
                if (result instanceof Exception)
                {
                    throw (Exception) result;
                }
                else if (!"OK".equals(result))
                {
                    throw new Exception("One of the cluster nodes is not started");
                }
            }
        }
        catch (Exception e)
        {
            stopCluster();
            throw e;
        }
        finally
        {
            executor.shutdown();
        }

    }

    public void stopNode(final int brokerPortNumber)
    {
        _testcase.stopBroker(brokerPortNumber);
    }

    public void stopCluster() throws Exception
    {
        shutdownMonitor();
        for (final Integer brokerPortNumber : _brokerConfigurations.keySet())
        {
            try
            {
                stopNode(brokerPortNumber);
            }
            catch(Exception e)
            {
                LOGGER.warn("Failed to stop node on port:" + brokerPortNumber);
            }
        }
    }

    public int getBrokerPortNumberFromConnection(Connection connection)
    {
        final AMQConnection amqConnection = (AMQConnection)connection;
        return amqConnection.getActiveBrokerDetails().getPort();
    }

    public int getPortNumberOfAnInactiveBroker(final Connection activeConnection)
    {
        final Set<Integer> allBrokerPorts = _testcase.getBrokerPortNumbers();
        LOGGER.debug("Broker ports:" + allBrokerPorts);
        final int activeBrokerPort = getBrokerPortNumberFromConnection(activeConnection);
        allBrokerPorts.remove(activeBrokerPort);
        LOGGER.debug("Broker ports:" + allBrokerPorts);
        final int inactiveBrokerPort = allBrokerPorts.iterator().next();
        return inactiveBrokerPort;
    }

    public int getBdbPortForBrokerPort(final int brokerPortNumber)
    {
        return _brokerPortToBdbPortMap.get(brokerPortNumber);
    }

    public Set<Integer> getBdbPortNumbers()
    {
        return new HashSet<Integer>(_brokerPortToBdbPortMap.values());
    }

    public AMQConnectionURL getConnectionUrlForAllClusterNodes() throws Exception
    {
        final StringBuilder brokerList = new StringBuilder();

        for(Iterator<Integer> itr = _brokerPortToBdbPortMap.keySet().iterator(); itr.hasNext(); )
        {
            int brokerPortNumber = itr.next();

            brokerList.append(String.format(BROKER_PORTION_FORMAT, brokerPortNumber, CONNECTDELAY, RETRIES));
            if (itr.hasNext())
            {
                brokerList.append(";");
            }
        }

        return new AMQConnectionURL(String.format(MANY_BROKER_URL_FORMAT, _virtualHostName, brokerList, CYCLECOUNT));
    }

    public AMQConnectionURL getConnectionUrlForSingleNode(final int brokerPortNumber) throws URLSyntaxException
    {
        String url = String.format(SINGLE_BROKER_URL_FORMAT, _virtualHostName, brokerPortNumber, CONNECTDELAY, RETRIES);
        return new AMQConnectionURL(url);
    }

    public String getGroupName()
    {
        return _groupName;
    }

    public String getNodeNameForNodeAt(final int bdbPort)
    {
        return "node" + _testcase.getName() + bdbPort;
    }

    public String getNodeHostPortForNodeAt(final int bdbPort)
    {
        return _ipAddressOfBroker + ":" + bdbPort;
    }

    public String getHelperHostPort()
    {
        if (_bdbHelperPort == 0)
        {
            throw new IllegalStateException("Helper port not yet assigned.");
        }

        return _ipAddressOfBroker + ":" + _bdbHelperPort;
    }

    public void setHelperHostPort(int bdbHelperPort)
    {
        _bdbHelperPort = bdbHelperPort;
    }

    public int getBrokerPortNumberOfPrimary()
    {
        if (_numberOfNodes != 2)
        {
            throw new IllegalArgumentException("Only two nodes groups have the concept of primary");
        }

        return _primaryBrokerPort;
    }

    public int getBrokerPortNumberOfSecondaryNode()
    {
        final Set<Integer> portNumbers = getBrokerPortNumbersForNodes();
        portNumbers.remove(getBrokerPortNumberOfPrimary());
        return portNumbers.iterator().next();
    }

    public Set<Integer> getBrokerPortNumbersForNodes()
    {
        return new HashSet<Integer>(_brokerConfigurations.keySet());
    }

    private void configureClusterNode(final int brokerPort, final int bdbPort) throws Exception
    {
        final String nodeName = getNodeNameForNodeAt(bdbPort);

        _testcase.setConfigurationProperty(_configKeyPrefix + "class", "org.apache.qpid.server.store.berkeleydb.BDBHAMessageStore");

        _testcase.setConfigurationProperty(_configKeyPrefix + "highAvailability.groupName", _groupName);
        _testcase.setConfigurationProperty(_configKeyPrefix + "highAvailability.nodeName", nodeName);
        _testcase.setConfigurationProperty(_configKeyPrefix + "highAvailability.nodeHostPort", getNodeHostPortForNodeAt(bdbPort));
        _testcase.setConfigurationProperty(_configKeyPrefix + "highAvailability.helperHostPort", getHelperHostPort());
        // TODO replication policy
    }

    public String getIpAddressOfBrokerHost()
    {
        String brokerHost = _testcase.getBroker().getHost();
        try
        {
            return InetAddress.getByName(brokerHost).getHostAddress();
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException("Could not determine IP address of host : " + brokerHost, e);
        }
    }

    private void collectConfig(final int brokerPortNumber, XMLConfiguration testConfiguration, XMLConfiguration testVirtualhosts)
    {
        _brokerConfigurations.put(brokerPortNumber, new BrokerConfigHolder((XMLConfiguration) testConfiguration.clone(),
                                                                    (XMLConfiguration) testVirtualhosts.clone()));
    }

    public class BrokerConfigHolder
    {
        private final XMLConfiguration _testConfiguration;
        private final XMLConfiguration _testVirtualhosts;

        public BrokerConfigHolder(XMLConfiguration testConfiguration, XMLConfiguration testVirtualhosts)
        {
            _testConfiguration = testConfiguration;
            _testVirtualhosts = testVirtualhosts;
        }

        public XMLConfiguration getTestConfiguration()
        {
            return _testConfiguration;
        }

        public XMLConfiguration getTestVirtualhosts()
        {
            return _testVirtualhosts;
        }
    }

    public void modifyClusterNodeBdbAddress(int brokerPortNumberToBeMoved, int newBdbPort)
    {
        final BrokerConfigHolder brokerConfigHolder = _brokerConfigurations.get(brokerPortNumberToBeMoved);
        final XMLConfiguration virtualHostConfig = brokerConfigHolder.getTestVirtualhosts();

        final String configKey = getConfigKey("highAvailability.nodeHostPort");
        final String oldBdbHostPort = virtualHostConfig.getString(configKey);

        final String[] oldHostAndPort = StringUtils.split(oldBdbHostPort, ":");
        final String oldHost = oldHostAndPort[0];

        final String newBdbHostPort = oldHost + ":" + newBdbPort;

        virtualHostConfig.setProperty(configKey, newBdbHostPort);
        collectConfig(brokerPortNumberToBeMoved, brokerConfigHolder.getTestConfiguration(), virtualHostConfig);
    }

    public void startMonitorNode()
    {
        shutdownMonitor();

        MonitorConfig config = new MonitorConfig();
        config.setGroupName(_groupName);
        int monitorPort = _testcase.findFreePort();
        config.setNodeName(getNodeNameForNodeAt(monitorPort));
        config.setNodeHostPort("" + monitorPort);
        config.setHelperHosts(getHelperHostPort());

        _monitor = new Monitor(config);

        ReplicationNode currentMaster = _monitor.register();
        LOGGER.info("Current master " + currentMaster.getName());
    }

    public void startListening(MonitorChangeListener listener) throws IOException
    {
        _monitor.startListener(listener);
    }

    public void statListeningForNewMasterEvent(final CountDownLatch latch) throws IOException
    {
        startListening(new MonitorChangeListenerSupport(){
            @Override
            public void notify(NewMasterEvent newMasterEvent)
            {
                LOGGER.debug("New master is elected " + newMasterEvent.getMasterName());
                latch.countDown();
            }
        });
    }

    public void shutdownMonitor()
    {
        if (_monitor != null)
        {
            try
            {
                _monitor.shutdown();
            }
            catch (Exception e)
            {
                LOGGER.warn("Monitor shutdown error:", e);
            }
        }
    }

    public static class MonitorChangeListenerSupport implements MonitorChangeListener
    {

        @Override
        public void notify(NewMasterEvent newMasterEvent)
        {
        }

        @Override
        public void notify(GroupChangeEvent groupChangeEvent)
        {
        }

        @Override
        public void notify(JoinGroupEvent joinGroupEvent)
        {
        }

        @Override
        public void notify(LeaveGroupEvent leaveGroupEvent)
        {
        }
    }
}
