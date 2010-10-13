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

import junit.framework.TestCase;
import junit.framework.TestResult;

import org.apache.log4j.Logger;

public class QpidTestCase extends TestCase
{
    protected static final Logger _logger = Logger.getLogger(QpidTestCase.class);

    protected Map<String, String> _propertiesSetForTestOnly = new HashMap<String, String>();
    protected Map<String, String> _propertiesSetForBroker = new HashMap<String, String>();
    
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
            String exclusionListURIs = System.getProperties().getProperty("test.excludefiles", "");
            String exclusionListString = System.getProperties().getProperty("test.excludelist", "");
            List<String> exclusionList = new ArrayList<String>();

            for (String uri : exclusionListURIs.split("\\s+"))
            {
                File file = new File(uri);
                if (file.exists())
                {
                    _logger.info("Using exclude file: " + uri);
                    try
                    {
                        BufferedReader in = new BufferedReader(new FileReader(file));
                        String excludedTest = in.readLine();
                        do
                        {
                            exclusionList.add(excludedTest);
                            excludedTest = in.readLine();
                        }
                        while (excludedTest != null);
                    }
                    catch (IOException e)
                    {
                        _logger.warn("Exception when reading exclusion list", e);
                    }
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
    
    protected static final String MS_CLASS_NAME_KEY = "messagestore.class.name";
    protected static final String MEMORY_STORE_CLASS_NAME = "org.apache.qpid.server.store.MemoryMessageStore";
    
    private static List<String> _exclusionList;
    
    public QpidTestCase()
    {
        this("QpidTestCase");
    }

    public QpidTestCase(String name)
    {
        super(name);
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

    public String getTestProfileMessageStoreClassName()
    {
        String storeClass = System.getProperty(MS_CLASS_NAME_KEY);
        
        return storeClass != null ? storeClass : MEMORY_STORE_CLASS_NAME ;
    }

    /**
     * Set a System property that is to be applied only to the external test
     * broker.
     *
     * This is a convenience method to enable the setting of a -Dproperty=value
     * entry in QPID_OPTS
     *
     * This is only useful for the External Java Broker tests.
     *
     * @param property the property name
     * @param value the value to set the property to
     */
    protected void setBrokerOnlySystemProperty(String property, String value)
    {
        if (!_propertiesSetForBroker.containsKey(property))
        {
            _propertiesSetForBroker.put(property, value);
        }

    }

    /**
     * Set a System (-D) property for this test run.
     *
     * This convenience method copies the current VMs System Property
     * for the external VM Broker.
     *
     * @param property the System property to set
     */
    protected void setSystemProperty(String property)
    {
        setSystemProperty(property, System.getProperty(property));
    }

    /**
     * Set a System property for the duration of this test.
     *
     * When the test run is complete the value will be reverted.
     *
     * The values set using this method will also be propogated to the external
     * Java Broker via a -D value defined in QPID_OPTS.
     *
     * If the value should not be set on the broker then use
     * setTestClientSystemProperty().
     *
     * @param property the property to set
     * @param value    the new value to use
     */
    protected void setSystemProperty(String property, String value)
    {
        // Record the value for the external broker
        _propertiesSetForBroker.put(property, value);

        //Set the value for the test client vm aswell.
        setTestClientSystemProperty(property, value);
    }

    /**
     * Set a System (-D) property for the external Broker of this test.
     *
     * @param property The property to set
     * @param value the value to set it to.
     */
    protected void setTestClientSystemProperty(String property, String value)
    {
        if (!_propertiesSetForTestOnly.containsKey(property))
        {
            // Record the current value so we can revert it later.
            _propertiesSetForTestOnly.put(property, System.getProperty(property));
        }

        System.setProperty(property, value);
    }

    /**
     * Restore the System property values that were set before this test run.
     */
    protected void revertSystemProperties()
    {
        for (String key : _propertiesSetForTestOnly.keySet())
        {
            String value = _propertiesSetForTestOnly.get(key);
            if (value != null)
            {
                System.setProperty(key, value);
            }
            else
            {
                System.clearProperty(key);
            }
        }

        _propertiesSetForTestOnly.clear();

        // We don't change the current VMs settings for Broker only properties
        // so we can just clear this map
        _propertiesSetForBroker.clear();
    }
    
    protected void tearDown() throws Exception
    {
        revertSystemProperties();
    }
}
