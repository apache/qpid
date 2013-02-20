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

public class BrokerOptions
{
    public static final String DEFAULT_STORE_TYPE = "json";
    public static final String DEFAULT_CONFIG_FILE = "config";
    public static final String DEFAULT_LOG_CONFIG_FILE = "etc/log4j.xml";

    private String _logConfigFile;
    private Integer _logWatchFrequency = 0;

    private String _configurationStoreLocation;
    private String _configurationStoreType = DEFAULT_STORE_TYPE;

    private String _initialConfigurationStoreLocation;
    private String _initialConfigurationStoreType = DEFAULT_STORE_TYPE;

    public String getLogConfigFile()
    {
        return _logConfigFile;
    }

    public void setLogConfigFile(final String logConfigFile)
    {
        _logConfigFile = logConfigFile;
    }

    public int getLogWatchFrequency()
    {
        return _logWatchFrequency;
    }

    /**
     * Set the frequency with which the log config file will be checked for updates.
     * @param logWatchFrequency frequency in seconds
     */
    public void setLogWatchFrequency(final int logWatchFrequency)
    {
        _logWatchFrequency = logWatchFrequency;
    }

    public String getConfigurationStoreLocation()
    {
        return _configurationStoreLocation;
    }

    public void setConfigurationStoreLocation(String cofigurationStore)
    {
        _configurationStoreLocation = cofigurationStore;
    }

    public String getConfigurationStoreType()
    {
        return _configurationStoreType;
    }

    public void setConfigurationStoreType(String cofigurationStoreType)
    {
        _configurationStoreType = cofigurationStoreType;
    }

    public void setInitialConfigurationStoreLocation(String initialConfigurationStore)
    {
        _initialConfigurationStoreLocation = initialConfigurationStore;
    }

    public void setInitialConfigurationStoreType(String initialConfigurationStoreType)
    {
        _initialConfigurationStoreType = initialConfigurationStoreType;
    }

    public String getInitialConfigurationStoreLocation()
    {
        return _initialConfigurationStoreLocation;
    }

    public String getInitialConfigurationStoreType()
    {
        return _initialConfigurationStoreType;
    }

}