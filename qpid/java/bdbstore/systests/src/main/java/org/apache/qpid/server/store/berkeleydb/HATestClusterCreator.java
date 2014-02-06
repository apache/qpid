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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.systest.rest.RestTestHelper;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.url.URLSyntaxException;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;

public class HATestClusterCreator
{
    protected static final Logger LOGGER = Logger.getLogger(HATestClusterCreator.class);

    private static final String MANY_BROKER_URL_FORMAT = "amqp://guest:guest@/%s?brokerlist='%s'&failover='roundrobin?cyclecount='%d''";
    private static final String BROKER_PORTION_FORMAT = "tcp://localhost:%d?connectdelay='%d',retries='%d'";

    private static final int FAILOVER_CYCLECOUNT = 10;
    private static final int FAILOVER_RETRIES = 1;
    private static final int FAILOVER_CONNECTDELAY = 1000;

    private static final String SINGLE_BROKER_URL_WITH_RETRY_FORMAT = "amqp://guest:guest@/%s?brokerlist='tcp://localhost:%d?connectdelay='%d',retries='%d''";
    private static final String SINGLE_BROKER_URL_WITHOUT_RETRY_FORMAT = "amqp://guest:guest@/%s?brokerlist='tcp://localhost:%d'";

    private static final int RETRIES = 60;
    private static final int CONNECTDELAY = 75;

    private final QpidBrokerTestCase _testcase;
    private final Map<Integer, Integer> _brokerPortToBdbPortMap = new TreeMap<Integer, Integer>();
    private final String _virtualHostName;

    private final String _ipAddressOfBroker;
    private final String _groupName;
    private final int _numberOfNodes;
    private int _bdbHelperPort;
    private int _primaryBrokerPort;

    public HATestClusterCreator(QpidBrokerTestCase testcase, String virtualHostName, int numberOfNodes)
    {
        _testcase = testcase;
        _virtualHostName = virtualHostName;
        _groupName = "group" + _testcase.getName();
        _ipAddressOfBroker = getIpAddressOfBrokerHost();
        _numberOfNodes = numberOfNodes;
        _bdbHelperPort = 0;
    }

    public void configureClusterNodes(Map<String,String> replicationParameters) throws Exception
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

            configureClusterNodeInBrokerConfiguration(brokerPort, bdbPort, replicationParameters);

            brokerPort = _testcase.getNextAvailable(bdbPort + 1);
        }
    }

    public void configureDesignatedPrimaryOnFirstBroker(boolean designatedPrimary) throws Exception
    {
        if (_numberOfNodes != 2)
        {
            throw new IllegalArgumentException("Only two nodes groups have the concept of primary");
        }
        Map.Entry<Integer, Integer> portsEntry = _brokerPortToBdbPortMap.entrySet().iterator().next();
        TestBrokerConfiguration brokerConfiguration = _testcase.getBrokerConfiguration(portsEntry.getKey());
        String nodeName = getNodeNameForNodeAt(portsEntry.getValue());
        brokerConfiguration.setObjectAttribute(nodeName, ReplicationNode.DESIGNATED_PRIMARY, designatedPrimary);

        // store broker configuration on next restart
        brokerConfiguration.setSaved(false);
        _primaryBrokerPort = portsEntry.getKey();
    }

    public void startNode(final int brokerPortNumber) throws Exception
    {
        _testcase.startBroker(brokerPortNumber);
    }

    public void startCluster() throws Exception
    {
        for (final Integer brokerPortNumber : _brokerPortToBdbPortMap.keySet())
        {
            startNode(brokerPortNumber);
        }
    }

    public void startClusterParallel() throws Exception
    {
        final ExecutorService executor = Executors.newFixedThreadPool(_brokerPortToBdbPortMap.size());
        try
        {
            List<Future<Object>> brokers = new CopyOnWriteArrayList<Future<Object>>();
            for (final Integer brokerPortNumber : _brokerPortToBdbPortMap.keySet())
            {
                Future<Object> future = executor.submit(new Callable<Object>()
                {
                    public Object call()
                    {
                        try
                        {
                            _testcase.startBroker(brokerPortNumber);
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
        _testcase.killBroker(brokerPortNumber);
    }

    public void stopCluster() throws Exception
    {
        for (final Integer brokerPortNumber : _brokerPortToBdbPortMap.keySet())
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

            brokerList.append(String.format(BROKER_PORTION_FORMAT, brokerPortNumber, FAILOVER_CONNECTDELAY, FAILOVER_RETRIES));
            if (itr.hasNext())
            {
                brokerList.append(";");
            }
        }

        return new AMQConnectionURL(String.format(MANY_BROKER_URL_FORMAT, _virtualHostName, brokerList, FAILOVER_CYCLECOUNT));
    }

    public AMQConnectionURL getConnectionUrlForSingleNodeWithoutRetry(final int brokerPortNumber) throws URLSyntaxException
    {
        return getConnectionUrlForSingleNode(brokerPortNumber, false);
    }

    public AMQConnectionURL getConnectionUrlForSingleNodeWithRetry(final int brokerPortNumber) throws URLSyntaxException
    {
        return getConnectionUrlForSingleNode(brokerPortNumber, true);
    }

    private AMQConnectionURL getConnectionUrlForSingleNode(final int brokerPortNumber, boolean retryAllowed) throws URLSyntaxException
    {
        final String url;
        if (retryAllowed)
        {
            url = String.format(SINGLE_BROKER_URL_WITH_RETRY_FORMAT, _virtualHostName, brokerPortNumber, CONNECTDELAY, RETRIES);
        }
        else
        {
            url = String.format(SINGLE_BROKER_URL_WITHOUT_RETRY_FORMAT, _virtualHostName, brokerPortNumber);
        }

        return new AMQConnectionURL(url);
    }

    public String getGroupName()
    {
        return _groupName;
    }

    public String getNodeNameForBrokerPort(final int brokerPort)
    {
        return getNodeNameForNodeAt(_brokerPortToBdbPortMap.get(brokerPort));
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
        return new HashSet<Integer>(_brokerPortToBdbPortMap.keySet());
    }

    private void configureClusterNodeInBrokerConfiguration(final int brokerPort, final int bdbPort, Map<String,String> replicationParameters) throws Exception
    {
        String nodeName = getNodeNameForNodeAt(bdbPort);
        TestBrokerConfiguration config = _testcase.getBrokerConfiguration(brokerPort);

        //remove default non-ha test virtual host
        config.removeObjectConfiguration("test");

        // replication node
        Map<String, Object> replicationNodeAttributes = new HashMap<String, Object>();
        replicationNodeAttributes.put(ReplicationNode.NAME, nodeName);
        replicationNodeAttributes.put(ReplicationNode.GROUP_NAME, _groupName);
        replicationNodeAttributes.put(ReplicationNode.HOST_PORT, getNodeHostPortForNodeAt(bdbPort));
        replicationNodeAttributes.put(ReplicationNode.HELPER_HOST_PORT, getHelperHostPort());
        if (replicationParameters != null)
        {
            replicationNodeAttributes.put(ReplicationNode.REPLICATION_PARAMETERS, replicationParameters);
        }
        replicationNodeAttributes.put(ReplicationNode.STORE_PATH, System.getProperty(BrokerProperties.PROPERTY_QPID_WORK) + File.separator + nodeName);

        // ha virtual host
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, _virtualHostName);
        virtualHostAttributes.put(VirtualHost.TYPE, BDBHAVirtualHostFactory.TYPE);

        UUID hostId = config.addVirtualHostConfiguration(virtualHostAttributes);
        config.addReplicationNodeConfiguration(hostId, replicationNodeAttributes);

        config.addJmxManagementConfiguration();
        config.addHttpManagementConfiguration();
        config.setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
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

    public void setReplicationNodeAttributes(int brokerPort, Map<String, Object> attributeMap) throws Exception
    {
        String replicationNodeName = getNodeNameForBrokerPort(brokerPort);
        setReplicationNodeAttributes(brokerPort, replicationNodeName, attributeMap);
    }

    public void setReplicationNodeAttributes(int brokerPort, String replicationNodeName, Map<String, Object> attributeMap)
            throws IOException, JsonGenerationException, JsonMappingException, Exception
    {
        RestTestHelper restHelper = createRestTestHelper(brokerPort);
        int status = restHelper.submitRequest("/rest/replicationnode/" + _virtualHostName + "/" + replicationNodeName , "PUT", attributeMap);
        if (status != 200)
        {
            throw new Exception("Unexpected http status when updating " + replicationNodeName + " attribute's : " + status);
        }
    }

    public Map<String, Object> getReplicationNodeAttributes(int brokerPort) throws Exception
    {
        String replicationNodeName = getNodeNameForBrokerPort(brokerPort);
        return getReplicationNodeAttributes(brokerPort, replicationNodeName);
    }

    public Map<String, Object> getReplicationNodeAttributes(int brokerPort, String replicationNodeName) throws IOException
    {
        RestTestHelper restHelper = createRestTestHelper(brokerPort);
        List<Map<String, Object>> results= restHelper.getJsonAsList("/rest/replicationnode/" + _virtualHostName + "/" + replicationNodeName );
        int size = results.size();
        if (size == 0)
        {
            return Collections.emptyMap();
        }
        else if (size == 1)
        {
            return results.get(0);
        }
        else
        {
            throw new RuntimeException("Unexpected number of nodes " + size);
        }
    }

    private RestTestHelper createRestTestHelper(int brokerPort)
    {
        int httpPort = _testcase.getHttpManagementPort(brokerPort);
        return RestTestHelper.createRestTestHelperWithDefaultCredentials(httpPort);
    }

    public void awaitNodeToAttainRole(int brokerPort, String nodeName, String desiredRole) throws Exception
    {
        final long startTime = System.currentTimeMillis();
        Map<String, Object> data = Collections.emptyMap();

        while(!desiredRole.equals(data.get(ReplicationNode.ROLE)) && (System.currentTimeMillis() - startTime) < 30000)
        {
            LOGGER.debug("Awaiting node '" + nodeName + "' to transit into " + desiredRole + " role");
            data = getReplicationNodeAttributes(brokerPort, nodeName);
            if (!desiredRole.equals(data.get(ReplicationNode.ROLE)))
            {
                Thread.sleep(1000);
            }
        }
        Assert.assertEquals("Node is in unexpected role", desiredRole, data.get(ReplicationNode.ROLE));
    }
}
