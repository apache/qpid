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
package org.apache.qpid.server.configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import junit.framework.TestCase;

public class ServerConfigurationTest extends TestCase
{

    private XMLConfiguration _config;

    @Override
    public void setUp()
    {
        _config = new XMLConfiguration();
    }
    
    public void testSetJMXManagementPort() throws ConfigurationException
    {
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        serverConfig.setJMXManagementPort(23);
        assertEquals(23, serverConfig.getJMXManagementPort());
    }

    public void testGetJMXManagementPort() throws ConfigurationException
    {
        _config.setProperty("management.jmxport", 42);
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(42, serverConfig.getJMXManagementPort());
    }

    public void testGetPlatformMbeanserver() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getPlatformMbeanserver());

        // Check value we set 
        _config.setProperty("management.platform-mbeanserver", false);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getPlatformMbeanserver());
    }

    public void testGetPluginDirectory() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(null, serverConfig.getPluginDirectory());

        // Check value we set 
        _config.setProperty("plugin-directory", "/path/to/plugins");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("/path/to/plugins", serverConfig.getPluginDirectory());
    }

    public void testGetPrincipalDatabaseNames() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getPrincipalDatabaseNames().size());

        // Check value we set 
        _config.setProperty("security.principal-databases.principal-database(0).name", "a");
        _config.setProperty("security.principal-databases.principal-database(1).name", "b");
        serverConfig = new ServerConfiguration(_config);
        List<String> dbs = serverConfig.getPrincipalDatabaseNames();
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }
    
    public void testGetPrincipalDatabaseClass() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getPrincipalDatabaseClass().size());

        // Check value we set 
        _config.setProperty("security.principal-databases.principal-database(0).class", "a");
        _config.setProperty("security.principal-databases.principal-database(1).class", "b");
        serverConfig = new ServerConfiguration(_config);
        List<String> dbs = serverConfig.getPrincipalDatabaseClass();
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetPrincipalDatabaseAttributeNames() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getPrincipalDatabaseAttributeNames(1).size());

        // Check value we set 
        _config.setProperty("security.principal-databases.principal-database(0).attributes(0).attribute.name", "a");
        _config.setProperty("security.principal-databases.principal-database(0).attributes(1).attribute.name", "b");
        serverConfig = new ServerConfiguration(_config);
        List<String> dbs = serverConfig.getPrincipalDatabaseAttributeNames(0);
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }


    public void testGetPrincipalDatabaseAttributeValues() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getPrincipalDatabaseAttributeValues(1).size());

        // Check value we set 
        _config.setProperty("security.principal-databases.principal-database(0).attributes(0).attribute.value", "a");
        _config.setProperty("security.principal-databases.principal-database(0).attributes(1).attribute.value", "b");
        serverConfig = new ServerConfiguration(_config);
        List<String> dbs = serverConfig.getPrincipalDatabaseAttributeValues(0);
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetManagementAccessList() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getManagementAccessList().size());

        // Check value we set 
        _config.setProperty("security.jmx.access(0)", "a");
        _config.setProperty("security.jmx.access(1)", "b");
        serverConfig = new ServerConfiguration(_config);
        List<String> dbs = serverConfig.getManagementAccessList();
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetFrameSize() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(65536, serverConfig.getFrameSize());

        // Check value we set 
        _config.setProperty("advanced.framesize", "23");
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getFrameSize());
    }

    public void testGetManagementSecurityEnabled() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getManagementSecurityEnabled());

        // Check value we set 
        _config.setProperty("management.security-enabled", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getManagementSecurityEnabled());
    }

    public void testGetProtectIOEnabled() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getProtectIOEnabled());

        // Check value we set 
        _config.setProperty("broker.connector.protectio.enabled", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getProtectIOEnabled());
    }

    public void testGetBufferReadLimit() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(262144, serverConfig.getBufferReadLimit());

        // Check value we set 
        _config.setProperty("broker.connector.protectio.readBufferLimitSize", 23);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getBufferReadLimit());
    }

    public void testGetBufferWriteLimit() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(262144, serverConfig.getBufferWriteLimit());

        // Check value we set 
        _config.setProperty("broker.connector.protectio.writeBufferLimitSize", 23);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getBufferWriteLimit());
    }

    public void testGetSynchedClocks() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getSynchedClocks());

        // Check value we set 
        _config.setProperty("advanced.synced-clocks", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getSynchedClocks());
    }

    public void testGetMsgAuth() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getMsgAuth());

        // Check value we set 
        _config.setProperty("security.msg-auth", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getMsgAuth());
    }

    public void testGetJMXPrincipalDatabase() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(null, serverConfig.getJMXPrincipalDatabase());

        // Check value we set 
        _config.setProperty("security.jmx.principal-database", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getJMXPrincipalDatabase());
    }

    public void testGetManagementKeyStorePath() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(null, serverConfig.getManagementKeyStorePath());

        // Check value we set 
        _config.setProperty("management.ssl.keyStorePath", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getManagementKeyStorePath());
    }

    public void testGetManagementSSLEnabled() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getManagementSSLEnabled());

        // Check value we set 
        _config.setProperty("management.ssl.enabled", false);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getManagementSSLEnabled());
    }

    public void testGetManagementKeyStorePassword() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(null, serverConfig.getManagementKeyStorePassword());

        // Check value we set 
        _config.setProperty("management.ssl.keyStorePassword", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getManagementKeyStorePassword());
    }

    public void testGetQueueAutoRegister() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getQueueAutoRegister());

        // Check value we set 
        _config.setProperty("queue.auto_register", false);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getQueueAutoRegister());
    }

    public void testGetManagementEnabled() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getManagementEnabled());

        // Check value we set 
        _config.setProperty("management.enabled", false);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getManagementEnabled());
    }

    public void testSetManagementEnabled() throws ConfigurationException
    {
        // Check value we set 
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        serverConfig.setManagementEnabled(false);
        assertEquals(false, serverConfig.getManagementEnabled());
    }

    public void testGetHeartBeatDelay() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(5, serverConfig.getHeartBeatDelay());

        // Check value we set 
        _config.setProperty("heartbeat.delay", 23);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getHeartBeatDelay());
    }

    public void testGetHeartBeatTimeout() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(2.0, serverConfig.getHeartBeatTimeout());

        // Check value we set 
        _config.setProperty("heartbeat.timeoutFactor", 2.3);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(2.3, serverConfig.getHeartBeatTimeout());
    }

    public void testGetMaximumMessageAge() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getMaximumMessageAge());

        // Check value we set 
        _config.setProperty("maximumMessageAge", 10L);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getMaximumMessageAge());
    }

    public void testGetMaximumMessageCount() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getMaximumMessageCount());

        // Check value we set 
        _config.setProperty("maximumMessageCount", 10L);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getMaximumMessageCount());
    }

    public void testGetMaximumQueueDepth() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getMaximumQueueDepth());

        // Check value we set 
        _config.setProperty("maximumQueueDepth", 10L);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getMaximumQueueDepth());
    }

    public void testGetMaximumMessageSize() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getMaximumMessageSize());

        // Check value we set 
        _config.setProperty("maximumMessageSize", 10L);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getMaximumMessageSize());
    }

    public void testGetMinimumAlertRepeatGap() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(0, serverConfig.getMinimumAlertRepeatGap());

        // Check value we set 
        _config.setProperty("minimumAlertRepeatGap", 10L);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getMinimumAlertRepeatGap());
    }

    public void testGetProcessors() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(4, serverConfig.getProcessors());

        // Check value we set 
        _config.setProperty("connector.processors", 10);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getProcessors());
    }

    public void testGetPort() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(5672, serverConfig.getPort());

        // Check value we set 
        _config.setProperty("connector.port", 10);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(10, serverConfig.getPort());
    }

    public void testGetBind() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals("wildcard", serverConfig.getBind());

        // Check value we set 
        _config.setProperty("connector.bind", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getBind());
    }

    public void testGetReceiveBufferSize() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(32767, serverConfig.getReceiveBufferSize());

        // Check value we set 
        _config.setProperty("connector.socketReceiveBuffer", "23");
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getReceiveBufferSize());
    }

    public void testGetWriteBufferSize() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(32767, serverConfig.getWriteBufferSize());

        // Check value we set 
        _config.setProperty("connector.socketWriteBuffer", "23");
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getWriteBufferSize());
    }

    public void testGetTcpNoDelay() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getTcpNoDelay());

        // Check value we set 
        _config.setProperty("connector.tcpNoDelay", false);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getTcpNoDelay());
    }

    public void testGetEnableExecutorPool() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getEnableExecutorPool());

        // Check value we set 
        _config.setProperty("advanced.filterchain[@enableExecutorPool]", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getEnableExecutorPool());
    }

    public void testGetEnablePooledAllocator() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getEnablePooledAllocator());

        // Check value we set 
        _config.setProperty("advanced.enablePooledAllocator", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getEnablePooledAllocator());
    }

    public void testGetEnableDirectBuffers() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getEnableDirectBuffers());

        // Check value we set 
        _config.setProperty("advanced.enableDirectBuffers", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getEnableDirectBuffers());
    }

    public void testGetEnableSSL() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getEnableSSL());

        // Check value we set 
        _config.setProperty("connector.ssl.enabled", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getEnableSSL());
    }

    public void testGetSSLOnly() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getSSLOnly());

        // Check value we set 
        _config.setProperty("connector.ssl.sslOnly", false);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getSSLOnly());
    }
    
    public void testGetSSLPort() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(8672, serverConfig.getSSLPort());

        // Check value we set 
        _config.setProperty("connector.ssl.port", 23);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getSSLPort());
    }

    public void testGetKeystorePath() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals("none", serverConfig.getKeystorePath());

        // Check value we set 
        _config.setProperty("connector.ssl.keystorePath", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getKeystorePath());
    }
    
    public void testGetKeystorePassword() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals("none", serverConfig.getKeystorePassword());

        // Check value we set 
        _config.setProperty("connector.ssl.keystorePassword", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getKeystorePassword());
    }

    public void testGetCertType() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals("SunX509", serverConfig.getCertType());

        // Check value we set 
        _config.setProperty("connector.ssl.certType", "a");
        serverConfig = new ServerConfiguration(_config);
        assertEquals("a", serverConfig.getCertType());
    }

    public void testGetQpidNIO() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getQpidNIO());

        // Check value we set 
        _config.setProperty("connector.qpidnio", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getQpidNIO());
    }

    public void testGetUseBiasedWrites() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(false, serverConfig.getUseBiasedWrites());

        // Check value we set 
        _config.setProperty("advanced.useWriteBiasedPool", true);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(true, serverConfig.getUseBiasedWrites());
    }

    public void testGetHousekeepingExpiredMessageCheckPeriod() throws ConfigurationException
    {
        // Check default
        ServerConfiguration serverConfig = new ServerConfiguration(_config);
        assertEquals(30000, serverConfig.getHousekeepingExpiredMessageCheckPeriod());

        // Check value we set 
        _config.setProperty("housekeeping.expiredMessageCheckPeriod", 23L);
        serverConfig = new ServerConfiguration(_config);
        assertEquals(23, serverConfig.getHousekeepingExpiredMessageCheckPeriod());
        serverConfig.setHousekeepingExpiredMessageCheckPeriod(42L);
        assertEquals(42, serverConfig.getHousekeepingExpiredMessageCheckPeriod());
    }

    public void testSingleConfiguration() throws IOException, ConfigurationException 
    {
        File fileA = File.createTempFile(getClass().getName(), null);
        fileA.deleteOnExit();
        FileWriter out = new FileWriter(fileA);
        out.write("<broker><connector><port>2342</port><ssl><port>4235</port></ssl></connector></broker>");
        out.close();
        ServerConfiguration conf = new ServerConfiguration(fileA);
        assertEquals(4235, conf.getSSLPort());
    }
    
    public void testCombinedConfiguration() throws IOException, ConfigurationException
    {
        File mainFile = File.createTempFile(getClass().getName(), null);
        File fileA = File.createTempFile(getClass().getName(), null);
        File fileB = File.createTempFile(getClass().getName(), null);
        
        mainFile.deleteOnExit();
        fileA.deleteOnExit();
        fileB.deleteOnExit();
        
        FileWriter out = new FileWriter(mainFile);
        out.write("<configuration><system/>");
        out.write("<xml fileName=\""+fileA.getAbsolutePath()+"\"/>");
        out.write("<xml fileName=\""+fileB.getAbsolutePath()+"\"/>");
        out.write("</configuration>");
        out.close();
        
        out = new FileWriter(fileA);
        out.write("<broker><connector><port>2342</port><ssl><port>4235</port></ssl></connector></broker>");
        out.close();
        
        out = new FileWriter(fileB);
        out.write("<broker><connector><ssl><port>2345</port></ssl><qpidnio>true</qpidnio></connector></broker>");
        out.close();
        
        ServerConfiguration config = new ServerConfiguration(mainFile.getAbsoluteFile());
        assertEquals(4235, config.getSSLPort()); // From first file, not overriden by second
        assertEquals(2342, config.getPort()); // From the first file, not present in the second
        assertEquals(true, config.getQpidNIO()); // From the second file, not present in the first
    }
    
}
