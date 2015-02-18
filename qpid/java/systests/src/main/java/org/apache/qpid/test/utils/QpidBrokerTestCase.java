/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.test.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.FileAppender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.Broker;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.MemoryConfigurationStore;
import org.apache.qpid.server.virtualhostnode.AbstractVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.JsonVirtualHostNode;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.SystemUtils;

/**
 * Qpid base class for system testing test cases.
 */
public class QpidBrokerTestCase extends QpidTestCase
{
    private TaskExecutor _taskExecutor;

    public static final String GUEST_USERNAME = "guest";
    public static final String GUEST_PASSWORD = "guest";

    private final File _configFile = new File(System.getProperty("broker.config"));
    private File _spawnedBrokerLogConfigFile;
    protected final String _brokerStoreType = System.getProperty("broker.config-store-type", "JSON");
    protected static final Logger _logger = Logger.getLogger(QpidBrokerTestCase.class);
    protected static final int LOGMONITOR_TIMEOUT = 5000;

    protected long RECEIVE_TIMEOUT = 1000l;

    private Map<String, String> _propertiesSetForBroker = new HashMap<String, String>();

    private Map<Integer, TestBrokerConfiguration> _brokerConfigurations;

    protected static final String INDEX = "index";
    protected static final String CONTENT = "content";

    private static final String DEFAULT_INITIAL_CONTEXT = "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";

    static
    {
        String initialContext = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);

        if (initialContext == null || initialContext.length() == 0)
        {
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, DEFAULT_INITIAL_CONTEXT);
        }
    }

    // system properties
    private static final String BROKER_LANGUAGE = "broker.language";
    protected static final String BROKER_TYPE = "broker.type";
    private static final String BROKER_COMMAND = "broker.command";
    private static final String BROKER_COMMAND_PLATFORM = "broker.command." + SystemUtils.getOSConfigSuffix();
    private static final String BROKER_CLEAN_BETWEEN_TESTS = "broker.clean.between.tests";
    private static final String BROKER_VERSION = "broker.version";
    private static final String TEST_OUTPUT = "test.output";
    private static final String BROKER_PERSITENT = "broker.persistent";
    public static final String PROFILE_USE_SSL = "profile.use_ssl";

    public static final int DEFAULT_PORT_VALUE = 5672;
    public static final int DEFAULT_SSL_PORT_VALUE = 5671;
    public static final int DEFAULT_JMXPORT_REGISTRYSERVER = 8999;
    public static final int JMXPORT_CONNECTORSERVER_OFFSET = 100;
    public static final int DEFAULT_HTTP_MANAGEMENT_PORT_VALUE = 8080;

    public static final String TEST_AMQP_PORT_PROTOCOLS_PROPERTY="test.amqp_port_protocols";

    // values
    protected static final String JAVA = "java";

    public static final int DEFAULT_PORT = Integer.getInteger("test.port", DEFAULT_PORT_VALUE);
    public static final int FAILING_PORT = Integer.parseInt(System.getProperty("test.port.alt"));
    public static final int DEFAULT_MANAGEMENT_PORT = Integer.getInteger("test.mport", DEFAULT_JMXPORT_REGISTRYSERVER);
    public static final int DEFAULT_HTTP_MANAGEMENT_PORT = Integer.getInteger("test.hport", DEFAULT_HTTP_MANAGEMENT_PORT_VALUE);
    public static final int DEFAULT_SSL_PORT = Integer.getInteger("test.port.ssl", DEFAULT_SSL_PORT_VALUE);

    protected String _brokerLanguage = System.getProperty(BROKER_LANGUAGE, JAVA);
    protected BrokerHolder.BrokerType _brokerType = BrokerHolder.BrokerType.valueOf(System.getProperty(BROKER_TYPE, "").toUpperCase());

    private static final String BROKER_COMMAND_TEMPLATE = System.getProperty(BROKER_COMMAND_PLATFORM, System.getProperty(BROKER_COMMAND));

    private Boolean _brokerCleanBetweenTests = Boolean.getBoolean(BROKER_CLEAN_BETWEEN_TESTS);
    private final Protocol _brokerProtocol = Protocol.valueOf("AMQP_" + System.getProperty(BROKER_VERSION, " ").substring(1));
    protected String _output = System.getProperty(TEST_OUTPUT, System.getProperty("java.io.tmpdir"));
    protected Boolean _brokerPersistent = Boolean.getBoolean(BROKER_PERSITENT);

    protected File _outputFile;

    protected Map<Integer, BrokerHolder> _brokers = new HashMap<Integer, BrokerHolder>();

    protected InitialContext _initialContext;
    protected AMQConnectionFactory _connectionFactory;

    // the connections created for a given test
    protected List<Connection> _connections = new ArrayList<Connection>();
    public static final String QUEUE = "queue";
    public static final String TOPIC = "topic";
    public static final String MANAGEMENT_MODE_PASSWORD = "mm_password";

    /** Map to hold test defined environment properties */
    private Map<String, String> _env;

    /** Ensure our messages have some sort of size */
    protected static final int DEFAULT_MESSAGE_SIZE = 1024;

    /** Size to create our message*/
    private int _messageSize = DEFAULT_MESSAGE_SIZE;
    private String _brokerCommandTemplate;

    /** Type of message*/
    protected enum MessageType
    {
        BYTES,
        MAP,
        OBJECT,
        STREAM,
        TEXT
    }
    private MessageType _messageType  = MessageType.TEXT;

    public QpidBrokerTestCase()
    {
        super();
        _brokerConfigurations = new HashMap<Integer, TestBrokerConfiguration>();
        initialiseSpawnedBrokerLogConfigFile();
        _brokerCommandTemplate = BROKER_COMMAND_TEMPLATE;


        if (JAVA.equals(_brokerLanguage))
        {
            try
            {
                Broker.populateSystemPropertiesFromDefaults(null);
            }
            catch (IOException ioe)
            {
                throw new RuntimeException("Failed to load Java broker system properties", ioe);
            }
        }
    }

    public TestBrokerConfiguration getBrokerConfiguration(int port)
    {
        int actualPort = getPort(port);

        synchronized (_brokerConfigurations)
        {
            TestBrokerConfiguration configuration = _brokerConfigurations.get(actualPort);
            if (configuration == null)
            {
                configuration = createBrokerConfiguration(actualPort);
            }
            return configuration;
        }
    }

    public TestBrokerConfiguration getBrokerConfiguration()
    {
        return getBrokerConfiguration(DEFAULT_PORT);
    }

    public TestBrokerConfiguration createBrokerConfiguration(int port)
    {
        int actualPort = getPort(port);
        if(_taskExecutor == null)
        {
            _taskExecutor = new TaskExecutorImpl();
            _taskExecutor.start();
        }
        TestBrokerConfiguration  configuration = new TestBrokerConfiguration(_brokerStoreType, _configFile.getAbsolutePath(), _taskExecutor);
        synchronized (_brokerConfigurations)
        {
            _brokerConfigurations.put(actualPort, configuration);
        }
        if (actualPort != DEFAULT_PORT)
        {
            configuration.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT, Port.PORT, actualPort);
            configuration.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_RMI_PORT, Port.PORT, getManagementPort(actualPort));
            configuration.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_JMX_PORT, Port.PORT, getManagementPort(actualPort) + JMXPORT_CONNECTORSERVER_OFFSET);

            String workDir = System.getProperty("QPID_WORK") + File.separator + TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST + File.separator + actualPort;
            configuration.setObjectAttribute(VirtualHostNode.class, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST, JsonVirtualHostNode.STORE_PATH, workDir);
        }

        return configuration;
    }

    private void initialiseSpawnedBrokerLogConfigFile()
    {
        _spawnedBrokerLogConfigFile = new File(SPAWNED_BROKER_LOG4J_CONFIG_FILE_PATH);
        if(!_spawnedBrokerLogConfigFile.exists())
        {
            throw new RuntimeException("Log config file " + _spawnedBrokerLogConfigFile.getAbsolutePath() + " does not exist");
        }
    }

    public Logger getLogger()
    {
        return QpidBrokerTestCase._logger;
    }

    @Override
    public void runBare() throws Throwable
    {
        // Initialize this for each test run
        _env = new HashMap<String, String>();

        // Log4j properties expects this to be set
        System.setProperty("qpid.testMethod", "-" + getName());
        System.setProperty("qpid.testClass", getClass().getName());

        String log4jConfigFile = System.getProperty("log4j.configuration.file");
        DOMConfigurator.configure(log4jConfigFile);

        // get log file from file appender
        _outputFile = new File(((FileAppender)LogManager.getRootLogger().getAllAppenders().nextElement()).getFile());

        try
        {
            super.runBare();
        }
        catch (Exception e)
        {
            _logger.error("exception", e);
            throw e;
        }
        finally
        {
            stopAllBrokers();

            // reset properties used in the test
            revertSystemProperties();
            revertLoggingLevels();

            if (_brokerCleanBetweenTests)
            {
                final String qpidWork = System.getProperty("QPID_WORK");
                cleanBrokerWork(qpidWork);
                createBrokerWork(qpidWork);
            }

            _logger.info("==========  stop " + getTestName() + " ==========");

            LogManager.resetConfiguration();
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = new TaskExecutorImpl();
        _taskExecutor.start();
        if (!_configFile.exists())
        {
            fail("Unable to test without config file:" + _configFile);
        }

        startBroker();
    }

    /**
     * Return the management port in use by the broker on this main port
     *
     * @param mainPort the broker's main port.
     *
     * @return the management port that corresponds to the broker on the given port
     */
    protected int getManagementPort(int mainPort)
    {
        return mainPort + (DEFAULT_MANAGEMENT_PORT - DEFAULT_PORT);
    }

    /**
     * The returned set of port numbers is only a guess because it assumes no ports have been overridden
     * using system properties.
     */
    protected Set<Integer> guessAllPortsUsedByBroker(int mainPort)
    {
        Set<Integer> ports = new HashSet<Integer>();
        int managementPort = getManagementPort(mainPort);
        int connectorServerPort = managementPort + JMXPORT_CONNECTORSERVER_OFFSET;

        ports.add(mainPort);
        ports.add(managementPort);
        ports.add(connectorServerPort);
        ports.add(DEFAULT_SSL_PORT);

        return ports;
    }

    /**
     * Get the Port that is use by the current broker
     *
     * @return the current port
     */
    protected int getPort()
    {
        return getPort(0);
    }

    protected int getPort(int port)
    {
        if (!_brokerType.equals(BrokerHolder.BrokerType.EXTERNAL))
        {
            return port == 0 ? DEFAULT_PORT : port;
        }
        else
        {
            return port;
        }
    }

    public void startBroker() throws Exception
    {
        startBroker(0);
    }

    public void startBroker(int port) throws Exception
    {
        startBroker(port, false);
    }

    public void startBroker(int port, boolean managementMode) throws Exception
    {
        startBroker(port, managementMode, null);
    }

    public void startBroker(int port, boolean managementMode, String log4jFile) throws Exception
    {
        int actualPort = getPort(port);
        TestBrokerConfiguration configuration = getBrokerConfiguration(actualPort);
        startBroker(actualPort, configuration, managementMode, log4jFile);
    }

    protected File getBrokerCommandLog4JFile()
    {
        return _spawnedBrokerLogConfigFile;
    }

    protected void setBrokerCommandLog4JFile(File file)
    {
        _spawnedBrokerLogConfigFile = file;
        _logger.info("Modified log config file to: " + file);
    }

    public void startBroker(int port, TestBrokerConfiguration testConfiguration, String log4jFile) throws Exception
    {
        startBroker(port, testConfiguration, false, log4jFile);
    }

    protected void startBroker(int port, TestBrokerConfiguration testConfiguration, boolean managementMode, String log4jFile) throws Exception
    {
        port = getPort(port);

        if(_brokers.get(port) != null)
        {
            throw new IllegalStateException("There is already an existing broker running on port " + port);
        }

        String testConfig = saveTestConfiguration(port, testConfiguration);
        String log4jConfig = log4jFile == null ? getBrokerCommandLog4JFile().getAbsolutePath() : log4jFile;
        BrokerOptions options = getBrokerOptions(managementMode, testConfig, log4jConfig, log4jFile == null);
        BrokerHolder holder = new BrokerHolderFactory().create(_brokerType, port, this);
        _brokers.put(port, holder);
        holder.start(options);
    }

    private boolean existingInternalBroker()
    {
        for(BrokerHolder holder : _brokers.values())
        {
            if(holder instanceof InternalBrokerHolder)
            {
                return true;
            }
        }

        return false;
    }

    public String getTestConfigFile(int port)
    {
        return _output + File.separator + getTestQueueName() + "-" + port + "-config";
    }

    protected String getPathRelativeToWorkingDirectory(String file)
    {
        File configLocation = new File(file);
        File workingDirectory = new File(System.getProperty("user.dir"));

        _logger.debug("Converting path to be relative to working directory: " + file);

        try
        {
            String configPath = configLocation.getAbsolutePath();
            String workingDirectoryPath = workingDirectory.getCanonicalPath();
            if (SystemUtils.isWindows())
            {
                configPath = configPath.toLowerCase();
                workingDirectoryPath = workingDirectoryPath.toLowerCase();
            }
            if(!configPath.startsWith(workingDirectoryPath))
            {
                throw new RuntimeException("Provided path is not a child of the working directory: " + workingDirectoryPath);
            }

            String substring = configPath.replace(workingDirectoryPath, "").substring(1);
            _logger.debug("Converted relative path: " + substring);

            return substring;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Problem while converting to relative path", e);
        }
    }

    protected String saveTestConfiguration(int port, TestBrokerConfiguration testConfiguration)
    {
        String testConfig = getTestConfigFile(port);
        String relative = getPathRelativeToWorkingDirectory(testConfig);
        if (testConfiguration != null && !testConfiguration.isSaved())
        {
            _logger.info("Saving test broker configuration at: " + testConfig);
            testConfiguration.save(new File(testConfig));
            testConfiguration.setSaved(true);
        }
        return relative;
    }

    protected void cleanBrokerWork(final String qpidWork)
    {
        if (qpidWork != null)
        {
            _logger.info("Cleaning broker work dir: " + qpidWork);

            File file = new File(qpidWork);
            if (file.exists())
            {
                final boolean success = FileUtils.delete(file, true);
                if(!success)
                {
                    throw new RuntimeException("Failed to recursively delete beneath : " + file);
                }
            }
        }
    }

    protected void createBrokerWork(final String qpidWork)
    {
        if (qpidWork != null)
        {
            final File dir = new File(qpidWork);
            dir.mkdirs();
            if (!dir.isDirectory())
            {
                throw new RuntimeException("Failed to created Qpid work directory : " + qpidWork);
            }
        }
    }

    public void stopBroker()
    {
        stopBroker(0);
    }

    public void stopAllBrokers()
    {
        boolean exceptionOccured = false;
        Set<Integer> runningBrokerPorts = new HashSet<Integer>(getBrokerPortNumbers());
        for (int brokerPortNumber : runningBrokerPorts)
        {
            if (!stopBrokerSafely(brokerPortNumber))
            {
                exceptionOccured = true;
            }
        }
        if (exceptionOccured)
        {
            throw new RuntimeException("Exception occurred on stopping of test broker. Please, examine logs for details");
        }
    }

    protected boolean stopBrokerSafely(int brokerPortNumber)
    {
        boolean success = true;
        BrokerHolder broker = _brokers.get(brokerPortNumber);
        try
        {
            stopBroker(brokerPortNumber);
        }
        catch(Exception e)
        {
            success = false;
            _logger.error("Failed to stop broker " + broker + " at port " + brokerPortNumber, e);
            if (broker != null)
            {
                // save the thread dump in case of dead locks
                try
                {
                    _logger.error("Broker " + broker + " thread dump:" + broker.dumpThreads());
                }
                finally
                {
                    // try to kill broker
                    try
                    {
                        broker.kill();
                    }
                    catch(Exception killException)
                    {
                        // ignore
                    }
                }
            }
        }
        return success;
    }

    public void stopBroker(int port)
    {
        if (isBrokerPresent(port))
        {
            port = getPort(port);

            _logger.info("stopping broker on port : " + port);
            BrokerHolder broker = _brokers.remove(port);
            broker.shutdown();
        }
    }

    public void killBroker()
    {
        killBroker(0);
    }

    public void killBroker(int port)
    {
        if (isBrokerPresent(port))
        {
            port = getPort(port);

            _logger.info("killing broker on port : " + port);
            BrokerHolder broker = _brokers.remove(port);
            broker.kill();
        }
    }

    public boolean isBrokerPresent(int port)
    {
        port = getPort(port);

        return _brokers.containsKey(port);
    }

    public BrokerHolder getBroker(int port) throws Exception
    {
        port = getPort(port);
        return _brokers.get(port);
    }

    public Set<Integer> getBrokerPortNumbers()
    {
        return new HashSet<Integer>(_brokers.keySet());
    }

    /**
     * Creates a new virtual host node in broker configuration for given broker port
     * @param brokerPort broker port
     * @param virtualHostNodeName virtual host node name
     */
    protected void createTestVirtualHostNode(int brokerPort, String virtualHostNodeName, boolean withBlueprint)
    {
        String storeType = getTestProfileVirtualHostNodeType();
        String storeDir = null;

        if (System.getProperty("profile", "").startsWith("java-dby-mem"))
        {
            storeDir = ":memory:";
        }
        else if (!MemoryConfigurationStore.TYPE.equals(storeType))
        {
            storeDir = "${QPID_WORK}" + File.separator + virtualHostNodeName + File.separator + brokerPort;
        }

        // add new virtual host node with vhost blueprint configuration to the broker store
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHostNode.NAME, virtualHostNodeName);
        attributes.put(VirtualHostNode.TYPE, storeType);
        if (storeDir != null)
        {
            attributes.put(JsonVirtualHostNode.STORE_PATH, storeDir);
        }

        if (withBlueprint)
        {
            final String blueprint = getTestProfileVirtualHostNodeBlueprint();

            attributes.put(ConfiguredObject.CONTEXT,
                           Collections.singletonMap(AbstractVirtualHostNode.VIRTUALHOST_BLUEPRINT_CONTEXT_VAR,
                                                    blueprint));
        }

        int port = getPort(brokerPort);
        getBrokerConfiguration(port).addObjectConfiguration(VirtualHostNode.class, attributes);
    }

    protected void createTestVirtualHostNode(int brokerPort, String virtualHostNodeName)
    {
        createTestVirtualHostNode(brokerPort, virtualHostNodeName, true);
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
        synchronized (_propertiesSetForBroker)
        {
            if (!_propertiesSetForBroker.containsKey(property))
            {
                _propertiesSetForBroker.put(property, value);
            }
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
        String value = System.getProperty(property);
        if (value != null)
        {
            setSystemProperty(property, value);
        }
    }

    /**
     * Set a System property for the duration of this test.
     *
     * When the test run is complete the value will be reverted.
     *
     * The values set using this method will also be propagated to the external
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
        synchronized(_propertiesSetForBroker)
        {
            // Record the value for the external broker
            if (value == null)
            {
                _propertiesSetForBroker.remove(property);
            }
            else
            {
                _propertiesSetForBroker.put(property, value);
            }
        }
        //Set the value for the test client vm aswell.
        setTestClientSystemProperty(property, value);
    }

    /**
     * Set a System  property for the client (and broker if using the same vm) of this test.
     *
     * @param property The property to set
     * @param value the value to set it to.
     */
    protected void setTestClientSystemProperty(String property, String value)
    {
        setTestSystemProperty(property, value);
    }

    /**
     * Restore the System property values that were set before this test run.
     */
    protected void revertSystemProperties()
    {
        revertTestSystemProperties();

        // We don't change the current VMs settings for Broker only properties
        // so we can just clear this map
        _propertiesSetForBroker.clear();
    }

    /**
     * Add an environment variable for the external broker environment
     *
     * @param property the property to set
     * @param value    the value to set it to
     */
    protected void setBrokerEnvironment(String property, String value)
    {
        _env.put(property, value);
    }

    /**
     * Check whether the broker is an 0.8
     *
     * @return true if the broker is an 0_8 version, false otherwise.
     */
    public boolean isBroker08()
    {
        return _brokerProtocol.equals(Protocol.AMQP_0_8);
    }

    public boolean isBroker010()
    {
        return _brokerProtocol.equals(Protocol.AMQP_0_10);
    }

    public Protocol getBrokerProtocol()
    {
        return _brokerProtocol;
    }

    protected boolean isJavaBroker()
    {
        return _brokerLanguage.equals("java");
    }

    protected boolean isCppBroker()
    {
        return _brokerLanguage.equals("cpp");
    }

    protected boolean isExternalBroker()
    {
        return !isInternalBroker();
    }

    protected boolean isInternalBroker()
    {
        return _brokerType.equals(BrokerHolder.BrokerType.INTERNAL);
    }

    protected boolean isBrokerStorePersistent()
    {
        return _brokerPersistent;
    }

    public void restartBroker() throws Exception
    {
        restartBroker(0);
    }

    public void restartBroker(int port) throws Exception
    {
        stopBroker(port);
        startBroker(port);
    }

    /**
     * we assume that the environment is correctly set
     * i.e. -Djava.naming.provider.url="..//example010.properties"
     *
     * @return an initial context
     *
     * @throws NamingException if there is an error getting the context
     */
    public InitialContext getInitialContext() throws NamingException
    {
        if (_initialContext == null)
        {
            _initialContext = new InitialContext();
        }
        return _initialContext;
    }

    /**
     * Get the default connection factory for the currently used broker
     * Default factory is "local"
     *
     * @return A connection factory
     *
     * @throws Exception if there is an error getting the factory
     */
    public AMQConnectionFactory getConnectionFactory() throws NamingException
    {
        if (_connectionFactory == null)
        {
            if (Boolean.getBoolean(PROFILE_USE_SSL))
            {
                _connectionFactory = getConnectionFactory("default.ssl");
            }
            else
            {
                _connectionFactory = getConnectionFactory("default");
            }
        }
        return _connectionFactory;
    }

    /**
     * Get a connection factory for the currently used broker
     *
     * @param factoryName The factory name
     *
     * @return A connection factory
     *
     * @throws Exception if there is an error getting the factory
     */
    public AMQConnectionFactory getConnectionFactory(String factoryName) throws NamingException
    {
        return (AMQConnectionFactory) getInitialContext().lookup(factoryName);
    }

    public Connection getConnection() throws JMSException, NamingException
    {
        return getConnection(GUEST_USERNAME, GUEST_PASSWORD);
    }

    public Connection getConnectionWithOptions(Map<String, String> options)
                throws URLSyntaxException, NamingException, JMSException
    {
        ConnectionURL curl = new AMQConnectionURL(getConnectionFactory().getConnectionURLString());
        for(Map.Entry<String,String> entry : options.entrySet())
        {
            curl.setOption(entry.getKey(), entry.getValue());
        }
        curl = new AMQConnectionURL(curl.toString());

        curl.setUsername(GUEST_USERNAME);
        curl.setPassword(GUEST_PASSWORD);
        return getConnection(curl);
    }


    public Connection getConnection(ConnectionURL url) throws JMSException
    {
        _logger.debug("get connection for " + url.getURL());
        Connection connection = new AMQConnectionFactory(url).createConnection(url.getUsername(), url.getPassword());

        _connections.add(connection);

        return connection;
    }

    /**
     * Get a connection (remote or in-VM)
     *
     * @param username The user name
     * @param password The user password
     *
     * @return a newly created connection
     *
     * @throws Exception if there is an error getting the connection
     */
    public Connection getConnection(String username, String password) throws JMSException, NamingException
    {
        _logger.debug("get connection for username " + username);
        Connection con = getConnectionFactory().createConnection(username, password);
        //add the connection in the list of connections
        _connections.add(con);
        return con;
    }

    protected Connection getClientConnection(String username, String password, String id) throws JMSException, URLSyntaxException, AMQException, NamingException
    {
        _logger.debug("get connection for id " + id);
        Connection con = getConnectionFactory().createConnection(username, password, id);
        //add the connection in the list of connections
        _connections.add(con);
        return con;
    }

    /**
     * Useful, for example, to avoid the connection being automatically closed in {@link #tearDown()}
     * if it has deliberately been put into an error state already.
     */
    protected void forgetConnection(Connection connection)
    {
        _logger.debug("Forgetting about connection " + connection);
        boolean removed = _connections.remove(connection);
        assertTrue(
                "The supplied connection " + connection + " should have been one that I already know about",
                removed);
    }

    /**
     * Return a uniqueName for this test.
     * In this case it returns a queue Named by the TestCase and TestName
     *
     * @return String name for a queue
     */
    protected String getTestQueueName()
    {
        return getClass().getSimpleName() + "-" + getName();
    }

    /**
     * Return a Queue specific for this test.
     * Uses getTestQueueName() as the name of the queue
     * @return
     */
    public Queue getTestQueue()
    {
        return new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, getTestQueueName());
    }

    /**
     * Return a Topic specific for this test.
     * Uses getTestQueueName() as the name of the topic
     * @return
     */
    public Topic getTestTopic()
    {
        return new AMQTopic(AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_NAME), getTestQueueName());
    }

    @Override
    protected void tearDown() throws java.lang.Exception
    {
        super.tearDown();

        // close all the connections used by this test.
        for (Connection c : _connections)
        {
            c.close();
        }
        if(_taskExecutor != null)
        {
            _taskExecutor.stop();
        }
    }

    /**
     * Consume all the messages in the specified queue. Helper to ensure
     * persistent tests don't leave data behind.
     *
     * @param queue the queue to purge
     *
     * @return the count of messages drained
     *
     * @throws Exception if a problem occurs
     */
    protected int drainQueue(Queue queue) throws Exception
    {
        Connection connection = getConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageConsumer consumer = session.createConsumer(queue);

        connection.start();

        int count = 0;
        while (consumer.receive(1000) != null)
        {
            count++;
        }

        connection.close();

        return count;
    }

    /**
     * Send messages to the given destination.
     *
     * If session is transacted then messages will be committed before returning
     *
     * @param session the session to use for sending
     * @param destination where to send them to
     * @param count no. of messages to send
     *
     * @return the sent messages
     *
     * @throws Exception
     */
    public List<Message> sendMessage(Session session, Destination destination,
                                     int count) throws Exception
    {
        return sendMessage(session, destination, count, 0, 0);
    }

    /**
     * Send messages to the given destination.
     *
     * If session is transacted then messages will be committed before returning
     *
     * @param session the session to use for sending
     * @param destination where to send them to
     * @param count no. of messages to send
     *
     * @param batchSize the batchSize in which to commit, 0 means no batching,
     * but a single commit at the end
     * @return the sent message
     *
     * @throws Exception
     */
    public List<Message> sendMessage(Session session, Destination destination,
                                     int count, int batchSize) throws Exception
    {
        return sendMessage(session, destination, count, 0, batchSize);
    }

    /**
     * Send messages to the given destination.
     *
     * If session is transacted then messages will be committed before returning
     *
     * @param session the session to use for sending
     * @param destination where to send them to
     * @param count no. of messages to send
     *
     * @param offset offset allows the INDEX value of the message to be adjusted.
     * @param batchSize the batchSize in which to commit, 0 means no batching,
     * but a single commit at the end
     * @return the sent message
     *
     * @throws Exception
     */
    public List<Message> sendMessage(Session session, Destination destination,
                                     int count, int offset, int batchSize) throws Exception
    {
        List<Message> messages = new ArrayList<Message>(count);

        MessageProducer producer = session.createProducer(destination);

        int i = offset;
        for (; i < (count + offset); i++)
        {
            Message next = createNextMessage(session, i);

            producer.send(next);

            if (session.getTransacted() && batchSize > 0)
            {
                if (i % batchSize == 0)
                {
                    session.commit();
                }

            }

            messages.add(next);
        }

        // Ensure we commit the last messages
        // Commit the session if we are transacted and
        // we have no batchSize or
        // our count is not divible by batchSize.
        if (session.getTransacted() &&
            ( batchSize == 0 || (i-1) % batchSize != 0))
        {
            session.commit();
        }

        return messages;
    }

    public Message createNextMessage(Session session, int msgCount) throws JMSException
    {
        Message message = createMessage(session, _messageSize);
        message.setIntProperty(INDEX, msgCount);

        return message;

    }

    public Message createMessage(Session session, int messageSize) throws JMSException
    {
        String payload = new String(new byte[messageSize]);

        Message message;

        switch (_messageType)
        {
            case BYTES:
                message = session.createBytesMessage();
                ((BytesMessage) message).writeUTF(payload);
                break;
            case MAP:
                message = session.createMapMessage();
                ((MapMessage) message).setString(CONTENT, payload);
                break;
            default: // To keep the compiler happy
            case TEXT:
                message = session.createTextMessage();
                ((TextMessage) message).setText(payload);
                break;
            case OBJECT:
                message = session.createObjectMessage();
                ((ObjectMessage) message).setObject(payload);
                break;
            case STREAM:
                message = session.createStreamMessage();
                ((StreamMessage) message).writeString(payload);
                break;
        }

        return message;
    }

    public BrokerDetails getBroker()
    {
        try
        {
            if (getConnectionFactory().getConnectionURL().getBrokerCount() > 0)
            {
                return getConnectionFactory().getConnectionURL().getBrokerDetails(0);
            }
            else
            {
                fail("No broker details are available.");
            }
        }
        catch (NamingException e)
        {
            fail(e.getMessage());
        }

        //keep compiler happy
        return null;
    }

    protected int getFailingPort()
    {
        return FAILING_PORT;
    }

    public int getHttpManagementPort(int mainPort)
    {
        return mainPort + (DEFAULT_HTTP_MANAGEMENT_PORT - DEFAULT_PORT);
    }

    public void assertProducingConsuming(final Connection connection) throws Exception
    {
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Destination destination = session.createQueue(getTestQueueName());
        MessageConsumer consumer = session.createConsumer(destination);
        sendMessage(session, destination, 1);
        session.commit();
        connection.start();
        Message m1 = consumer.receive(RECEIVE_TIMEOUT);
        assertNotNull("Message 1 is not received", m1);
        assertEquals("Unexpected first message received", 0, m1.getIntProperty(INDEX));
        session.commit();
        session.close();
    }

    protected BrokerOptions getBrokerOptions( boolean managementMode, String testConfig, String log4jConfig, boolean skipLoggingConfiguration)
    {
        BrokerOptions options = new BrokerOptions();

        options.setConfigurationStoreType(_brokerStoreType);
        options.setConfigurationStoreLocation(testConfig);
        options.setManagementMode(managementMode);
        if (managementMode)
        {
            options.setManagementModePassword(MANAGEMENT_MODE_PASSWORD);
        }
        options.setSkipLoggingConfiguration(skipLoggingConfiguration);
        options.setLogConfigFileLocation(log4jConfig);
        options.setStartupLoggedToSystemOut(false);
        return options;
    }

    private Map<String, String> getJvmProperties()
    {
        Map<String,String> jvmOptions = new HashMap();
        synchronized (_propertiesSetForBroker)
        {
            jvmOptions.putAll(_propertiesSetForBroker);

            copySystemProperty("amqj.protocol.logging.level", jvmOptions);
            copySystemProperty("root.logging.level", jvmOptions);

            copySystemProperty("test.port", jvmOptions);
            copySystemProperty("test.mport", jvmOptions);
            copySystemProperty("test.cport", jvmOptions);
            copySystemProperty("test.hport", jvmOptions);
            copySystemProperty("test.hsport", jvmOptions);
            copySystemProperty("test.port.ssl", jvmOptions);
            copySystemProperty("test.port.alt", jvmOptions);
            copySystemProperty("test.port.alt.ssl", jvmOptions);
            copySystemProperty("test.amqp_port_protocols", jvmOptions);

            copySystemProperty("virtualhostnode.type", jvmOptions);
            copySystemProperty("virtualhostnode.context.blueprint", jvmOptions);
        }
        return jvmOptions;
    }

    private void copySystemProperty(String name, Map<String, String> jvmOptions)
    {
        String value = System.getProperty(name);
        if (value != null)
        {
            jvmOptions.put(name, value);
        }
    }

    private Map<String, String> getEnvironmentProperties()
    {
        return new HashMap<>(_env);
    }

    private String getBrokerCommandTemplate()
    {
        return _brokerCommandTemplate;
    }

    public static class BrokerHolderFactory
    {

        public BrokerHolder create(BrokerHolder.BrokerType brokerType, int port, QpidBrokerTestCase testCase)
        {
            Set<Integer> portsUsedByBroker = testCase.guessAllPortsUsedByBroker(port);
            BrokerHolder holder = null;
            if (brokerType.equals(BrokerHolder.BrokerType.INTERNAL) && !testCase.existingInternalBroker())
            {
                testCase.setSystemProperty(BrokerProperties.PROPERTY_USE_CUSTOM_RMI_SOCKET_FACTORY, "false");
                holder = new InternalBrokerHolder(portsUsedByBroker);
            }
            else if (!brokerType.equals(BrokerHolder.BrokerType.EXTERNAL))
            {

                Map<String,String> jvmOptions = testCase.getJvmProperties();
                Map<String,String> environmentProperties = testCase.getEnvironmentProperties();

                holder = new SpawnedBrokerHolder(testCase.getBrokerCommandTemplate(), port, testCase.getTestName(), jvmOptions, environmentProperties, brokerType, portsUsedByBroker);
            }
            return holder;
        }
    }

}
