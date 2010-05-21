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
package org.apache.qpid.server.virtualhost.plugin;

import junit.framework.TestCase;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.plugin.SlowConsumerDetectionConfiguration;

import java.util.concurrent.TimeUnit;

/** Provide Unit Test coverage of the SlowConsumerConfiguration */
public class SlowConsumerDetectionConfigurationTest extends TestCase
{

    public void testConfigLoadingValidConfig()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delay", "10");
        xmlconfig.addProperty("timeunit", TimeUnit.MICROSECONDS.toString());

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
    }

    /**
     * TimeUnit parsing requires the String value be in UpperCase.
     * Ensure we can handle when the user doesn't know this.
     */
    public void testConfigLoadingValidConfigStrangeTimeUnit()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delay", "10");
        xmlconfig.addProperty("timeunit", "MiCrOsEcOnDs");

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
    }

    /** Test that delay must be long not a string value. */
    public void testConfigLoadingInValidDelayString()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delay", "ten");
        xmlconfig.addProperty("timeunit", TimeUnit.MICROSECONDS.toString());

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("Configuration should fail to validate");
        }
        catch (ConfigurationException e)
        {
            Throwable cause = e.getCause();

            assertEquals("Cause not correct", NumberFormatException.class, cause.getClass());
        }
    }

    /** Test that negative delays are invalid */
    public void testConfigLoadingInValidDelayNegative()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delay", "-10");
        xmlconfig.addProperty("timeunit", TimeUnit.MICROSECONDS.toString());

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("Configuration should fail to validate");
        }
        catch (ConfigurationException e)
        {
            Throwable cause = e.getCause();

            assertNotNull("Configuration Exception must not be null.", cause);
            assertEquals("Cause not correct",
                         ConfigurationException.class, cause.getClass());
            assertEquals("Incorrect message.",
                         "SlowConsumerDetectionConfiguration: 'delay' must be a Positive Long value.",
                         cause.getMessage());
        }
    }

    /** Tet that delay cannot be 0 */
    public void testConfigLoadingInValidDelayZero()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delay", "0");
        xmlconfig.addProperty("timeunit", TimeUnit.MICROSECONDS.toString());

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        try
        {
            config.setConfiguration("", composite);
            fail("Configuration should fail to validate");
        }
        catch (ConfigurationException e)
        {
            Throwable cause = e.getCause();

            assertNotNull("Configuration Exception must not be null.", cause);
            assertEquals("Cause not correct",
                         ConfigurationException.class, cause.getClass());
            assertEquals("Incorrect message.",
                         "SlowConsumerDetectionConfiguration: 'delay' must be a Positive Long value.",
                         cause.getMessage());
        }
    }

    /** Test that missing delay fails */
    public void testConfigLoadingInValidMissingDelay()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("timeunit", TimeUnit.SECONDS.toString());

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);
        try
        {
            config.setConfiguration("", composite);
            fail("Configuration should fail to validate");
        }
        catch (ConfigurationException e)
        {
            assertEquals("Incorrect message.", "SlowConsumerDetectionConfiguration: unable to configure invalid delay:null", e.getMessage());
        }
    }

    /** Test that erroneous TimeUnit fails */
    public void testConfigLoadingInValidTimeUnit()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        String TIMEUNIT = "foo";
        XMLConfiguration xmlconfig = new XMLConfiguration();
                                                                                
        xmlconfig.addProperty("delay", "10");
        xmlconfig.addProperty("timeunit", TIMEUNIT);

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);
        try
        {
            config.setConfiguration("", composite);
            fail("Configuration should fail to validate");
        }
        catch (ConfigurationException e)
        {
            assertEquals("Incorrect message.", "Unable to configure Slow Consumer Detection invalid TimeUnit:" + TIMEUNIT, e.getMessage());
        }
    }

    /** Test Missing TimeUnit value gets default */
    public void testConfigLoadingMissingTimeUnitDefaults()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        xmlconfig.addProperty("delay", "10");

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

        assertEquals("Default TimeUnit incorrect", TimeUnit.SECONDS, config.getTimeUnit());
    }

}
