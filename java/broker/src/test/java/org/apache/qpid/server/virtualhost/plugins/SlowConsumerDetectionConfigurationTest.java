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
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionConfiguration;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

import java.util.concurrent.TimeUnit;

/**
 *  Provide Unit Test coverage of the virtualhost SlowConsumer Configuration
 *  This is what controls how often the plugin will execute
 */
public class SlowConsumerDetectionConfigurationTest extends InternalBrokerBaseCase
{

    /**
     * Default Testing:
     *
     * Provide a fully complete and valid configuration specifying 'delay' and
     * 'timeunit' and ensure that it is correctly processed.
     *
     * Ensure no exceptions are thrown and that we get the same values back that
     * were put into the configuration.
     */
    public void testConfigLoadingValidConfig()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        long DELAY=10;
        String TIMEUNIT=TimeUnit.MICROSECONDS.toString();
        xmlconfig.addProperty("delay", String.valueOf(DELAY));
        xmlconfig.addProperty("timeunit", TIMEUNIT);

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

        assertEquals("Delay not correctly returned.", DELAY, config.getDelay());
        assertEquals("TimeUnit not correctly returned.",
                     TIMEUNIT, String.valueOf(config.getTimeUnit()));
    }

    /**
       * Default Testing:
       *
       * Test Missing TimeUnit value gets default.
       *
       * The TimeUnit value is optional and default to SECONDS.
       *
       * Test that if we do not specify a TimeUnit then we correctly get seconds.
       *
       * Also verify that relying on the default does not impact the setting of
       * the 'delay' value.
       *
       */
      public void testConfigLoadingMissingTimeUnitDefaults()
      {
          SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

          XMLConfiguration xmlconfig = new XMLConfiguration();

          long DELAY=10;
          xmlconfig.addProperty("delay", String.valueOf(DELAY));

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

          assertEquals("Delay not correctly returned.", DELAY, config.getDelay());
          assertEquals("Default TimeUnit incorrect", TimeUnit.SECONDS, config.getTimeUnit());
      }    

    /**
     * Input Testing:
     *
     * TimeUnit parsing requires the String value be in UpperCase.
     * Ensure we can handle when the user doesn't know this.
     *
     * Same test as 'testConfigLoadingValidConfig' but checking that
     * the timeunit field is not case sensitive.
     * i.e. the toUpper is being correctly applied.
     */
    public void testConfigLoadingValidConfigStrangeTimeUnit()
    {
        SlowConsumerDetectionConfiguration config = new SlowConsumerDetectionConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();

        long DELAY=10;

        xmlconfig.addProperty("delay", DELAY);
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

        assertEquals("Delay not correctly returned.", DELAY, config.getDelay());
        assertEquals("TimeUnit not correctly returned.",
                     TimeUnit.MICROSECONDS.toString(), String.valueOf(config.getTimeUnit()));

    }

    /**
     * Failure Testing:
     *
     * Test that delay must be long not a string value.
     * Provide a delay as a written value not a long. 'ten'.
     *
     * This should throw a configuration exception which is being trapped and
     * verified to be the right exception, a NumberFormatException.
     *
     */
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

    /**
     * Failure Testing:
     *
     * Test that negative delays are invalid.
     *
     * Delay must be a positive value as negative delay means doesn't make sense.
     *
     * Configuration exception with a useful message should be thrown here.
     *
     */
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

    /**
     * Failure Testing:
     *
     *  Test that delay cannot be 0.
     *
     * A zero delay means run constantly. This is not how VirtualHostTasks
     * are designed to be run so we dis-allow the use of 0 delay.
     *
     * Same test as 'testConfigLoadingInValidDelayNegative' but with a 0 value.
     *
     */
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

    /**
     * Failure Testing:
     *
     * Test that missing delay fails.
     * If we have no delay then we do not pick a default. So a Configuration
     * Exception is thrown.
     *
     * */
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

    /**
     * Failure Testing:
     *
     * Test that erroneous TimeUnit fails.
     *
     * Valid TimeUnit values vary based on the JVM version i.e. 1.6 added HOURS/DAYS etc.
     *
     * We don't test the values for TimeUnit are accepted other than MILLISECONDS in the
     * positive testing at the start.
     *
     * Here we ensure that an erroneous for TimeUnit correctly throws an exception.
     *
     * We test with 'foo', which will never be a TimeUnit
     *
     */
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


}
