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
 *
 */
package org.apache.qpid.disttest;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.test.utils.QpidTestCase;

public class ArgumentParserTest extends QpidTestCase
{
    private static final String TEST_CONFIG_FILENAME = "ControllerRunnerTest-test-config-filename.json";
    private static final String JNDI_CONFIG_FILENAME = "ControllerRunnerTest-jndi-config-filename.properties";
    private static final String DISTRIBUTED_MODE = "true";

    public static final String TEST_CONFIG_PROP = "test-config";
    public static final String JNDI_CONFIG_PROP = "jndi-config";
    public static final String DISTRIBUTED_PROP = "distributed";

    public static final String TEST_CONFIG_DEFAULT = "perftests-config.json";
    public static final String JNDI_CONFIG_DEFAULT = "perftests-jndi.properties";
    public static final String DISTRIBUTED_DEFAULT = "false";

    private Map<String,String> _options = new HashMap<String, String>();

    private ArgumentParser _parser;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _parser = new ArgumentParser();

        _options.clear();
        _options.put(TEST_CONFIG_PROP, TEST_CONFIG_DEFAULT);
        _options.put(JNDI_CONFIG_PROP, JNDI_CONFIG_DEFAULT);
        _options.put(DISTRIBUTED_PROP, DISTRIBUTED_DEFAULT);
    }

    public void testInvalidArguments()
    {
        String[] args = new String[]{"nonExistentConfigProperty" + "=" + TEST_CONFIG_FILENAME};

        try
        {
            _parser.parseArgumentsIntoConfig(_options, args);
            fail("expected exception to be thrown due to provision of a non existent config property");
        }
        catch(IllegalArgumentException e)
        {
            //expected
        }
    }

    public void testDefaultConfigValues()
    {
        String[] args = new String[0];

        _parser.parseArgumentsIntoConfig(_options, args);

        assertEquals("unexpected config value", TEST_CONFIG_DEFAULT, _options.get(TEST_CONFIG_PROP));
        assertEquals("unexpected config value", JNDI_CONFIG_DEFAULT, _options.get(JNDI_CONFIG_PROP));
        assertEquals("unexpected config value", DISTRIBUTED_DEFAULT, _options.get(DISTRIBUTED_PROP));
    }

    public void testConfigurationParsingOverridesDefault() throws Exception
    {
        String[] args = new String[]{TEST_CONFIG_PROP + "=" + TEST_CONFIG_FILENAME,
                JNDI_CONFIG_PROP + "=" + JNDI_CONFIG_FILENAME,
                DISTRIBUTED_PROP + "=" + DISTRIBUTED_MODE};

        _parser.parseArgumentsIntoConfig(_options, args);

        assertEquals("unexpected config value", TEST_CONFIG_FILENAME, _options.get(TEST_CONFIG_PROP));
        assertEquals("unexpected config value", JNDI_CONFIG_FILENAME, _options.get(JNDI_CONFIG_PROP));
        assertEquals("unexpected config value", DISTRIBUTED_MODE, _options.get(DISTRIBUTED_PROP));
        assertFalse("override value was the same as the default", DISTRIBUTED_MODE.equalsIgnoreCase(_options.get(DISTRIBUTED_DEFAULT)));
    }
}
