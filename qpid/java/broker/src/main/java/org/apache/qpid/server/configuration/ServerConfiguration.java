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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationFactory;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.management.ConfigurationManagementMBean;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.tools.messagestore.MessageStoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ServerConfiguration implements SignalHandler
{

    private static Configuration _config;

    private static final int DEFAULT_FRAME_SIZE = 65536;
    private static final int DEFAULT_BUFFER_READ_LIMIT_SIZE = 262144;
    private static final int DEFAULT_BUFFER_WRITE_LIMIT_SIZE = 262144;
    private static final int DEFAULT_PORT = 5672;
    private static final int DEFAUL_SSL_PORT = 8672;
    private static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;
    private static final int DEFAULT_JMXPORT = 8999;

    private static int _jmxPort = DEFAULT_JMXPORT;

    private Map<String, VirtualHostConfiguration> _virtualHosts = new HashMap<String, VirtualHostConfiguration>();
    private SecurityConfiguration _securityConfiguration = null;

    private File _configFile;
    
    private Logger _log = LoggerFactory.getLogger(this.getClass());

    private ConfigurationManagementMBean _mbean;
    

    // Map of environment variables to config items
    private static final Map<String, String> envVarMap = new HashMap<String, String>();
    
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
        envVarMap.put("QPID_MINIMUMALERTREPEATGAP", "minimumAlertRepeatGap");
        envVarMap.put("QPID_SOCKETRECEIVEBUFFER", "connector.socketReceiveBuffer");
        envVarMap.put("QPID_SOCKETWRITEBUFFER", "connector.socketWriteBuffer");
        envVarMap.put("QPID_TCPNODELAY", "connector.tcpNoDelay");
        envVarMap.put("QPID_ENABLEPOOLEDALLOCATOR", "advanced.enablePooledAllocator");
    }
    
    public ServerConfiguration(File configurationURL) throws ConfigurationException
    {
        this(parseConfig(configurationURL));
        _configFile = configurationURL;
        sun.misc.Signal.handle(new sun.misc.Signal("HUP"), this);
    }

    public ServerConfiguration(Configuration conf) throws ConfigurationException
    {
        _config = conf;
        
        substituteEnvironmentVariables();
        
        _jmxPort = _config.getInt("management.jmxport", 8999);
        _securityConfiguration = new SecurityConfiguration(conf.subset("security"));

        setupVirtualHosts(conf);
        
    }
    
    private void setupVirtualHosts(Configuration conf) throws ConfigurationException
    {
        List vhosts = conf.getList("virtualhosts");
        Iterator i = vhosts.iterator();
        while (i.hasNext())
        {
            Object thing = i.next();
            if (thing instanceof String)
            {
                XMLConfiguration vhostConfiguration = new XMLConfiguration((String) thing);
                List hosts = vhostConfiguration.getList("virtualhost.name");
                for (int j = 0; j < hosts.size(); j++)
                {
                    String name = (String) hosts.get(j);
                    CompositeConfiguration mungedConf = new CompositeConfiguration();
                    mungedConf.addConfiguration(conf.subset("virtualhosts.virtualhost."+name));
                    mungedConf.addConfiguration(vhostConfiguration.subset("virtualhost." + name));
                    VirtualHostConfiguration vhostConfig = new VirtualHostConfiguration(name, mungedConf, this);
                    _virtualHosts.put(vhostConfig.getName(), vhostConfig);
                }
            }
        }
    }

    private void substituteEnvironmentVariables()
    {
        for (Entry<String, String> var : envVarMap.entrySet())
        {
            String val = System.getenv(var.getKey());
            if (val != null)
            {
                _config.setProperty(var.getValue(), val); 
            }
        }
    }

    private final static Configuration parseConfig(File file) throws ConfigurationException
    {      
        ConfigurationFactory factory = new ConfigurationFactory();
        factory.setConfigurationFileName(file.getAbsolutePath());
        Configuration conf = factory.getConfiguration();
        Iterator keys = conf.getKeys();
        if (!keys.hasNext())
        {
            keys = null;
            conf = flatConfig(file);
        }
        return conf;
    }
    
    // Our configuration class needs to make the interpolate method
    // public so it can be called below from the config method.
    private static class MyConfiguration extends CompositeConfiguration
    {
        public String interpolate(String obj)
        {
            return super.interpolate(obj);
        }
    }
    
    private final static Configuration flatConfig(File file) throws ConfigurationException
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

    @Override
    public void handle(Signal arg0)
    {
        try
        {
            reparseConfigFile();
        }
        catch (ConfigurationException e)
        {
             _log.error("Could not reload configuration file", e);
        }        
    }

    public void reparseConfigFile() throws ConfigurationException
    {
        if (_configFile != null)
        {
            Configuration newConfig = parseConfig(_configFile);
            _securityConfiguration = new SecurityConfiguration(newConfig.subset("security"));
            ApplicationRegistry.getInstance().getAccessManager().configurePlugins(_securityConfiguration);
            
            VirtualHostRegistry vhostRegistry = ApplicationRegistry.getInstance().getVirtualHostRegistry();
            for (String hostname : _virtualHosts.keySet())
            {
                VirtualHost vhost = vhostRegistry.getVirtualHost(hostname);
                SecurityConfiguration hostSecurityConfig = new SecurityConfiguration(newConfig.subset("virtualhosts.virtualhost."+hostname+".security"));
                vhost.getAccessManager().configureHostPlugins(hostSecurityConfig);
            }
        }
    }

    public String getQpidWork()
    {
        return System.getProperty("QPID_WORK", System.getProperty("java.io.tmpdir"));
    }

    public void setJMXManagementPort(int mport)
    {
        _jmxPort = mport;
    }

    public int getJMXManagementPort()
    {
        return _jmxPort;
    }

    public boolean getPlatformMbeanserver()
    {
        return _config.getBoolean("management.platform-mbeanserver", true);
    }

    public String[] getVirtualHosts()
    {
        return _virtualHosts.keySet().toArray(new String[_virtualHosts.size()]);
    }

    public String getPluginDirectory()
    {
        return _config.getString("plugin-directory");
    }

    public VirtualHostConfiguration getVirtualHostConfig(String name)
    {
        return _virtualHosts.get(name);
    }

    public List<String> getPrincipalDatabaseNames()
    {
        return _config.getList("security.principal-databases.principal-database.name");
    }

    public List<String> getPrincipalDatabaseClass()
    {
        return _config.getList("security.principal-databases.principal-database.class");
    }

    public List<String> getPrincipalDatabaseAttributeNames(int index)
    {
        String name = "security.principal-databases.principal-database(" + index + ")." + "attributes.attribute.name";
        return _config.getList(name);
    }

    public List<String> getPrincipalDatabaseAttributeValues(int index)
    {
        String name = "security.principal-databases.principal-database(" + index + ")." + "attributes.attribute.value";
        return _config.getList(name);
    }

    public List<String> getManagementPrincipalDBs()
    {
        return _config.getList("security.jmx.principal-database");
    }

    public List<String> getManagementAccessList()
    {
        return _config.getList("security.jmx.access");
    }

    public int getFrameSize()
    {
        return _config.getInt("advanced.framesize", DEFAULT_FRAME_SIZE);
    }

    public boolean getProtectIOEnabled()
    {
        return _config.getBoolean("broker.connector.protectio.enabled", false);
    }

    public int getBufferReadLimit()
    {
        return _config.getInt("broker.connector.protectio.readBufferLimitSize", DEFAULT_BUFFER_READ_LIMIT_SIZE);
    }

    public int getBufferWriteLimit()
    {
        return _config.getInt("broker.connector.protectio.writeBufferLimitSize", DEFAULT_BUFFER_WRITE_LIMIT_SIZE);
    }

    public boolean getSynchedClocks()
    {
        return _config.getBoolean("advanced.synced-clocks", false);
    }

    public boolean getMsgAuth()
    {
        return _config.getBoolean("security.msg-auth", false);
    }

    public String getJMXPrincipalDatabase()
    {
        return _config.getString("security.jmx.principal-database");
    }

    public String getManagementKeyStorePath()
    {
        return _config.getString("management.ssl.keyStorePath", null);
    }

    public boolean getManagementSSLEnabled()
    {
        return _config.getBoolean("management.ssl.enabled", true);
    }

    public String getManagementKeyStorePassword()
    {
        return _config.getString("management.ssl.keyStorePassword");
    }

    public SecurityConfiguration getSecurityConfiguration()
    {
        return _securityConfiguration;
    }

    public boolean getQueueAutoRegister()
    {
        return _config.getBoolean("queue.auto_register", true);
    }

    public boolean getManagementEnabled()
    {
        return _config.getBoolean("management.enabled", true);
    }

    public void setManagementEnabled(boolean enabled)
    {
        _config.setProperty("management.enabled", enabled);
    }


    public int getHeartBeatDelay()
    {
        return _config.getInt("heartbeat.delay", 5);
    }

    public double getHeartBeatTimeout()
    {
        return _config.getDouble("heartbeat.timeoutFactor", 2.0);
    }

    public int getDeliveryPoolSize()
    {
        return _config.getInt("delivery.poolsize", 0);
    }

    public long getMaximumMessageAge()
    {
        return _config.getLong("maximumMessageAge", 0);
    }

    public long getMaximumMessageCount()
    {
        return _config.getLong("maximumMessageCount", 0);
    }

    public long getMaximumQueueDepth()
    {
        return _config.getLong("maximumQueueDepth", 0);
    }

    public long getMaximumMessageSize()
    {
        return _config.getLong("maximumMessageSize", 0);
    }

    public long getMinimumAlertRepeatGap()
    {
        return _config.getLong("minimumAlertRepeatGap", 0);
    }

    public int getProcessors()
    {
        return _config.getInt("connector.processors", 4);
    }

    public int getPort()
    {
        return _config.getInt("connector.port", DEFAULT_PORT);
    }

    public String getBind()
    {
        return _config.getString("connector.bind", "wildcard");
    }

    public int getReceiveBufferSize()
    {
        return _config.getInt("connector.socketReceiveBuffer", 32767);
    }

    public int getWriteBufferSize()
    {
        return _config.getInt("connector.socketWriteBuffer", 32767);
    }

    public boolean getTcpNoDelay()
    {
        return _config.getBoolean("connector.tcpNoDelay", true);
    }

    public boolean getEnableExecutorPool()
    {
        return _config.getBoolean("advanced.filterchain[@enableExecutorPool]", false);
    }

    public boolean getEnablePooledAllocator()
    {
        return _config.getBoolean("advanced.enablePooledAllocator", false);
    }

    public boolean getEnableDirectBuffers()
    {
        return _config.getBoolean("advanced.enableDirectBuffers", false);
    }

    public boolean getEnableSSL()
    {
        return _config.getBoolean("connector.ssl.enabled", false);
    }

    public boolean getSSLOnly()
    {
        return _config.getBoolean("connector.ssl.sslOnly", true);
    }

    public int getSSLPort()
    {
        return _config.getInt("connector.ssl.port", DEFAUL_SSL_PORT);
    }

    public String getKeystorePath()
    {
        return _config.getString("connector.ssl.keystorePath", "none");
    }

    public String getKeystorePassword()
    {
        return _config.getString("connector.ssl.keystorePassword", "none");
    }

    public String getCertType()
    {
        return _config.getString("connector.ssl.certType", "SunX509");
    }

    public boolean getQpidNIO()
    {
        return _config.getBoolean("connector.qpidnio", false);
    }

    public boolean getUseBiasedWrites()
    {
        return _config.getBoolean("advanced.useWriteBiasedPool", false);
    }

    public String getDefaultVirtualHost()
    {
        return _config.getString("virtualhosts.default");
    }

    public void setHousekeepingExpiredMessageCheckPeriod(long value)
    {
        _config.setProperty("housekeeping.expiredMessageCheckPeriod", value);
    }

    public long getHousekeepingCheckPeriod()
    {
        return _config.getLong("housekeeping.checkPeriod", 
                   _config.getLong("housekeeping.expiredMessageCheckPeriod", 
                           DEFAULT_HOUSEKEEPING_PERIOD));
    }
}
