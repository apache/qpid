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
package org.apache.qpid.server;


import java.io.File;
import java.util.HashMap;
import java.util.Map;


import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;

public class BrokerTest extends QpidTestCase
{
    private static final String INITIAL_SYSTEM_PROPERTY = "test";
    private static final String INITIAL_SYSTEM_PROPERTY_VALUE = "testValue";

    private File _initialSystemProperties;
    private File _initialConfiguration;
    private File _brokerWork;
    private Broker _broker;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        // create empty initial configuration
        Map<String,Object> initialConfig = new HashMap<>();
        initialConfig.put(ConfiguredObject.NAME, "test");
        initialConfig.put(org.apache.qpid.server.model.Broker.MODEL_VERSION, BrokerModel.MODEL_VERSION);

        ObjectMapper mapper = new ObjectMapper();
        String config = mapper.writeValueAsString(initialConfig);
        _initialConfiguration = TestFileUtils.createTempFile(this, ".initial-config.json", config);
        _brokerWork = TestFileUtils.createTestDirectory("qpid-work", true);
        _initialSystemProperties = TestFileUtils.createTempFile(this, ".initial-system.properties",
                INITIAL_SYSTEM_PROPERTY + "=" + INITIAL_SYSTEM_PROPERTY_VALUE
                + "\nQPID_WORK=" +  _brokerWork.getAbsolutePath() + "_test");
        setTestSystemProperty("QPID_WORK", _brokerWork.getAbsolutePath());
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_broker != null)
            {
                _broker.shutdown();
            }
            System.clearProperty(INITIAL_SYSTEM_PROPERTY);
            FileUtils.delete(_brokerWork, true);
            FileUtils.delete(_initialSystemProperties, false);
            FileUtils.delete(_initialConfiguration, false);
        }
    }

    public void testInitialSystemPropertiesAreSetOnBrokerStartup() throws Exception
    {
        BrokerOptions options = new BrokerOptions();
        options.setInitialSystemProperties(_initialSystemProperties.getAbsolutePath());
        options.setSkipLoggingConfiguration(true);
        options.setStartupLoggedToSystemOut(true);
        options.setInitialConfigurationLocation(_initialConfiguration.getAbsolutePath());
        _broker = new Broker();
        _broker.startup(options);

        // test JVM system property should be set from initial system config file
        assertEquals("Unexpected JVM system property", INITIAL_SYSTEM_PROPERTY_VALUE, System.getProperty(INITIAL_SYSTEM_PROPERTY));

        // existing system property should not be overridden
        assertEquals("Unexpected QPID_WORK system property", _brokerWork.getAbsolutePath(), System.getProperty("QPID_WORK"));
    }
}
