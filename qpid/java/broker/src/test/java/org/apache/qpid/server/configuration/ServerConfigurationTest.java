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

import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.test.utils.QpidTestCase;

public class ServerConfigurationTest extends QpidTestCase
{
    private XMLConfiguration _xml = new XMLConfiguration();
    private ServerConfiguration _config ;
    
    public void setUp() throws Exception
    {
        _config = new ServerConfiguration(_xml);
        TestApplicationRegistry instance = new TestApplicationRegistry(_config);
        ApplicationRegistry.initialise(instance);
        _config.initialise();
    }
    
    public void testGetJMXManagementPort() throws Exception
    {
        _config.setJMXManagementPort(23);
        assertEquals(23, _config.getJMXManagementPort());
    }

    public void testSetJMXManagementPort() throws Exception
    {
        _xml.setProperty("management.jmxport", 42);
        _config.initialise();
        assertEquals(42, _config.getJMXManagementPort());
    }

    public void testGetPlatformMbeanserver() throws Exception
    {
        assertTrue(_config.getPlatformMbeanserver());
    }

    public void testSetPlatformMbeanserver() throws Exception
    {
        _xml.setProperty("management.platform-mbeanserver", false);
        _config.initialise();
        assertFalse(_config.getPlatformMbeanserver());
    }

    public void testGetPluginDirectory() throws Exception
    {
        assertNull(_config.getPluginDirectory());
    }

    public void testSetPluginDirectory() throws Exception
    {
        _xml.setProperty("plugin-directory", "/path/to/plugins");
        _config.initialise();
        assertEquals("/path/to/plugins", _config.getPluginDirectory());
    }

    public void testGetCacheDirectory() throws Exception
    {
        assertNull(_config.getCacheDirectory());
    }

    public void testSetCacheDirectory() throws Exception
    {
        _xml.setProperty("cache-directory", "/path/to/cache");
        _config.initialise();
        assertEquals("/path/to/cache", _config.getCacheDirectory());
    }

    public void testGetPrincipalDatabaseNames() throws Exception
    {
        assertEquals(0, _config.getPrincipalDatabaseNames().size());
    }

    public void testSetPrincipalDatabaseNames() throws Exception
    {
        _xml.setProperty("security.principal-databases.principal-database(0).name", "a");
        _xml.setProperty("security.principal-databases.principal-database(1).name", "b");
        _config.initialise();
        List<String> dbs = _config.getPrincipalDatabaseNames();
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetPrincipalDatabaseClass() throws Exception
    {
        assertEquals(0, _config.getPrincipalDatabaseClass().size());
    }

    public void testSetPrincipalDatabaseClass() throws Exception
    {
        _xml.setProperty("security.principal-databases.principal-database(0).class", "a");
        _xml.setProperty("security.principal-databases.principal-database(1).class", "b");
        _config.initialise();
        List<String> dbs = _config.getPrincipalDatabaseClass();
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetPrincipalDatabaseAttributeNames() throws Exception
    {
        assertEquals(0, _config.getPrincipalDatabaseAttributeNames(1).size());
    }

    public void testSetPrincipalDatabaseAttributeNames() throws Exception
    {
        _xml.setProperty("security.principal-databases.principal-database(0).attributes(0).attribute.name", "a");
        _xml.setProperty("security.principal-databases.principal-database(0).attributes(1).attribute.name", "b");
        _config.initialise();
        List<String> dbs = _config.getPrincipalDatabaseAttributeNames(0);
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetPrincipalDatabaseAttributeValues() throws Exception
    {
        assertEquals(0, _config.getPrincipalDatabaseAttributeValues(1).size());
    }

    public void testSetPrincipalDatabaseAttributeValues() throws Exception
    {
        _xml.setProperty("security.principal-databases.principal-database(0).attributes(0).attribute.value", "a");
        _xml.setProperty("security.principal-databases.principal-database(0).attributes(1).attribute.value", "b");
        _config.initialise();
        List<String> dbs = _config.getPrincipalDatabaseAttributeValues(0);
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetManagementAccessList() throws Exception
    {
        assertEquals(0, _config.getManagementAccessList().size());
    }

    public void testSetManagementAccessList() throws Exception
    {
        _xml.setProperty("security.jmx.access(0)", "a");
        _xml.setProperty("security.jmx.access(1)", "b");
        _config.initialise();
        List<String> dbs = _config.getManagementAccessList();
        assertEquals(2, dbs.size());
        assertEquals("a", dbs.get(0));
        assertEquals("b", dbs.get(1));
    }

    public void testGetFrameSize() throws Exception
    {
        assertEquals(65536, _config.getFrameSize());
    }

    public void testSetFrameSize() throws Exception
    {
        _xml.setProperty("advanced.framesize", "23");
        _config.initialise();
        assertEquals(23, _config.getFrameSize());
    }

    public void testGetProtectIOEnabled() throws Exception
    {
        assertFalse(_config.getProtectIOEnabled());
    }

    public void testSetProtectIOEnabled() throws Exception
    {
        _xml.setProperty(ServerConfiguration.CONNECTOR_PROTECTIO_ENABLED, true);
        _config.initialise();
        assertTrue(_config.getProtectIOEnabled());
    }

    public void testGetBufferReadLimit() throws Exception
    {
        assertEquals(262144, _config.getBufferReadLimit());
    }

    public void testSetBufferReadLimit() throws Exception
    {
        _xml.setProperty(ServerConfiguration.CONNECTOR_PROTECTIO_READ_BUFFER_LIMIT_SIZE, 23);
        _config.initialise();
        assertEquals(23, _config.getBufferReadLimit());
    }

    public void testGetBufferWriteLimit() throws Exception
    {
        assertEquals(262144, _config.getBufferWriteLimit());
    }

    public void testSetBufferWriteLimit() throws Exception
    {
        _xml.setProperty(ServerConfiguration.CONNECTOR_PROTECTIO_WRITE_BUFFER_LIMIT_SIZE, 23);
        _config.initialise();
        assertEquals(23, _config.getBufferWriteLimit());
    }

    public void testGetStatusEnabled() throws Exception
    {
        assertEquals(ServerConfiguration.DEFAULT_STATUS_UPDATES.equalsIgnoreCase("on"),
                     _config.getStatusUpdatesEnabled());
    }

    public void testSetStatusEnabled() throws Exception
    {
        _xml.setProperty(ServerConfiguration.STATUS_UPDATES, "off");
        _config.initialise();
        assertFalse(_config.getStatusUpdatesEnabled());
    }

    public void testSetStatusEnabledError() throws Exception
    {
        _xml.setProperty(ServerConfiguration.STATUS_UPDATES, "Yes Please");
        _config.initialise();
        assertFalse(_config.getStatusUpdatesEnabled());
    }

    public void testGetSynchedClocks() throws Exception
    {
        assertFalse(_config.getSynchedClocks());
    }

    public void testSetSynchedClocks() throws Exception
    {
        _xml.setProperty("advanced.synced-clocks", true);
        _config.initialise();
        assertTrue(_config.getSynchedClocks());
    }

    public void testGetLocale() throws Exception
    {
        // The Default is what ever the VMs default is
        Locale defaultLocale = Locale.getDefault();

        assertEquals(defaultLocale, _config.getLocale());
    }

    public void testSetLocaleOne() throws Exception
    {
        Locale update = new Locale("es");
        _xml.setProperty(ServerConfiguration.ADVANCED_LOCALE, "es");
        _config.initialise();
        assertEquals(update, _config.getLocale());
    }

    public void testSetLocaleTwo() throws Exception
    {
        Locale update = new Locale("es","ES");
        _xml.setProperty(ServerConfiguration.ADVANCED_LOCALE, "es_ES");
        _config.initialise();
        assertEquals(update, _config.getLocale());
    }

    public void testSetLocaleThree() throws Exception
    {
        Locale update = new Locale("es","ES", "Traditional_WIN");
        _xml.setProperty(ServerConfiguration.ADVANCED_LOCALE, "es_ES_Traditional_WIN");
        _config.initialise();
        assertEquals(update, _config.getLocale());
    }

    public void testGetMsgAuth() throws Exception
    {
        assertFalse(_config.getMsgAuth());
    }

    public void testSetMsgAuth() throws Exception
    {
        _xml.setProperty("security.msg-auth", true);
        _config.initialise();
        assertTrue(_config.getMsgAuth());
    }

    public void testGetJMXPrincipalDatabase() throws Exception
    {
        assertNull(_config.getJMXPrincipalDatabase());
    }

    public void testSetJMXPrincipalDatabase() throws Exception
    {
        _xml.setProperty("security.jmx.principal-database", "a");
        _config.initialise();
        assertEquals("a", _config.getJMXPrincipalDatabase());
    }

    public void testGetManagementKeyStorePath() throws Exception
    {
        assertNull(_config.getManagementKeyStorePath());
    }

    public void testSetManagementKeyStorePath() throws Exception
    {
        _xml.setProperty("management.ssl.keyStorePath", "a");
        _config.initialise();
        assertEquals("a", _config.getManagementKeyStorePath());
    }

    public void testGetManagementSSLEnabled() throws Exception
    {
        assertTrue(_config.getManagementSSLEnabled());
    }

    public void testSetManagementSSLEnabled() throws Exception
    {
        _xml.setProperty("management.ssl.enabled", false);
        _config.initialise();
        assertFalse(_config.getManagementSSLEnabled());
    }

    public void testGetManagementKeyStorePassword() throws Exception
    {
        assertNull(_config.getManagementKeyStorePassword());
    }

    public void testSetManagementKeyStorePassword() throws Exception
    {
        _xml.setProperty("management.ssl.keyStorePassword", "a");
        _config.initialise();
        assertEquals("a", _config.getManagementKeyStorePassword());
    }

    public void testGetQueueAutoRegister() throws Exception
    {
        assertTrue(_config.getQueueAutoRegister());
    }

    public void testSetQueueAutoRegister() throws Exception
    {
        _xml.setProperty("queue.auto_register", false);
        _config.initialise();
        assertFalse(_config.getQueueAutoRegister());
    }

    public void testGetManagementEnabled() throws Exception
    {
        _config.setManagementEnabled(false);
        _config.initialise();
        assertFalse(_config.getManagementEnabled());
    }

    public void testSetManagementEnabled() throws Exception
    {
        _xml.setProperty("management.enabled", false);
        _config.initialise();
        assertFalse(_config.getManagementEnabled());
    }

    public void testGetHeartBeatDelay() throws Exception
    {
        assertEquals(5, _config.getHeartBeatDelay());
    }

    public void testSetHeartBeatDelay() throws Exception
    {
        _xml.setProperty("heartbeat.delay", 23);
        _config.initialise();
        assertEquals(23, _config.getHeartBeatDelay());
    }

    public void testGetHeartBeatTimeout() throws Exception
    {
        assertEquals(2.0, _config.getHeartBeatTimeout());
    }

    public void testSetHeartBeatTimeout() throws Exception
    {
        _xml.setProperty("heartbeat.timeoutFactor", 2.3);
        _config.initialise();
        assertEquals(2.3, _config.getHeartBeatTimeout());
    }

    public void testGetMaximumMessageAge() throws Exception
    {
        assertEquals(0, _config.getMaximumMessageAge());
    }

    public void testSetMaximumMessageAge() throws Exception
    {
        _xml.setProperty("maximumMessageAge", 10L);
        _config.initialise();
        assertEquals(10, _config.getMaximumMessageAge());
    }

    public void testGetMaximumMessageCount() throws Exception
    {
        assertEquals(0, _config.getMaximumMessageCount());
    }

    public void testSetMaximumMessageCount() throws Exception
    {
        _xml.setProperty("maximumMessageCount", 10L);
        _config.initialise();
        assertEquals(10, _config.getMaximumMessageCount());
    }

    public void testGetMaximumQueueDepth() throws Exception
    {
        assertEquals(0, _config.getMaximumQueueDepth());
    }

    public void testSetMaximumQueueDepth() throws Exception
    {
        _xml.setProperty("maximumQueueDepth", 10L);
        _config.initialise();
        assertEquals(10, _config.getMaximumQueueDepth());
    }

    public void testGetMaximumMessageSize() throws Exception
    {
        assertEquals(0, _config.getMaximumMessageSize());
    }

    public void testSetMaximumMessageSize() throws Exception
    {
        _xml.setProperty("maximumMessageSize", 10L);
        _config.initialise();
        assertEquals(10, _config.getMaximumMessageSize());
    }

    public void testGetMinimumAlertRepeatGap() throws Exception
    {
        assertEquals(0, _config.getMinimumAlertRepeatGap());
    }

    public void testSetMinimumAlertRepeatGap() throws Exception
    {
        _xml.setProperty("minimumAlertRepeatGap", 10L);
        _config.initialise();
        assertEquals(10, _config.getMinimumAlertRepeatGap());
    }

    public void testGetProcessors() throws Exception
    {
        assertEquals(4, _config.getProcessors());
    }

    public void testSetProcessors() throws Exception
    {
        _xml.setProperty("connector.processors", 10);
        _config.initialise();
        assertEquals(10, _config.getProcessors());
    }

    public void testGetPort() throws Exception
    {
        assertNotNull(_config.getPorts());
        assertEquals(1, _config.getPorts().size());
        assertEquals("5672", _config.getPorts().get(0));
    }

    public void testSetPort() throws Exception
    {
        _xml.setProperty("connector.port", "10");
        _config.initialise();
        assertNotNull(_config.getPorts());
        assertEquals(1, _config.getPorts().size());
        assertEquals("10", _config.getPorts().get(0));
    }

    public void testGetBind() throws Exception
    {
        assertEquals("*", _config.getBind());
    }

    public void testSetBind() throws Exception
    {
        _xml.setProperty("connector.bind", "a");
        _config.initialise();
        assertEquals("a", _config.getBind());
    }

    public void testGetReceiveBufferSize() throws Exception
    {
        assertEquals(32767, _config.getReceiveBufferSize());
    }

    public void testSetReceiveBufferSize() throws Exception
    {
        _xml.setProperty("connector.socketReceiveBuffer", "23");
        _config.initialise();
        assertEquals(23, _config.getReceiveBufferSize());
    }

    public void testGetWriteBufferSize() throws Exception
    {
        _config.initialise();
        assertEquals(32767, _config.getWriteBufferSize());
    }

    public void testSetWriteBufferSize() throws Exception
    {
        _xml.setProperty("connector.socketWriteBuffer", "23");
        _config.initialise();
        assertEquals(23, _config.getWriteBufferSize());
    }

    public void testGetTcpNoDelay() throws Exception
    {
        assertTrue(_config.getTcpNoDelay());
    }

    public void testSetTcpNoDelay() throws Exception
    {
        _xml.setProperty("connector.tcpNoDelay", false);
        _config.initialise();
        assertFalse(_config.getTcpNoDelay());
    }

    public void testGetEnableExecutorPool() throws Exception
    {
        assertFalse(_config.getEnableExecutorPool());
    }

    public void testSetEnableExecutorPool() throws Exception
    {
        _xml.setProperty("advanced.filterchain[@enableExecutorPool]", true);
        _config.initialise();
        assertTrue(_config.getEnableExecutorPool());
    }

    public void testGetEnablePooledAllocator() throws Exception
    {
        assertFalse(_config.getEnablePooledAllocator());
    }

    public void testSetEnablePooledAllocator() throws Exception
    {
        _xml.setProperty("advanced.enablePooledAllocator", true);
        _config.initialise();
        assertTrue(_config.getEnablePooledAllocator());
    }

    public void testGetEnableDirectBuffers() throws Exception
    {
        assertFalse(_config.getEnableDirectBuffers());
    }

    public void testSetEnableDirectBuffers() throws Exception
    {
        _xml.setProperty("advanced.enableDirectBuffers", true);
        _config.initialise();
        assertTrue(_config.getEnableDirectBuffers());
    }

    public void testGetEnableSSL() throws Exception
    {
        assertFalse(_config.getEnableSSL());
    }

    public void testSetEnableSSL() throws Exception
    {
        _xml.setProperty("connector.ssl.enabled", true);
        _config.initialise();
        assertTrue(_config.getEnableSSL());
    }

    public void testGetSSLOnly() throws Exception
    {
        assertFalse(_config.getSSLOnly());
    }

    public void testSetSSLOnly() throws Exception
    {
        _xml.setProperty("connector.ssl.sslOnly", true);
        _config.initialise();
        assertTrue(_config.getSSLOnly());
    }

    public void testGetSSLPort() throws Exception
    {
        assertEquals(8672, _config.getSSLPort());
    }

    public void testSetSSLPort() throws Exception
    {
        _xml.setProperty("connector.ssl.port", 23);
        _config.initialise();
        assertEquals(23, _config.getSSLPort());
    }

    public void testGetKeystorePath() throws Exception
    {
        assertEquals("none", _config.getKeystorePath());
    }

    public void testSetKeystorePath() throws Exception
    {
        _xml.setProperty("connector.ssl.keystorePath", "a");
        _config.initialise();
        assertEquals("a", _config.getKeystorePath());
    }

    public void testGetKeystorePassword() throws Exception
    {
        assertEquals("none", _config.getKeystorePassword());
    }

    public void testSetKeystorePassword() throws Exception
    {
        _xml.setProperty("connector.ssl.keystorePassword", "a");
        _config.initialise();
        assertEquals("a", _config.getKeystorePassword());
    }

    public void testGetCertType() throws Exception
    {
        assertEquals("SunX509", _config.getCertType());
    }

    public void testSetCertType() throws Exception
    {
        _xml.setProperty("connector.ssl.certType", "a");
        _config.initialise();
        assertEquals("a", _config.getCertType());
    }

    public void testGetQpidNIO() throws Exception
    {
        assertFalse(_config.getQpidNIO());
    }

    public void testSetQpidNIO() throws Exception
    {
        _xml.setProperty("connector.qpidnio", true);
        _config.initialise();
        assertTrue(_config.getQpidNIO());
    }

    public void testGetUseBiasedWrites() throws Exception
    {
        assertFalse(_config.getUseBiasedWrites());
    }

    public void testSetUseBiasedWrites() throws Exception
    {
        _xml.setProperty("advanced.useWriteBiasedPool", true);
        _config.initialise();
        assertTrue(_config.getUseBiasedWrites());
    }

    public void testGetHousekeepingExpiredMessageCheckPeriod() throws Exception
    {
        assertEquals(30000, _config.getHousekeepingCheckPeriod());
    }

    public void testSetHousekeepingExpiredMessageCheckPeriod() throws Exception
    {
        _xml.setProperty("housekeeping.expiredMessageCheckPeriod", 23L);
        _config.initialise();
        assertEquals(23, _config.getHousekeepingCheckPeriod());
        _config.setHousekeepingExpiredMessageCheckPeriod(42L);
        assertEquals(42, _config.getHousekeepingCheckPeriod());
    }
}
