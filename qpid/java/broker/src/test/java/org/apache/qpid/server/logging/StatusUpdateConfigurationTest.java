/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.logging;

import junit.framework.TestCase;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * Set of test to validate the effects of the changes made to the
 * ServerConfiguration to enable the enabling/disabling of status update
 * messages.
 *
 * The default is to on.
 */
public class StatusUpdateConfigurationTest extends TestCase
{

    /**
     * Validate that with no configuration the status updates will default to
     * enabled.
     * @throws org.apache.commons.configuration.ConfigurationException
     * - if there was a problem in creating the configuratino
     */
    public void testEnabled() throws ConfigurationException
    {

        ServerConfiguration serverConfig = new ServerConfiguration(
                new PropertiesConfiguration());

        assertTrue("Status Updates not enabled as expected.",
                   serverConfig.getStatusUpdates());
    }


    /**
     * Validate that through the config it is possible to disable status updates
     * @throws org.apache.commons.configuration.ConfigurationException
     * - if there was a problem in creating the configuratino
     */
    public void testUpdateControls() throws ConfigurationException
    {

        Configuration config = new PropertiesConfiguration();
        ServerConfiguration serverConfig = new ServerConfiguration(config);

        config.setProperty("status-updates", "off");


        assertFalse("Status Updates should not be enabled.",
                   serverConfig.getStatusUpdates());
    }
}
