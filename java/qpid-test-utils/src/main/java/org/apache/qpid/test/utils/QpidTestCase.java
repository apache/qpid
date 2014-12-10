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
package org.apache.qpid.test.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import junit.framework.TestCase;
import junit.framework.TestResult;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class QpidTestCase extends TestCase
{
    private static final String TEST_EXCLUDES = "test.excludes";
    private static final String TEST_EXCLUDELIST = "test.excludelist";
    private static final String TEST_EXCLUDEFILES = "test.excludefiles";
    private static final String VIRTUAL_HOST_NODE_TYPE = "virtualhostnode.type";
    private static final String VIRTUAL_HOST_NODE_CONTEXT_BLUEPRINT = "virtualhostnode.context.blueprint";
    public static final String QPID_HOME = System.getProperty("QPID_HOME");
    public static final String TEST_RESOURCES_DIR = QPID_HOME + "/../test-profiles/test_resources/";
    public static final String TEST_PROFILES_DIR = QPID_HOME + "/../test-profiles/";
    public static final String TMP_FOLDER = System.getProperty("java.io.tmpdir");
    public static final String SPAWNED_BROKER_LOG4J_CONFIG_FILE_PATH = System.getProperty("spawnedbroker.log4j.configuration.file");

    private static final Logger _logger = Logger.getLogger(QpidTestCase.class);

    private final Map<Logger, Level> _loggerLevelSetForTest = new HashMap<Logger, Level>();
    private final Map<String, String> _propertiesSetForTest = new HashMap<String, String>();

    private String _testName;

    /**
     * Some tests are excluded when the property test.excludes is set to true.
     * An exclusion list is either a file (prop test.excludesfile) which contains one test name
     * to be excluded per line or a String (prop test.excludeslist) where tests to be excluded are
     * separated by " ". Excluded tests are specified following the format:
     * className#testName where className is the class of the test to be
     * excluded and testName is the name of the test to be excluded.
     * className#* excludes all the tests of the specified class.
     */
    static
    {
        if (Boolean.getBoolean("test.exclude"))
        {
            _logger.info("Some tests should be excluded, building the exclude list");
            String exclusionListURIs = System.getProperty(TEST_EXCLUDEFILES, "");
            String exclusionListString = System.getProperty(TEST_EXCLUDELIST, "");
            String testExcludes = System.getProperty(TEST_EXCLUDES);

            //For the maven build, process the test.excludes property
            if(testExcludes != null && "".equals(exclusionListURIs))
            {
                for (String exclude : testExcludes.split("\\s+"))
                {
                    exclusionListURIs += TEST_PROFILES_DIR + "/" + exclude + ";";
                }
            }

            List<String> exclusionList = new ArrayList<String>();

            for (String uri : exclusionListURIs.split(";\\s*"))
            {
                File file = new File(uri);
                if (file.exists())
                {
                    _logger.info("Using exclude file: " + uri);
                    try(FileReader fileReader = new FileReader(file))
                    {
                        try(BufferedReader in = new BufferedReader(fileReader))
                        {
                            String excludedTest = in.readLine();
                            do
                            {
                                exclusionList.add(excludedTest);
                                excludedTest = in.readLine();
                            }
                            while (excludedTest != null);
                        }
                    }
                    catch (IOException e)
                    {
                        _logger.warn("Exception when reading exclusion list", e);
                    }
                }
                else
                {
                    _logger.info("Specified exclude file does not exist: " + uri);
                }
            }

            if (!exclusionListString.equals(""))
            {
                _logger.info("Using excludeslist: " + exclusionListString);
                for (String test : exclusionListString.split("\\s+"))
                {
                    exclusionList.add(test);
                }
            }

            _exclusionList = exclusionList;
        }
    }

    private static List<String> _exclusionList;

    public QpidTestCase()
    {
        super();
    }

    public void run(TestResult testResult)
    {
        if (_exclusionList != null && (_exclusionList.contains(getClass().getPackage().getName() + ".*") ||
                                       _exclusionList.contains(getClass().getName() + "#*") ||
                                       _exclusionList.contains(getClass().getName() + "#" + getName())))
        {
            _logger.info("Test: " + getName() + " is excluded");
            testResult.endTest(this);
        }
        else
        {
            super.run(testResult);
        }
    }

    public String getTestProfileVirtualHostNodeType()
    {
        final String storeType = System.getProperty(VIRTUAL_HOST_NODE_TYPE);

        if (_logger.isDebugEnabled())
        {
            _logger.debug(VIRTUAL_HOST_NODE_TYPE + "=" + storeType);
        }

        return storeType != null ? storeType : "TestMemory";
    }

    public String getTestProfileVirtualHostNodeBlueprint()
    {
        return System.getProperty(VIRTUAL_HOST_NODE_CONTEXT_BLUEPRINT);
    }


    /**
     * Gets the next available port starting at a port.
     *
     * @param fromPort the port to scan for availability
     * @throws NoSuchElementException if there are no ports available
     */
    public int getNextAvailable(int fromPort)
    {
        return new PortHelper().getNextAvailable(fromPort);
    }

    public int findFreePort()
    {
        return new PortHelper().getNextAvailable();
    }

    /**
     * Set a System property for duration of this test only. The tearDown will
     * guarantee to reset the property to its previous value after the test
     * completes.
     *
     * @param property The property to set
     * @param value the value to set it to, if null, the property will be cleared
     */
    protected void setTestSystemProperty(final String property, final String value)
    {
        if (!_propertiesSetForTest.containsKey(property))
        {
            // Record the current value so we can revert it later.
            _propertiesSetForTest.put(property, System.getProperty(property));
        }

        if (value == null)
        {
            System.clearProperty(property);
        }
        else
        {
            System.setProperty(property, value);
        }

        _logger.info("Set system property \"" + property + "\" to: \"" + value + "\"");
    }

    /**
     * Restore the System property values that were set by this test run.
     */
    protected void revertTestSystemProperties()
    {
        if(!_propertiesSetForTest.isEmpty())
        {
            _logger.debug("reverting " + _propertiesSetForTest.size() + " test properties");
            for (String key : _propertiesSetForTest.keySet())
            {
                String value = _propertiesSetForTest.get(key);
                if (value != null)
                {
                    System.setProperty(key, value);
                }
                else
                {
                    System.clearProperty(key);
                }
            }

            _propertiesSetForTest.clear();
        }
    }

    /**
     * Adjust the VMs Log4j Settings just for this test run
     *
     * @param logger the logger to change
     * @param level the level to set
     */
    protected void setLoggerLevel(Logger logger, Level level)
    {
        assertNotNull("Cannot set level of null logger", logger);
        assertNotNull("Cannot set Logger("+logger.getName()+") to null level.",level);

        if (!_loggerLevelSetForTest.containsKey(logger))
        {
            // Record the current value so we can revert it later.
            _loggerLevelSetForTest.put(logger, logger.getLevel());
        }

        logger.setLevel(level);
    }

    /**
     * Restore the logging levels defined by this test.
     */
    protected void revertLoggingLevels()
    {
        for (Logger logger : _loggerLevelSetForTest.keySet())
        {
            logger.setLevel(_loggerLevelSetForTest.get(logger));
        }

        _loggerLevelSetForTest.clear();
    }

    protected void tearDown() throws java.lang.Exception
    {
        _logger.info("========== tearDown " + _testName + " ==========");
        revertTestSystemProperties();
        revertLoggingLevels();
    }

    protected void setUp() throws Exception
    {
        _testName = getClass().getSimpleName() + "." + getName();
        _logger.info("========== start " + _testName + " ==========");
    }

    protected String getTestName()
    {
        return _testName;
    }
}
