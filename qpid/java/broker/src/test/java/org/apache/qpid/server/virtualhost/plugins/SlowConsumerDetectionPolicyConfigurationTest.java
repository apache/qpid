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
package org.apache.qpid.server.virtualhost.plugins;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionPolicyConfiguration;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

/**
 * Test class to ensure that the policy configuration can be processed.
 */
public class SlowConsumerDetectionPolicyConfigurationTest extends InternalBrokerBaseCase
{

    /**
     * Input Testing:
     *
     * Test that a given String can be set and retrieved through the configuration
     *
     * No validation is being performed to ensure that the policy exists. Only
     * that a value can be set for the policy.
     *
     */
    public void testConfigLoadingValidConfig()
    {
        SlowConsumerDetectionPolicyConfiguration config = new SlowConsumerDetectionPolicyConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        String policyName = "TestPolicy";
        xmlconfig.addProperty("name", policyName);

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
        }
        catch (ConfigurationException e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

        assertEquals("Policy name not retrieved as expected.",
                     policyName, config.getPolicyName());
    }

    /**
     * Failure Testing:
     *
     * Test that providing a configuration section without the 'name' field
     * causes an exception to be thrown.
     *
     * An empty configuration is provided and the thrown exception message
     * is checked to confirm the right reason. 
     *
     */
    public void testConfigLoadingInValidConfig()
    {
        SlowConsumerDetectionPolicyConfiguration config = new SlowConsumerDetectionPolicyConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();


        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("Config is invalid so won't validate.");            
        }
        catch (ConfigurationException e)
        {
            e.printStackTrace();
            assertEquals("Exception message not as expected.", "No Slow consumer policy defined.", e.getMessage());
        }
    }

}
