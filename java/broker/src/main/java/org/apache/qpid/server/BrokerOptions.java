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

import java.io.File;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.MemoryConfigurationEntryStore;

public class BrokerOptions
{
    public static final String DEFAULT_STORE_TYPE = "json";
    public static final String DEFAULT_CONFIG_NAME_PREFIX = "config";
    public static final String DEFAULT_LOG_CONFIG_FILE = "etc/log4j.xml";
    public static final String DEFAULT_INITIAL_CONFIG_LOCATION =
        BrokerOptions.class.getClassLoader().getResource("initial-store.json").toExternalForm();

    private String _logConfigFile;
    private Integer _logWatchFrequency = 0;

    private String _configurationStoreLocation;
    private String _configurationStoreType;

    private String _initialConfigurationLocation;

    private boolean _managementMode;
    private boolean _managementModeQuiesceVhosts;
    private int _managementModeRmiPort;
    private int _managementModeConnectorPort;
    private int _managementModeHttpPort;
    private String _workingDir;
    private boolean _skipLoggingConfiguration;
    private boolean _overwriteConfigurationStore;

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

    public boolean isManagementMode()
    {
        return _managementMode;
    }

    public void setManagementMode(boolean managementMode)
    {
        _managementMode = managementMode;
    }

    public boolean isManagementModeQuiesceVirtualHosts()
    {
        return _managementModeQuiesceVhosts;
    }

    public void setManagementModeQuiesceVirtualHosts(boolean managementModeQuiesceVhosts)
    {
        _managementModeQuiesceVhosts = managementModeQuiesceVhosts;
    }

    public int getManagementModeRmiPort()
    {
        return _managementModeRmiPort;
    }

    public void setManagementModeRmiPort(int managementModeRmiPort)
    {
        _managementModeRmiPort = managementModeRmiPort;
    }

    public int getManagementModeConnectorPort()
    {
        return _managementModeConnectorPort;
    }

    public void setManagementModeConnectorPort(int managementModeConnectorPort)
    {
        _managementModeConnectorPort = managementModeConnectorPort;
    }

    public int getManagementModeHttpPort()
    {
        return _managementModeHttpPort;
    }

    public void setManagementModeHttpPort(int managementModeHttpPort)
    {
        _managementModeHttpPort = managementModeHttpPort;
    }

    /**
     * Get the broker configuration store type.
     *
     * @return the previously set store type, or if none was set the default: {@value #DEFAULT_STORE_TYPE}
     */
    public String getConfigurationStoreType()
    {
        if(_configurationStoreType == null)
        {
            return  DEFAULT_STORE_TYPE;
        }

        return _configurationStoreType;
    }

    /**
     * Set the broker configuration store type.
     *
     * Passing null clears previously set values and returns to the default.
     */
    public void setConfigurationStoreType(String cofigurationStoreType)
    {
        _configurationStoreType = cofigurationStoreType;
    }

    /**
     * Get the broker configuration store location.
     *
     * Defaults to {@value #DEFAULT_CONFIG_NAME_PREFIX}.{@literal <store type>} (see {@link BrokerOptions#getConfigurationStoreType()}) within the broker work directory (see {@link BrokerOptions#getWorkDir()}).
     *
     * @return the previously set configuration store location, or the default location if none was set.
     */
    public String getConfigurationStoreLocation()
    {
        if(_configurationStoreLocation == null)
        {
            String workDir = getWorkDir();
            String storeType = getConfigurationStoreType();

            return new File(workDir, DEFAULT_CONFIG_NAME_PREFIX + "." + storeType).getAbsolutePath();
        }

        return _configurationStoreLocation;
    }

    /**
     * Set the absolute path to use for the broker configuration store.
     *
     * Passing null clears any previously set value and returns to the default.
     */
    public void setConfigurationStoreLocation(String cofigurationStore)
    {
        _configurationStoreLocation = cofigurationStore;
    }

    /**
     * Returns whether the existing broker configuration store should be overwritten with the current
     * initial configuration file (see {@link BrokerOptions#getInitialConfigurationLocation()}).
     */
    public boolean isOverwriteConfigurationStore()
    {
        return _overwriteConfigurationStore;
    }

    /**
     * Sets whether the existing broker configuration store should be overwritten with the current
     * initial configuration file (see {@link BrokerOptions#getInitialConfigurationLocation()}).
     */
    public void setOverwriteConfigurationStore(boolean overwrite)
    {
        _overwriteConfigurationStore = overwrite;
    }

    /**
     * Get the broker work directory location.
     *
     * Defaults to the location set in the "QPID_WORK" system property if it is set, or the 'work' sub-directory
     * of the user working directory ("user.dir" property) for the Java process if it is not.
     *
     * @return the previously set configuration store location, or the default location if none was set.
     */
    public String getWorkDir()
    {
        if(_workingDir == null)
        {
            String qpidWork = System.getProperty(BrokerProperties.PROPERTY_QPID_WORK);
            if (qpidWork == null)
            {
                return new File(System.getProperty("user.dir"), "work").getAbsolutePath();
            }

            return qpidWork;
        }

        return _workingDir;
    }

    /**
     * Set the absolute path to use for the broker work directory.
     *
     * Passing null clears any previously set value and returns to the default.
     */
    public void setWorkDir(String workingDir)
    {
        _workingDir = workingDir;
    }

    /**
     * Get the broker initial JSON configuration location.
     *
     * Defaults to an internal configuration file within the broker jar.
     *
     * @return the previously set configuration location, or the default location if none was set.
     */
    public String getInitialConfigurationLocation()
    {
        if(_initialConfigurationLocation == null)
        {
            return DEFAULT_INITIAL_CONFIG_LOCATION;
        }

        return _initialConfigurationLocation;
    }

    /**
     * Set the absolute path or URL to use for the initial JSON configuration, which is loaded with the
     * {@link MemoryConfigurationEntryStore} in order to initialise any new {@link ConfigurationEntryStore} for the broker.
     *
     * Passing null clears any previously set value and returns to the default.
     */
    public void setInitialConfigurationLocation(String initialConfigurationLocation)
    {
        _initialConfigurationLocation = initialConfigurationLocation;
    }

    public boolean isSkipLoggingConfiguration()
    {
        return _skipLoggingConfiguration;
    }

    public void setSkipLoggingConfiguration(boolean skipLoggingConfiguration)
    {
        _skipLoggingConfiguration = skipLoggingConfiguration;
    }
}
