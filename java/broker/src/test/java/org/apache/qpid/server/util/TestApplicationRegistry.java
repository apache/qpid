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

import java.util.Properties;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.logging.NullRootMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.database.PropertiesPrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;

public class TestApplicationRegistry extends ApplicationRegistry
{

    public TestApplicationRegistry(ServerConfiguration config) throws ConfigurationException
    {
        super(config);
    }

    @Override
    public void initialise() throws Exception
    {
        CurrentActor.setDefault(new BrokerActor(new NullRootMessageLogger()));
        super.initialise();
    }

    /**
     * @see org.apache.qpid.server.registry.ApplicationRegistry#createAuthenticationManager()
     */
    @Override
    protected AuthenticationManager createAuthenticationManager() throws ConfigurationException
    {
        final Properties users = new Properties();
        users.put("guest","guest");
        users.put("admin","admin");

        final PropertiesPrincipalDatabase ppd = new PropertiesPrincipalDatabase(users);

        AuthenticationManager pdam =  new PrincipalDatabaseAuthenticationManager()
        {

            /**
             * @see org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager#configure(org.apache.qpid.server.configuration.plugins.ConfigurationPlugin)
             */
            @Override
            public void configure(ConfigurationPlugin config) throws ConfigurationException
            {
                // We don't pass configuration to this test instance.
            }

            @Override
            public void initialise()
            {
                setPrincipalDatabase(ppd);

                super.initialise();
            }
        };

        pdam.initialise();

        return pdam;
    }

}


