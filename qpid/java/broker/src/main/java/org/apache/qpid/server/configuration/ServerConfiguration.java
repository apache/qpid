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

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationFactory;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.signal.SignalHandlerTask;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.KeyManagerFactory;

public class ServerConfiguration extends ConfigurationPlugin
{
    protected static final Logger _logger = Logger.getLogger(ServerConfiguration.class);

    // Default Configuration values
    public static final int DEFAULT_BUFFER_SIZE = 262144;
    public static final String DEFAULT_STATUS_UPDATES = "on";
    public static final String SECURITY_CONFIG_RELOADED = "SECURITY CONFIGURATION RELOADED";

    public static final int DEFAULT_FRAME_SIZE = 65536;
    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_SSL_PORT = 5671;
    public static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;
    public static final int DEFAULT_JMXPORT_REGISTRYSERVER = 8999;
    public static final int JMXPORT_CONNECTORSERVER_OFFSET = 100;

    public static final String QPID_HOME = "QPID_HOME";
    public static final String QPID_WORK = "QPID_WORK";
    public static final String LIB_DIR = "lib";
    public static final String PLUGIN_DIR = "plugins";
    public static final String CACHE_DIR = "cache";

    private Map<String, VirtualHostConfiguration> _virtualHosts = new HashMap<String, VirtualHostConfiguration>();

    private File _configFile;
    private File _vhostsFile;

    // Map of environment variables to config items
    private static final Map<String, String> envVarMap = new HashMap<String, String>();

    // Configuration values to be read from the configuration file
    //todo Move all properties to static values to ensure system testing can be performed.
    public static final String MGMT_CUSTOM_REGISTRY_SOCKET = "management.custom-registry-socket";
    public static final String MGMT_JMXPORT_REGISTRYSERVER = "management.jmxport.registryServer";
    public static final String MGMT_JMXPORT_CONNECTORSERVER = "management.jmxport.connectorServer";
    public static final String STATUS_UPDATES = "status-updates";
    public static final String ADVANCED_LOCALE = "advanced.locale";
    public static final String CONNECTOR_AMQP010ENABLED = "connector.amqp010enabled";
    public static final String CONNECTOR_AMQP091ENABLED = "connector.amqp091enabled";
    public static final String CONNECTOR_AMQP09ENABLED = "connector.amqp09enabled";
    public static final String CONNECTOR_AMQP08ENABLED = "connector.amqp08enabled";
    public static final String CONNECTOR_AMQP_SUPPORTED_REPLY = "connector.amqpDefaultSupportedProtocolReply";

    {
        envVarMap.put("QPID_PORT", "connector.port");
        envVarMap.put("QPID_SSLPORT", "connector.ssl.port");
        envVarMap.put("QPID_JMXPORT_REGISTRYSERVER", MGMT_JMXPORT_REGISTRYSERVER);
        envVarMap.put("QPID_JMXPORT_CONNECTORSERVER", MGMT_JMXPORT_CONNECTORSERVER);
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
     * need to explicitly call {@link #initialise()} as this is done via the
     * {@link ApplicationRegistry#initialise()} method.
     *
     * @param configurationURL
     * @throws org.apache.commons.configuration.ConfigurationException
     */
    public ServerConfiguration(File configurationURL) throws ConfigurationException
    {
        this(parseConfig(configurationURL));
        _configFile = configurationURL;

        SignalHandlerTask hupReparseTask = new SignalHandlerTask()
        {
            public void handle()
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
        };

        if(!hupReparseTask.register("HUP"))
        {
            _logger.info("Unable to register Signal HUP handler to reload security configuration.");
            _logger.info("Signal HUP not supported for this OS / JVM combination - " + SignalHandlerTask.getPlatformDescription());
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
     * need to explicitly call {@link #initialise()} as this is done via the
     * {@link ApplicationRegistry#initialise()} method.
     *
     * @param conf
     */
    public ServerConfiguration(Configuration conf)
    {
        setConfig(conf);
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
        setConfiguration("", getConfig());
        setupVirtualHosts(getConfig());
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

        if (getListValue("security.jmx.principal-database").size() > 0)
        {
            String message = "Validation error : security/jmx/principal-database is no longer a supported element within the configuration xml."
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        if (getListValue("security.principal-databases.principal-database(0).class").size() > 0)
        {
            String message = "Validation error : security/principal-databases is no longer supported within the configuration xml." 
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        // QPID-3266.  Tidy up housekeeping configuration option for scheduling frequency
        if (contains("housekeeping.expiredMessageCheckPeriod"))
        {
            String message = "Validation error : housekeeping/expiredMessageCheckPeriod must be replaced by housekeeping/checkPeriod."
                    + (_configFile == null ? "" : " Configuration file : " + _configFile);
            throw new ConfigurationException(message);
        }

        // QPID-3517: Inconsistency in capitalisation in the SSL configuration keys used within the connector and management configuration
        // sections. For the moment, continue to understand both but generate a deprecated warning if the less preferred keystore is used.
        for (String key : new String[] {"management.ssl.keystorePath",
                "management.ssl.keystorePassword," +
                "connector.ssl.keystorePath",
                "connector.ssl.keystorePassword"})
        {
            if (contains(key))
            {
                final String deprecatedXpath = key.replaceAll("\\.", "/");
                final String preferredXpath = deprecatedXpath.replaceAll("keystore", "keyStore");
                _logger.warn("Validation warning: " + deprecatedXpath + " is deprecated and must be replaced by " + preferredXpath
                        + (_configFile == null ? "" : " Configuration file : " + _configFile));
            }
        }

        // QPID-3739 certType was a misleading name.
        if (contains("connector.ssl.certType"))
        {
            _logger.warn("Validation warning: connector/ssl/certType is deprecated and must be replaced by connector/ssl/keyManagerFactoryAlgorithm"
                    + (_configFile == null ? "" : " Configuration file : " + _configFile));
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
                getConfig().setProperty("virtualhosts.default", defaultVirtualHost);
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
        // interpolation takes place across the entirety of the
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
                vhost.getConfiguration().setConfiguration("virtualhosts.virtualhost", vhostConfig);
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

    public void setJMXPortRegistryServer(int registryServerPort)
    {
        getConfig().setProperty(MGMT_JMXPORT_REGISTRYSERVER, registryServerPort);
    }

    public int getJMXPortRegistryServer()
    {
        return getIntValue(MGMT_JMXPORT_REGISTRYSERVER, DEFAULT_JMXPORT_REGISTRYSERVER);
    }

    public void setJMXPortConnectorServer(int connectorServerPort)
    {
        getConfig().setProperty(MGMT_JMXPORT_CONNECTORSERVER, connectorServerPort);
    }

    public int getJMXConnectorServerPort()
    {
        return getIntValue(MGMT_JMXPORT_CONNECTORSERVER, getJMXPortRegistryServer() + JMXPORT_CONNECTORSERVER_OFFSET);
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

    public int getFrameSize()
    {
        return getIntValue("advanced.framesize", DEFAULT_FRAME_SIZE);
    }

    public boolean getSynchedClocks()
    {
        return getBooleanValue("advanced.synced-clocks");
    }

    public boolean getMsgAuth()
    {
        return getBooleanValue("security.msg-auth");
    }

    public String getManagementKeyStorePath()
    {
        final String fallback = getStringValue("management.ssl.keystorePath");
        return getStringValue("management.ssl.keyStorePath", fallback);
    }

    public boolean getManagementSSLEnabled()
    {
        return getBooleanValue("management.ssl.enabled", true);
    }

    public String getManagementKeyStorePassword()
    {
        final String fallback = getStringValue("management.ssl.keystorePassword");
        return getStringValue("management.ssl.keyStorePassword", fallback);
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

    public int getConnectorProcessors()
    {
        return getIntValue("connector.processors", 4);
    }

    public List getPorts()
    {
        return getListValue("connector.port", Collections.<Integer>singletonList(DEFAULT_PORT));
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
        return getStringValue("connector.bind", WILDCARD_ADDRESS);
    }

    public int getReceiveBufferSize()
    {
        return getIntValue("connector.socketReceiveBuffer", DEFAULT_BUFFER_SIZE);
    }

    public int getWriteBufferSize()
    {
        return getIntValue("connector.socketWriteBuffer", DEFAULT_BUFFER_SIZE);
    }

    public boolean getTcpNoDelay()
    {
        return getBooleanValue("connector.tcpNoDelay", true);
    }

    public boolean getEnableSSL()
    {
        return getBooleanValue("connector.ssl.enabled");
    }

    public boolean getSSLOnly()
    {
        return getBooleanValue("connector.ssl.sslOnly");
    }

    public List getSSLPorts()
    {
        return getListValue("connector.ssl.port", Collections.<Integer>singletonList(DEFAULT_SSL_PORT));
    }

    public String getConnectorKeyStorePath()
    {
        final String fallback = getStringValue("connector.ssl.keystorePath"); // pre-0.13 broker supported this name.
        return getStringValue("connector.ssl.keyStorePath", fallback);
    }

    public String getConnectorKeyStorePassword()
    {
        final String fallback = getStringValue("connector.ssl.keystorePassword"); // pre-0.13 brokers supported this name.
        return getStringValue("connector.ssl.keyStorePassword", fallback);
    }

    public String getConnectorKeyManagerFactoryAlgorithm()
    {
        final String systemFallback = KeyManagerFactory.getDefaultAlgorithm();
        // deprecated, pre-0.15 brokers supported this name.
        final String fallback = getStringValue("connector.ssl.certType", systemFallback);
        return getStringValue("connector.ssl.keyManagerFactoryAlgorithm", fallback);
    }

    public String getDefaultVirtualHost()
    {
        return getStringValue("virtualhosts.default");
    }

    public void setDefaultVirtualHost(String vhost)
    {
         getConfig().setProperty("virtualhosts.default", vhost);
    }    

    public void setHousekeepingCheckPeriod(long value)
    {
        getConfig().setProperty("housekeeping.checkPeriod", value);
    }

    public long getHousekeepingCheckPeriod()
    {
        return getLongValue("housekeeping.checkPeriod", DEFAULT_HOUSEKEEPING_PERIOD);
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

    public int getMaxChannelCount()
    {
        return getIntValue("maximumChannelCount", 256);
    }

    /**
     * List of Broker features that have been disabled within configuration.  Disabled
     * features won't be advertised to the clients on connection.
     *
     * @return list of disabled features, or empty list if no features are disabled.
     */
    public List<String> getDisabledFeatures()
    {
        final List<String> disabledFeatures = getListValue("disabledFeatures", Collections.emptyList());
        return disabledFeatures;
    }

    public boolean getManagementRightsInferAllAccess()
    {
        return getBooleanValue("management.managementRightsInferAllAccess", true);
    }

    public int getMaxDeliveryCount()
    {
        return getConfig().getInt("maximumDeliveryCount", 0);
    }

    /**
     * Check if dead letter queue delivery is enabled, defaults to disabled if not set.
     */
    public boolean isDeadLetterQueueEnabled()
    {
        return getConfig().getBoolean("deadLetterQueues", false);
    }

    /**
     * String to affix to end of queue name when generating an alternate exchange for DLQ purposes.
     */
    public String getDeadLetterExchangeSuffix()
    {
        return getConfig().getString("deadLetterExchangeSuffix", DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
    }

    /**
     * String to affix to end of queue name when generating a queue for DLQ purposes.
     */
    public String getDeadLetterQueueSuffix()
    {
        return getConfig().getString("deadLetterQueueSuffix", AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);
    }

    public boolean isAmqp010enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP010ENABLED, true);
    }

    public boolean isAmqp091enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP091ENABLED, true);
    }

    public boolean isAmqp09enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP09ENABLED, true);
    }

    public boolean isAmqp08enabled()
    {
        return getConfig().getBoolean(CONNECTOR_AMQP08ENABLED, true);
    }

    /**
     * Returns the configured default reply to an unsupported AMQP protocol initiation, or null if there is none
     */
    public AmqpProtocolVersion getDefaultSupportedProtocolReply()
    {
        String reply = getConfig().getString(CONNECTOR_AMQP_SUPPORTED_REPLY, null);

        return reply == null ? null : AmqpProtocolVersion.valueOf(reply);
    }
}
