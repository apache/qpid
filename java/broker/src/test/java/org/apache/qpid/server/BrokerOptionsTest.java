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
package org.apache.qpid.server;

import org.apache.qpid.test.utils.QpidTestCase;

public class BrokerOptionsTest extends QpidTestCase
{
    private BrokerOptions _options;

    protected void setUp()
    {
        _options = new BrokerOptions();
    }

    public void testDefaultConfigurationStoreType()
    {
        assertEquals("json", _options.getConfigurationStoreType());
    }

    public void testOverriddenConfigurationStoreType()
    {
        _options.setConfigurationStoreType("dby");
        assertEquals("dby", _options.getConfigurationStoreType());
    }

    public void testDefaultConfigurationStoreLocation()
    {
        assertNull(_options.getConfigurationStoreLocation());
    }

    public void testOverriddenConfigurationStoreLocation()
    {
        final String testConfigFile = "etc/mytestconfig.xml";
        _options.setConfigurationStoreLocation(testConfigFile);
        assertEquals(testConfigFile, _options.getConfigurationStoreLocation());
    }

    public void testDefaultLogConfigFile()
    {
        assertNull(_options.getLogConfigFile());
    }

    public void testOverriddenLogConfigFile()
    {
        final String testLogConfigFile = "etc/mytestlog4j.xml";
        _options.setLogConfigFile(testLogConfigFile);
        assertEquals(testLogConfigFile, _options.getLogConfigFile());
    }

    public void testDefaultLogWatchFrequency()
    {
        assertEquals(0L, _options.getLogWatchFrequency());
    }

    public void testOverridenLogWatchFrequency()
    {
        final int myFreq = 10 * 1000;
        
        _options.setLogWatchFrequency(myFreq);
        assertEquals(myFreq, _options.getLogWatchFrequency());
    }


    public void testDefaultInitialConfigurationStoreType()
    {
        assertEquals("json", _options.getInitialConfigurationStoreType());
    }

    public void testOverriddenInitialConfigurationStoreType()
    {
        _options.setInitialConfigurationStoreType("dby");
        assertEquals("dby", _options.getInitialConfigurationStoreType());
    }

    public void testDefaultInitialConfigurationStoreLocation()
    {
        assertNull(_options.getInitialConfigurationStoreLocation());
    }

    public void testOverriddenInitialConfigurationStoreLocation()
    {
        final String testConfigFile = "etc/mytestconfig.xml";
        _options.setInitialConfigurationStoreLocation(testConfigFile);
        assertEquals(testConfigFile, _options.getInitialConfigurationStoreLocation());
    }
}
