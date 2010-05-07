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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.database.PropertiesPrincipalDatabaseManager;

import java.util.NoSuchElementException;
import java.util.Properties;

public class NullApplicationRegistry extends ApplicationRegistry
{
    // Private Exception to track tests that cause Log Actor to become unset.
    private Exception _startup;

    public NullApplicationRegistry() throws ConfigurationException
    {
        this(new ServerConfiguration(new PropertiesConfiguration()));
        _logger.error("Creating NAR:"+this);
    }

    public NullApplicationRegistry(ServerConfiguration config) throws ConfigurationException
    {
        super(config);

        addTestVhost();

        _logger.error("Creating NAR with config:"+this);
    }

    private void addTestVhost() throws ConfigurationException
    {
        if (_configuration.getVirtualHostConfig("test") == null)
        {
            PropertiesConfiguration vhostProps = new PropertiesConfiguration();
            VirtualHostConfiguration hostConfig = new VirtualHostConfiguration("test", vhostProps);
            _configuration.setVirtualHostConfig(hostConfig);
            _configuration.setDefaultVirtualHost("test");
        }
    }


    @Override
    public void initialise(int instanceID) throws Exception
    {
        _logger.info("Initialising NullApplicationRegistry(" + this + ")");

        _configuration.setHousekeepingExpiredMessageCheckPeriod(200);

        super.initialise(instanceID);

        // Tests don't correctly setup logging
        CurrentActor.set(new TestLogActor(_rootMessageLogger));
        _startup = new Exception("NAR Test didn't correctly setup Log Actors");
    }

    /**
     * Create a user data base with just a single user guest with pwd guest.
     * @param configuration This is ignored here as it will be empty.
     */
    @Override
    protected void createDatabaseManager(ServerConfiguration configuration)
    {
        Properties users = new Properties();
        users.put("guest", "guest");
        _databaseManager = new PropertiesPrincipalDatabaseManager("default", users);
    }


    @Override
    public void close() throws Exception
    {        
        try
        {
            _logger.error("Closing NAR:"+this);            
            CurrentActor.set(new BrokerActor(_rootMessageLogger));
            super.close();
        }
        finally
        {
            try
            {
                CurrentActor.remove();
            }
            catch (NoSuchElementException npe)
            {
                _startup.printStackTrace();
                _startup.printStackTrace(System.err);
            }

        }
    }
}



