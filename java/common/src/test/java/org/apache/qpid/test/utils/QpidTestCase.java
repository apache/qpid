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
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestResult;

import org.apache.log4j.Logger;

public class QpidTestCase extends TestCase
{
    protected static final Logger _logger = Logger.getLogger(QpidTestCase.class);

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
}
