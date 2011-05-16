/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.apache.qpid.server.configuration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationFactory;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.management.ConfigurationManagementMBean;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.transport.NetworkDriverConfiguration;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ServerConfiguration extends ConfigurationPlugin implements SignalHandler
{
    protected static final Logger _logger = Logger.getLogger(ServerConfiguration.class);

    // Default Configuration values
    public static final int DEFAULT_BUFFER_READ_LIMIT_SIZE = 262144;
    public static final int DEFAULT_BUFFER_WRITE_LIMIT_SIZE = 262144;
    public static final boolean DEFAULT_BROKER_CONNECTOR_PROTECTIO_ENABLED = false;
    public static final String DEFAULT_STATUS_UPDATES = "on";
    public static final String SECURITY_CONFIG_RELOADED = "SECURITY CONFIGURATION RELOADED";

    public static final int DEFAULT_FRAME_SIZE = 65536;
    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_SSL_PORT = 8672;
    public static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;
    public static final int DEFAULT_JMXPORT = 8999;
    
    public static final String QPID_HOME = "QPID_HOME";
    public static final String QPID_WORK = "QPID_WORK";
    public static final String LIB_DIR = "lib";
    public static final String PLUGIN_DIR = "plugins";
    public static final String CACHE_DIR = "cache";

    private Map<String, VirtualHostConfiguration> _virtualHosts = new HashMap<String, VirtualHostConfiguration>();

    private File _configFile;
    private File _vhostsFile;

    private Logger _log = Logger.getLogger(this.getClass());

    private ConfigurationManagementMBean _mbean;

    // Map of environment variables to config items
    private static final Map<String, String> envVarMap = new HashMap<String, String>();

    // Configuration values to be read from the configuration file
    //todo Move all properties to static values to ensure system testing can be performed.
    public static final String CONNECTOR_PROTECTIO_ENABLED = "connector.protectio.enabled";
    public static final String CONNECTOR_PROTECTIO_READ_BUFFER_LIMIT_SIZE = "connector.protectio.readBufferLimitSize";
    public static final String CONNECTOR_PROTECTIO_WRITE_BUFFER_LIMIT_SIZE = "connector.protectio.writeBufferLimitSize";
    public static final String MGMT_CUSTOM_REGISTRY_SOCKET = "management.custom-registry-socket";
    public static final String STATUS_UPDATES = "status-updates";
    public static final String ADVANCED_LOCALE = "advanced.locale";

    {
        envVarMap.put("QPID_PORT", "connector.port");
        envVarMap.put("QPID_ENABLEDIRECTBUFFERS", "advanced.enableDirectBuffers");
        envVarMap.put("QPID_SSLPORT", "connector.ssl.port");
        envVarMap.put("QPID_NIO", "connector.qpidnio");
        envVarMap.put("QPID_WRITEBIASED", "advanced.useWriteBiasedPool");
        envVarMap.put("QPID_JMXPORT", "management.jmxport");
        envVarMap.put("QPID_FRAMESIZE", "advanced.framesize");
        envVarMap.put("QPID_MSGAUTH", "security.msg-auth");
        envVarMap.put("QPID_AUTOREGISTER", "auto_register");
        envVarMap.put("QPID_MANAGEMENTENABLED", "management.enabled");
        envVarMap.put("QPID_HEARTBEATDELAY", "heartbeat.delay");
        envVarMap.put("QPID_HEARTBEATTIMEOUTFACTOR", "heartbeat.timeoutFactor");
        envVarMap.put("QPID_MAXIMUMMESSAGEAGE", "maximumMessageAge");
        envVarMap.put("QPID_MAXIMUMMESSAGECOUNT", "maximumMessageCount");
        envVarMap.put("QPID_MAXIMUMQUEUEDEPTH", "maximumQueueDepth");
        envVarMap.put("QPID_MAXIMUMMESSAGESIZE", "maximumMessageSize");
        envVarMap.put("QPID_MAXIMUMCHANNELCOUNT", "maximumChannelCount");
        envVarMap.put("QPID_MINIMUMALERTREPEATGAP", "minimumAlertRepeatGap");
        envVarMap.put("QPID_QUEUECAPACITY", "capacity");
        envVarMap.put("QPID_FLOWRESUMECAPACITY", "flowResumeCapacity");
        envVarMap.put("QPID_SOCKETRECEIVEBUFFER", "connector.socketReceiveBuffer");
        envVarMap.put("QPID_SOCKETWRITEBUFFER", "connector.socketWriteBuffer");
        envVarMap.put("QPID_TCPNODELAY", "connector.tcpNoDelay");
        envVarMap.put("QPID_ENABLEPOOLEDALLOCATOR", "advanced.enablePooledAllocator");
        envVarMap.put("QPID_STATUS-UPDATES", "status-updates");
    }

    /**
     * Loads the given file and sets up the HUP signal handler.
     *
     * This will load the file and present the root level properties but will
     * not perform any virtualhost configuration.
     * <p>
     * To perform this {@link #initialise()} must be called.
     * <p>
     * This has been made a two step process to allow the Plugin Manager and
     * Configuration Manager to be initialised in the Application Registry.
     * <p>
     * If using this ServerConfiguration via an ApplicationRegistry there is no
     * need to explictly call {@link #initialise()} as this is done via the
     * {@link ApplicationRegistry#initialise()} method.
     *
     * @param configurationURL
     * @throws org.apache.commons.configuration.ConfigurationException
     */
    public ServerConfiguration(File configurationURL) throws ConfigurationException
    {
        this(parseConfig(configurationURL));
        _configFile = configurationURL;
        try
        {
            Signal sig = new sun.misc.Signal("HUP");
            sun.misc.Signal.handle(sig, this);
        }
        catch (Exception e)
        {
            _logger.info("Signal HUP not supported for OS: " + System.getProperty("os.name"));
            // We're on something that doesn't handle SIGHUP, how sad, Windows.
        }
    }

    /**
     * Wraps the given Commons Configuration as a ServerConfiguration.
     *
     * Mainly used during testing and in locations where configuration is not
     * desired but the interface requires configuration.
     * <p>
     * If the given configuration has VirtualHost configuration then
     * {@link #initialise()} must be called to perform the required setup.
     * <p>
     * This has been made a two step process to allow the Plugin Manager and
     * Configuration Manager to be initialised in the Application Registry.
     * <p>
     * If using this ServerConfiguration via an ApplicationRegistry there is no 
     * need to explictly call {@link #initialise()} as this is done via the
     * {@link ApplicationRegistry#initialise()} method.
     *
     * @param conf
     */
    public ServerConfiguration(Configuration conf)
    {
        _configuration = conf;        
    }

    /**
     * Processes this configuration and setups any VirtualHosts defined in the
     * configuration.
     *
     * This has been separated from the constructor to allow the PluginManager
     * time to be created and provide plugins to the ConfigurationManager for
     * processing here.
     * <p>
     * Called by {@link ApplicationRegistry#initialise()}.
     * <p>
     * NOTE: A DEFAULT ApplicationRegistry must exist when using this method
     * or a new ApplicationRegistry will be created. 
     *
     * @throws ConfigurationException
     */
    public void initialise() throws ConfigurationException
    {	
        setConfiguration("", _configuration);
        setupVirtualHosts(_configuration);
    }

    public String[] getElementsProcessed()
    {
        return new String[] { "" };
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        // Support for security.jmx.access was removed when JMX access rights were incorporated into the main ACL.
        // This ensure that users remove the element from their configuration file.
        
        if (getListValue("security.jmx.access").size() > 0)
        {
            String message = "Validation error : security/jmx/access is no longer a supported element within the configuration xml." 
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }
    }

    /*
     * Modified to enforce virtualhosts configuration in external file or main file, but not
     * both, as a fix for QPID-2360 and QPID-2361.
     */
    @SuppressWarnings("unchecked")
    protected void setupVirtualHosts(Configuration conf) throws ConfigurationException
    {
        List<String> vhostFiles = conf.getList("virtualhosts");
        Configuration vhostConfig = conf.subset("virtualhosts");

        // Only one configuration mechanism allowed
        if (!vhostFiles.isEmpty() && !vhostConfig.subset("virtualhost").isEmpty())
        {
            throw new ConfigurationException("Only one of external or embedded virtualhosts configuration allowed.");
        }

        // We can only have one vhosts XML file included
        if (vhostFiles.size() > 1)
        {
            throw new ConfigurationException("Only one external virtualhosts configuration file allowed, multiple filenames found.");
        }

        // Virtualhost configuration object
        Configuration vhostConfiguration = new HierarchicalConfiguration();

        // Load from embedded configuration if possible
        if (!vhostConfig.subset("virtualhost").isEmpty())
        {
            vhostConfiguration = vhostConfig;
        }
        else
        {
	    	// Load from the external configuration if possible
	    	for (String fileName : vhostFiles)
	        {
	            // Open the vhosts XML file and copy values from it to our config
                _vhostsFile = new File(fileName);
                if (!_vhostsFile.exists())
                {
                    throw new ConfigurationException("Virtualhosts file does not exist");
                }
	        	vhostConfiguration = parseConfig(new File(fileName));

                // save the default virtualhost name
                String defaultVirtualHost = vhostConfiguration.getString("default");
                _configuration.setProperty("virtualhosts.default", defaultVirtualHost);
            }
        }

        // Now extract the virtual host names from the configuration object
        List hosts = vhostConfiguration.getList("virtualhost.name");
        for (int j = 0; j < hosts.size(); j++)
        {
            String name = (String) hosts.get(j);

            // Add the virtual hosts to the server configuration
            VirtualHostConfiguration virtualhost = new VirtualHostConfiguration(name, vhostConfiguration.subset("virtualhost." + name));
            _virtualHosts.put(virtualhost.getName(), virtualhost);
        }
    }

    private static void substituteEnvironmentVariables(Configuration conf)
    {
        for (Entry<String, String> var : envVarMap.entrySet())
        {
            String val = System.getenv(var.getKey());
            if (val != null)
            {
                conf.setProperty(var.getValue(), val);
            }
        }
    }

    private static Configuration parseConfig(File file) throws ConfigurationException
    {
        ConfigurationFactory factory = new ConfigurationFactory();
        factory.setConfigurationFileName(file.getAbsolutePath());
        Configuration conf = factory.getConfiguration();

        Iterator<?> keys = conf.getKeys();
        if (!keys.hasNext())
        {
            keys = null;
            conf = flatConfig(file);
        }

        substituteEnvironmentVariables(conf);

        return conf;
    }

    /**
     * Check the configuration file to see if status updates are enabled.
     *
     * @return true if status updates are enabled
     */
    public boolean getStatusUpdatesEnabled()
    {
        // Retrieve the setting from configuration but default to on.
        String value = getStringValue(STATUS_UPDATES, DEFAULT_STATUS_UPDATES);

        return value.equalsIgnoreCase("on");
    }

    /**
     * The currently defined {@see Locale} for this broker
     *
     * @return the configuration defined locale
     */
    public Locale getLocale()
    {
        String localeString = getStringValue(ADVANCED_LOCALE);
        // Expecting locale of format langauge_country_variant

        // If the configuration does not have a defined locale use the JVM default
        if (localeString == null)
        {
            return Locale.getDefault();
        }

        String[] parts = localeString.split("_");

        Locale locale;
        switch (parts.length)
        {
            case 1:
                locale = new Locale(localeString);
                break;
            case 2:
                locale = new Locale(parts[0], parts[1]);
                break;
            default:
                StringBuilder variant = new StringBuilder(parts[2]);
                // If we have a variant such as the Java doc suggests for Spanish
                // Traditional_WIN we may end up with more than 3 parts on a
                // split with '_'. So we should recombine the variant.
                if (parts.length > 3)
                {
                    for (int index = 3; index < parts.length; index++)
                    {
                        variant.append('_').append(parts[index]);
                    }
                }

                locale = new Locale(parts[0], parts[1], variant.toString());
        }

        return locale;
    }

    // Our configuration class needs to make the interpolate method
    // public so it can be called below from the config method.
    public static class MyConfiguration extends CompositeConfiguration
    {
        public String interpolate(String obj)
        {
            return super.interpolate(obj);
        }
    }

    public final static Configuration flatConfig(File file) throws ConfigurationException
    {
        // We have to override the interpolate methods so that
        // interpolation takes place accross the entirety of the
        // composite configuration. Without doing this each
        // configuration object only interpolates variables defined
        // inside itself.
        final MyConfiguration conf = new MyConfiguration();
        conf.addConfiguration(new SystemConfiguration()
        {
            protected String interpolate(String o)
            {
                return conf.interpolate(o);
            }
        });
        conf.addConfiguration(new XMLConfiguration(file)
        {
            protected String interpolate(String o)
            {
                return conf.interpolate(o);
            }
        });
        return conf;
    }

    public String getConfigurationURL()
    {
        return _configFile == null ? "" : _configFile.getAbsolutePath();
    }

    public void handle(Signal arg0)
    {
        try
        {
            reparseConfigFileSecuritySections();
        }
        catch (ConfigurationException e)
        {
             _logger.error("Could not reload configuration file security sections", e);
        }
    }

    public void reparseConfigFileSecuritySections() throws ConfigurationException
    {
        if (_configFile != null)
        {
            Configuration newConfig = parseConfig(_configFile);
            setConfiguration("", newConfig);
            ApplicationRegistry.getInstance().getSecurityManager().configureHostPlugins(this);
			
            // Reload virtualhosts from correct location
            Configuration newVhosts;
            if (_vhostsFile == null)
            {
                newVhosts = newConfig.subset("virtualhosts");
            }
            else
            {
                newVhosts = parseConfig(_vhostsFile);
            }

            VirtualHostRegistry vhostRegistry = ApplicationRegistry.getInstance().getVirtualHostRegistry();
            for (String hostName : _virtualHosts.keySet())
            {
                VirtualHost vhost = vhostRegistry.getVirtualHost(hostName);
                Configuration vhostConfig = newVhosts.subset("virtualhost." + hostName);
                vhost.getConfiguration().setConfiguration("virtualhosts.virtualhost", vhostConfig); // XXX
                vhost.getSecurityManager().configureGlobalPlugins(this);
                vhost.getSecurityManager().configureHostPlugins(vhost.getConfiguration());
            }

            _logger.warn(SECURITY_CONFIG_RELOADED);
        }
    }
    
    public String getQpidWork()
    {
        return System.getProperty(QPID_WORK, System.getProperty("java.io.tmpdir"));
    }
    
    public String getQpidHome()
    {
        return System.getProperty(QPID_HOME);
    }

    public void setJMXManagementPort(int mport)
    {
        getConfig().setProperty("management.jmxport", mport);
    }

    public int getJMXManagementPort()
    {
        return getIntValue("management.jmxport", DEFAULT_JMXPORT);
    }

    public boolean getUseCustomRMISocketFactory()
    {
        return getBooleanValue(MGMT_CUSTOM_REGISTRY_SOCKET, true);
    }

    public void setUseCustomRMISocketFactory(boolean bool)
    {
        getConfig().setProperty(MGMT_CUSTOM_REGISTRY_SOCKET, bool);
    }

    public boolean getPlatformMbeanserver()
    {
        return getBooleanValue("management.platform-mbeanserver", true);
    }

    public String[] getVirtualHosts()
    {
        return _virtualHosts.keySet().toArray(new String[_virtualHosts.size()]);
    }
    
    public String getPluginDirectory()
    {
        return getStringValue("plugin-directory");
    }
    
    public String getCacheDirectory()
    {
        return getStringValue("cache-directory");
    }

    public VirtualHostConfiguration getVirtualHostConfig(String name)
    {
        return _virtualHosts.get(name);
    }

    public void setVirtualHostConfig(VirtualHostConfiguration config)
    {
        _virtualHosts.put(config.getName(), config);
    }

    public List<String> getPrincipalDatabaseNames()
    {
        return getListValue("security.principal-databases.principal-database.name");
    }

    public List<String> getPrincipalDatabaseClass()
    {
        return getListValue("security.principal-databases.principal-database.class");
    }

    public List<String> getPrincipalDatabaseAttributeNames(int index)
    {
        String name = "security.principal-databases.principal-database(" + index + ")." + "attributes.attribute.name";
        return getListValue(name);
    }

    public List<String> getPrincipalDatabaseAttributeValues(int index)
    {
        String name = "security.principal-databases.principal-database(" + index + ")." + "attributes.attribute.value";
        return getListValue(name);
    }

    public List<String> getManagementPrincipalDBs()
    {
        return getListValue("security.jmx.principal-database");
    }

    public int getFrameSize()
    {
        return getIntValue("advanced.framesize", DEFAULT_FRAME_SIZE);
    }

    public boolean getProtectIOEnabled()
    {
        return getBooleanValue(CONNECTOR_PROTECTIO_ENABLED, DEFAULT_BROKER_CONNECTOR_PROTECTIO_ENABLED);
    }

    public int getBufferReadLimit()
    {
        return getIntValue(CONNECTOR_PROTECTIO_READ_BUFFER_LIMIT_SIZE, DEFAULT_BUFFER_READ_LIMIT_SIZE);
    }

    public int getBufferWriteLimit()
    {
        return getIntValue(CONNECTOR_PROTECTIO_WRITE_BUFFER_LIMIT_SIZE, DEFAULT_BUFFER_WRITE_LIMIT_SIZE);
    }

    public boolean getSynchedClocks()
    {
        return getBooleanValue("advanced.synced-clocks");
    }

    public boolean getMsgAuth()
    {
        return getBooleanValue("security.msg-auth");
    }

    public String getJMXPrincipalDatabase()
    {
        return getStringValue("security.jmx.principal-database");
    }

    public String getManagementKeyStorePath()
    {
        return getStringValue("management.ssl.keyStorePath");
    }

    public boolean getManagementSSLEnabled()
    {
        return getBooleanValue("management.ssl.enabled", true);
    }

    public String getManagementKeyStorePassword()
    {
        return getStringValue("management.ssl.keyStorePassword");
    }

    public boolean getQueueAutoRegister()
    {
        return getBooleanValue("queue.auto_register", true);
    }

    public boolean getManagementEnabled()
    {
        return getBooleanValue("management.enabled", true);
    }

    public void setManagementEnabled(boolean enabled)
    {
        getConfig().setProperty("management.enabled", enabled);
    }

    public int getHeartBeatDelay()
    {
        return getIntValue("heartbeat.delay", 5);
    }

    public double getHeartBeatTimeout()
    {
        return getDoubleValue("heartbeat.timeoutFactor", 2.0);
    }

    public int getDeliveryPoolSize()
    {
        return getIntValue("delivery.poolsize");
    }

    public long getMaximumMessageAge()
    {
        return getLongValue("maximumMessageAge");
    }

    public long getMaximumMessageCount()
    {
        return getLongValue("maximumMessageCount");
    }

    public long getMaximumQueueDepth()
    {
        return getLongValue("maximumQueueDepth");
    }

    public long getMaximumMessageSize()
    {
        return getLongValue("maximumMessageSize");
    }

    public long getMinimumAlertRepeatGap()
    {
        return getLongValue("minimumAlertRepeatGap");
    }

    public long getCapacity()
    {
        return getLongValue("capacity");
    }

    public long getFlowResumeCapacity()
    {
        return getLongValue("flowResumeCapacity", getCapacity());
    }

    public int getProcessors()
    {
        return getIntValue("connector.processors", 4);
    }

    public List getPorts()
    {
        return getListValue("connector.port", Collections.singletonList(DEFAULT_PORT));
    }

    public List getPortExclude010()
    {
        return getListValue("connector.non010port");
    }

    public List getPortExclude091()
    {
        return getListValue("connector.non091port");
    }

    public List getPortExclude09()
    {
        return getListValue("connector.non09port");
    }

    public List getPortExclude08()
    {
        return getListValue("connector.non08port");
    }

    public String getBind()
    {
        return getStringValue("connector.bind", "wildcard");
    }

    public int getReceiveBufferSize()
    {
        return getIntValue("connector.socketReceiveBuffer", 32767);
    }

    public int getWriteBufferSize()
    {
        return getIntValue("connector.socketWriteBuffer", 32767);
    }

    public boolean getTcpNoDelay()
    {
        return getBooleanValue("connector.tcpNoDelay", true);
    }

    public boolean getEnableExecutorPool()
    {
        return getBooleanValue("advanced.filterchain[@enableExecutorPool]");
    }

    public boolean getEnableSSL()
    {
        return getBooleanValue("connector.ssl.enabled");
    }

    public boolean getSSLOnly()
    {
        return getBooleanValue("connector.ssl.sslOnly");
    }

    public int getSSLPort()
    {
        return getIntValue("connector.ssl.port", DEFAULT_SSL_PORT);
    }

    public String getKeystorePath()
    {
        return getStringValue("connector.ssl.keystorePath", "none");
    }

    public String getKeystorePassword()
    {
        return getStringValue("connector.ssl.keystorePassword", "none");
    }

    public String getCertType()
    {
        return getStringValue("connector.ssl.certType", "SunX509");
    }

    public boolean getQpidNIO()
    {
        return getBooleanValue("connector.qpidnio");
    }

    public boolean getUseBiasedWrites()
    {
        return getBooleanValue("advanced.useWriteBiasedPool");
    }

    public String getDefaultVirtualHost()
    {
        return getStringValue("virtualhosts.default");
    }

    public void setDefaultVirtualHost(String vhost)
    {
         getConfig().setProperty("virtualhosts.default", vhost);
    }    

    public void setHousekeepingExpiredMessageCheckPeriod(long value)
    {
        getConfig().setProperty("housekeeping.expiredMessageCheckPeriod", value);
    }

    public long getHousekeepingCheckPeriod()
    {
        return getLongValue("housekeeping.checkPeriod",
                                   getLongValue("housekeeping.expiredMessageCheckPeriod",
                                                       DEFAULT_HOUSEKEEPING_PERIOD));
    }

    public long getStatisticsSamplePeriod()
    {
        return getConfig().getLong("statistics.sample.period", 5000L);
    }

    public boolean isStatisticsGenerationBrokerEnabled()
    {
        return getConfig().getBoolean("statistics.generation.broker", false);
    }

    public boolean isStatisticsGenerationVirtualhostsEnabled()
    {
        return getConfig().getBoolean("statistics.generation.virtualhosts", false);
    }

    public boolean isStatisticsGenerationConnectionsEnabled()
    {
        return getConfig().getBoolean("statistics.generation.connections", false);
    }

    public long getStatisticsReportingPeriod()
    {
        return getConfig().getLong("statistics.reporting.period", 0L);
    }

    public boolean isStatisticsReportResetEnabled()
    {
        return getConfig().getBoolean("statistics.reporting.reset", false);
    }

    public NetworkDriverConfiguration getNetworkConfiguration()
    {
        return new NetworkDriverConfiguration()
        {

            public Integer getTrafficClass()
            {
                return null;
            }

            public Boolean getTcpNoDelay()
            {
                // Can't call parent getTcpNoDelay since it just calls this one
                return getBooleanValue("connector.tcpNoDelay", true);
            }

            public Integer getSoTimeout()
            {
                return null;
            }

            public Integer getSoLinger()
            {
                return null;
            }

            public Integer getSendBufferSize()
            {
                return getBufferWriteLimit();
            }

            public Boolean getReuseAddress()
            {
                return null;
            }

            public Integer getReceiveBufferSize()
            {
                return getBufferReadLimit();
            }

            public Boolean getOOBInline()
            {
                return null;
            }

            public Boolean getKeepAlive()
            {
                return null;
            }
        };
    }

    public int getMaxChannelCount()
    {
        return getIntValue("maximumChannelCount", 256);
    }
}
