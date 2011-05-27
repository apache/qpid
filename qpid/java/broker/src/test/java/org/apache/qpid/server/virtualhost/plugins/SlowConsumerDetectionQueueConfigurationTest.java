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
 */
package org.apache.qpid.server.virtualhost.plugins;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionQueueConfiguration;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

/**
 * Unit test the QueueConfiguration processing.
 *
 * This is slightly awkward as the {@link SlowConsumerDetectionQueueConfiguration}
 * requries that a policy be available.
 * <p>
 * So all the Valid test much catch the ensuing {@link ConfigurationException} and
 * validate that the error is due to a lack of a valid policy.
 */
public class SlowConsumerDetectionQueueConfigurationTest extends InternalBrokerBaseCase
{
    /**
     * Test a fully loaded configuration file.
     *
     * It is not an error to have all control values specified.
     * <p>
     * Here we need to catch the {@link ConfigurationException} that ensues due to lack
     * of a policy plugin.
     */
    public void testConfigLoadingValidConfig()
    {
        SlowConsumerDetectionQueueConfiguration config = new SlowConsumerDetectionQueueConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("messageAge", "60000");
        xmlconfig.addProperty("depth", "1024");
        xmlconfig.addProperty("messageCount", "10");

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("No Policies are avaialbe to load in a unit test");
        }
        catch (ConfigurationException e)
        {
            assertTrue("Exception message incorrect, was: " + e.getMessage(),
                    e.getMessage().startsWith("No Slow Consumer Policy specified. Known Policies:["));
        }
    }

    /**
     * When we do not specify any control value then a {@link ConfigurationException}
     * must be thrown to remind us.
     */
    public void testConfigLoadingMissingConfig()
    {
        SlowConsumerDetectionQueueConfiguration config = new SlowConsumerDetectionQueueConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("No Policies are avaialbe to load in a unit test");
        }
        catch (ConfigurationException e)
        {

            assertEquals("At least one configuration property('messageAge','depth'" +
                         " or 'messageCount') must be specified.", e.getMessage());
        }
    }

    /**
     * Setting messageAge on its own is enough to have a valid configuration
     *
     * Here we need to catch the {@link ConfigurationException} that ensues due to lack
     * of a policy plugin.
     */
    public void testConfigLoadingMessageAgeOk()
    {
        SlowConsumerDetectionQueueConfiguration config = new SlowConsumerDetectionQueueConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("messageAge", "60000");

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("No Policies are avaialbe to load in a unit test");
        }
        catch (ConfigurationException e)
        {
            assertTrue("Exception message incorrect, was: " + e.getMessage(),
                    e.getMessage().startsWith("No Slow Consumer Policy specified. Known Policies:["));
        }
    }

    /**
     * Setting depth on its own is enough to have a valid configuration.
     *
     * Here we need to catch the {@link ConfigurationException} that ensues due to lack
     * of a policy plugin.
     */
    public void testConfigLoadingDepthOk()
    {
        SlowConsumerDetectionQueueConfiguration config = new SlowConsumerDetectionQueueConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("depth", "1024");

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("No Policies are avaialbe to load in a unit test");
        }
        catch (ConfigurationException e)
        {
            assertTrue("Exception message incorrect, was: " + e.getMessage(),
                    e.getMessage().startsWith("No Slow Consumer Policy specified. Known Policies:["));
        }
    }

    /**
     * Setting messageCount on its own is enough to have a valid configuration.
     *
     * Here we need to catch the {@link ConfigurationException} that ensues due to lack
     * of a policy plugin.
     */
    public void testConfigLoadingMessageCountOk()
    {
        SlowConsumerDetectionQueueConfiguration config = new SlowConsumerDetectionQueueConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("messageCount", "10");

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("No Policies are avaialbe to load in a unit test");
        }
        catch (ConfigurationException e)
        {
            assertTrue("Exception message incorrect, was: " + e.getMessage(),
                    e.getMessage().startsWith("No Slow Consumer Policy specified. Known Policies:["));
        }
    }
}
