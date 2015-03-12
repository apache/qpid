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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;

import com.sleepycat.je.rep.ReplicationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.AbstractVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNodeImpl;
import org.apache.qpid.systest.rest.RestTestHelper;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.url.URLSyntaxException;

public class GroupCreator
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(GroupCreator.class);

    private static final String MANY_BROKER_URL_FORMAT = "amqp://guest:guest@/%s?brokerlist='%s'&failover='roundrobin?cyclecount='%d''";
    private static final String BROKER_PORTION_FORMAT = "tcp://localhost:%d?connectdelay='%d',retries='%d'";

    private static final int FAILOVER_CYCLECOUNT = 40;
    private static final int FAILOVER_RETRIES = 0;
    private static final int FAILOVER_CONNECTDELAY = 250;

    private static final String SINGLE_BROKER_URL_WITH_RETRY_FORMAT = "amqp://guest:guest@/%s?brokerlist='tcp://localhost:%d?connectdelay='%d',retries='%d''";
    private static final String SINGLE_BROKER_URL_WITHOUT_RETRY_FORMAT = "amqp://guest:guest@/%s?brokerlist='tcp://localhost:%d'";

    private static final int RETRIES = 60;
    private static final int CONNECTDELAY = 75;

    private final QpidBrokerTestCase _testcase;
    private final Map<Integer, Integer> _brokerPortToBdbPortMap = new TreeMap<Integer, Integer>();
    private final String _virtualHostName;

    private final String _ipAddressOfBroker;
    private final String _groupName ;
    private final int _numberOfNodes;
    private int _bdbHelperPort;
    private int _primaryBrokerPort;

    public GroupCreator(QpidBrokerTestCase testcase, String virtualHostName, int numberOfNodes)
    {
        _testcase = testcase;
        _virtualHostName = virtualHostName;
        _groupName = virtualHostName;
        _ipAddressOfBroker = getIpAddressOfBrokerHost();
        _numberOfNodes = numberOfNodes;
        _bdbHelperPort = 0;
    }

    public void configureClusterNodes() throws Exception
    {
        int brokerPort = _testcase.findFreePort();

        int[] bdbPorts = new int[_numberOfNodes];
        for (int i = 0; i < _numberOfNodes; i++)
        {
            int bdbPort = _testcase.getNextAvailable(brokerPort + 1);
            bdbPorts[i] = bdbPort;
            _brokerPortToBdbPortMap.put(brokerPort, bdbPort);
            brokerPort = _testcase.getNextAvailable(bdbPort + 1);
        }

        String bluePrintJson =  getBlueprint();
        List<String> permittedNodes = getPermittedNodes(_ipAddressOfBroker, bdbPorts);

        String helperName = null;
        for (Map.Entry<Integer,Integer> entry: _brokerPortToBdbPortMap.entrySet())
        {
            brokerPort = entry.getKey();
            int bdbPort = entry.getValue();
            LOGGER.debug("Cluster broker port " + brokerPort + ", bdb replication port " + bdbPort);
            if (_bdbHelperPort == 0)
            {
                _bdbHelperPort = bdbPort;
            }

            String nodeName = getNodeNameForNodeAt(bdbPort);
            if (helperName == null)
            {
                helperName = nodeName;
            }

            Map<String, Object> virtualHostNodeAttributes = new HashMap<String, Object>();
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.STORE_PATH, System.getProperty("QPID_WORK") + File.separator + brokerPort);
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.GROUP_NAME, _groupName);
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.NAME, nodeName);
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.ADDRESS, getNodeHostPortForNodeAt(bdbPort));
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, getHelperHostPort());
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.TYPE, BDBHAVirtualHostNodeImpl.VIRTUAL_HOST_NODE_TYPE);
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.HELPER_NODE_NAME, helperName);
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.PERMITTED_NODES, permittedNodes);

            Map<String, String> context = new HashMap<>();
            context.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");
            context.put(ReplicationConfig.ELECTIONS_PRIMARY_RETRIES, "0");
            context.put(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR, bluePrintJson);
            virtualHostNodeAttributes.put(BDBHAVirtualHostNode.CONTEXT, context);

            TestBrokerConfiguration brokerConfiguration = _testcase.getBrokerConfiguration(brokerPort);
            brokerConfiguration.addJmxManagementConfiguration();
            brokerConfiguration.addHttpManagementConfiguration();
            brokerConfiguration.setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
            brokerConfiguration.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.PORT, _testcase.getHttpManagementPort(brokerPort));

            brokerConfiguration.setObjectAttributes(VirtualHostNode.class, _virtualHostName, virtualHostNodeAttributes);

        }
        _primaryBrokerPort = getPrimaryBrokerPort();
    }

    public void setDesignatedPrimaryOnFirstBroker(boolean designatedPrimary) throws Exception
    {
        if (_numberOfNodes != 2)
        {
            throw new IllegalArgumentException("Only two nodes groups have the concept of primary");
        }
        TestBrokerConfiguration config = _testcase.getBrokerConfiguration(_primaryBrokerPort);
        String nodeName = getNodeNameForNodeAt(_brokerPortToBdbPortMap.get(_primaryBrokerPort));
        config.setObjectAttribute(VirtualHostNode.class, nodeName, BDBHAVirtualHostNode.DESIGNATED_PRIMARY, designatedPrimary);
        config.setSaved(false);
    }

    private int getPrimaryBrokerPort()
    {
        return _brokerPortToBdbPortMap.keySet().iterator().next();
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
                final TestBrokerConfiguration brokerConfig = _testcase.getBrokerConfiguration(brokerPortNumber);
                Future<Object> future = executor.submit(new Callable<Object>()
                {
                    public Object call()
                    {
                        try
                        {
                            _testcase.startBroker(brokerPortNumber, brokerConfig, null);
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

    public ConnectionURL getConnectionUrlForAllClusterNodes() throws Exception
    {
        return  getConnectionUrlForAllClusterNodes(FAILOVER_CONNECTDELAY, FAILOVER_RETRIES, FAILOVER_CYCLECOUNT);
    }

    public ConnectionURL getConnectionUrlForAllClusterNodes(int connectDelay, int retries, final int cyclecount) throws Exception
    {
        final StringBuilder brokerList = new StringBuilder();

        for(Iterator<Integer> itr = _brokerPortToBdbPortMap.keySet().iterator(); itr.hasNext(); )
        {
            int brokerPortNumber = itr.next();

            brokerList.append(String.format(BROKER_PORTION_FORMAT, brokerPortNumber, connectDelay, retries));
            if (itr.hasNext())
            {
                brokerList.append(";");
            }
        }

        return new AMQConnectionURL(String.format(MANY_BROKER_URL_FORMAT, _virtualHostName, brokerList, cyclecount));
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

    public String getNodeNameForBrokerPort(final int brokerPort)
    {
        return getNodeNameForNodeAt(_brokerPortToBdbPortMap.get(brokerPort));
    }

    public void setNodeAttributes(int brokerPort, Map<String, Object> attributeMap)
            throws Exception
    {
        setNodeAttributes(brokerPort, brokerPort, attributeMap);
    }

    public void setNodeAttributes(int localNodePort, int remoteNodePort, Map<String, Object> attributeMap)
            throws Exception
    {
        RestTestHelper restHelper = createRestTestHelper(localNodePort);
        String url = getNodeRestUrl(localNodePort, remoteNodePort);
        int status = restHelper.submitRequest(url, "PUT", attributeMap);
        if (status != 200)
        {
            throw new Exception("Unexpected http status when updating " + getNodeNameForBrokerPort(remoteNodePort) + " attribute(s) : " + status);
        }
    }

    private String getNodeRestUrl(int localNodePort, int remoteNodePort)
    {
        String remoteNodeName = getNodeNameForBrokerPort(remoteNodePort);
        String localNodeName = getNodeNameForBrokerPort(localNodePort);
        String url = null;
        if (localNodePort == remoteNodePort)
        {
            url = "/api/latest/virtualhostnode/" + localNodeName;
        }
        else
        {
            url = "/api/latest/replicationnode/" + localNodeName + "/" + remoteNodeName;
        }
        return url;
    }

    public Map<String, Object> getNodeAttributes(int brokerPort) throws IOException
    {
        return getNodeAttributes(brokerPort, brokerPort);
    }

    public Map<String, Object> getNodeAttributes(int localNodePort, int remoteNodePort) throws IOException
    {
        RestTestHelper restHelper = createRestTestHelper(localNodePort);
        List<Map<String, Object>> results= restHelper.getJsonAsList(getNodeRestUrl(localNodePort, remoteNodePort));
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

    public void awaitNodeToAttainRole(int brokerPort, String desiredRole) throws Exception
    {
        awaitNodeToAttainRole(brokerPort, brokerPort, desiredRole);
    }

    public void awaitNodeToAttainRole(int localNodePort, int remoteNodePort, String desiredRole) throws Exception
    {
        awaitNodeToAttainAttributeValue(localNodePort, remoteNodePort, BDBHARemoteReplicationNode.ROLE, desiredRole);
    }

    public void awaitNodeToAttainAttributeValue(int localNodePort, int remoteNodePort, String attributeName, String desiredValue) throws Exception
    {
        final long startTime = System.currentTimeMillis();
        Map<String, Object> data = Collections.emptyMap();

        while(!desiredValue.equals(data.get(attributeName)) && (System.currentTimeMillis() - startTime) < 30000)
        {
            LOGGER.debug("Awaiting node '" + getNodeNameForBrokerPort(remoteNodePort) + "' to transit into " + desiredValue + " role");
            data = getNodeAttributes(localNodePort, remoteNodePort);
            if (!desiredValue.equals(data.get(attributeName)))
            {
                Thread.sleep(1000);
            }
        }
        LOGGER.debug("Node '" + getNodeNameForBrokerPort(remoteNodePort) + "' attribute  '" + attributeName + "' is " + data.get(attributeName));
        Assert.assertEquals("Unexpected " + attributeName + " at " + localNodePort, desiredValue, data.get(attributeName));
    }

    public RestTestHelper createRestTestHelper(int brokerPort)
    {
        int httpPort = _testcase.getHttpManagementPort(brokerPort);
        RestTestHelper helper = new RestTestHelper(httpPort);
        helper.setUsernameAndPassword("webadmin", "webadmin");
        return helper;
    }

    public static String getBlueprint() throws Exception
    {
        Map<String,Object> bluePrint = new HashMap<>();
        bluePrint.put(VirtualHost.TYPE, BDBHAVirtualHostImpl.VIRTUAL_HOST_TYPE);

        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, bluePrint);
        return writer.toString();
    }

    public static List<String> getPermittedNodes(String hostName, int... ports)
    {
        List<String> permittedNodes = new ArrayList<String>();
        for (int port: ports)
        {
            permittedNodes.add(hostName + ":" + port);
        }
        return permittedNodes;
    }
}
