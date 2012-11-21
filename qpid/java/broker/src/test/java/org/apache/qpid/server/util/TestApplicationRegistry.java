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
package org.apache.qpid.server.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.XMLConfigurationEntryStore;
import org.apache.qpid.server.logging.NullRootMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.manager.TestAuthenticationManagerFactory;

public class TestApplicationRegistry extends ApplicationRegistry
{

    public TestApplicationRegistry(Configuration config) throws ConfigurationException
    {
        super(createStore(config));
    }

    @Override
    public void initialise() throws Exception
    {
        LoggingManagementFacade.configure("test-profiles/log4j-test.xml");

        super.initialise();

        CurrentActor.setDefault(new BrokerActor(new NullRootMessageLogger()));
        GenericActor.setDefaultMessageLogger(new NullRootMessageLogger());
    }

    private static ConfigurationEntryStore createStore(Configuration config) throws ConfigurationException
    {
        File file;
        try
        {
            file = File.createTempFile("_config", ".xml");
        }
        catch (IOException e)
        {
            throw new ConfigurationException("Cannot create configuration file");
        }
        XMLConfiguration xmlConfiguration = null;
        if (config instanceof XMLConfiguration)
        {
            xmlConfiguration = (XMLConfiguration)config;
        }
        else
        {
            xmlConfiguration = new XMLConfiguration(ConfigurationUtils.convertToHierarchical(config));
        }
        xmlConfiguration.addProperty("security." + TestAuthenticationManagerFactory.TEST_AUTH_MANAGER_MARKER, "");
        xmlConfiguration.save(file);
        return new XMLConfigurationEntryStore(file);
    }
}


