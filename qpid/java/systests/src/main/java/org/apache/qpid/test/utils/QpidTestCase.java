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

import junit.framework.TestCase;
import junit.framework.TestResult;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.server.store.DerbyMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 */
public class QpidTestCase extends TestCase
{
    protected final String QpidHome = System.getProperty("QPID_HOME");
    protected File _configFile = new File(System.getProperty("broker.config"));

    private static final Logger _logger = LoggerFactory.getLogger(QpidTestCase.class);

    protected long RECEIVE_TIMEOUT = 1000l;

    private Map<String, String> _setProperties = new HashMap<String, String>();

    /**
     * Some tests are excluded when the property test.excludes is set to true.
     * An exclusion list is either a file (prop test.excludesfile) which contains one test name
     * to be excluded per line or a String (prop test.excludeslist) where tests to be excluded are
     * separated by " ". Excluded tests are specified following the format:
     * className#testName where className is the class of the test to be
     * excluded and testName is the name of the test to be excluded.
     * className#* excludes all the tests of the specified class.
     */
    private static final String DEFAULT_INITIAL_CONTEXT = "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";

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

        String initialContext = System.getProperty(InitialContext.INITIAL_CONTEXT_FACTORY);

        if (initialContext == null || initialContext.length() == 0)
        {
            System.setProperty(InitialContext.INITIAL_CONTEXT_FACTORY, DEFAULT_INITIAL_CONTEXT);
        }
    }

    private static List<String> _exclusionList;

    // system properties
    private static final String BROKER_LANGUAGE = "broker.language";
    private static final String BROKER = "broker";
    private static final String BROKER_CLEAN = "broker.clean";
    private static final String BROKER_VERSION = "broker.version";
    protected static final String BROKER_READY = "broker.ready";
    private static final String BROKER_STOPPED = "broker.stopped";
    private static final String TEST_OUTPUT = "test.output";

    // values
    protected static final String JAVA = "java";
    protected static final String CPP = "cpp";
    protected static final String VM = "vm";
    protected static final String EXTERNAL = "external";
    private static final String VERSION_08 = "0-8";
    private static final String VERSION_010 = "0-10";

    protected static final String QPID_HOME = "QPID_HOME";

    protected static int DEFAULT_VM_PORT = 1;
    protected static int DEFAULT_PORT = Integer.getInteger("test.port", 5672);
    protected static int DEFAULT_MANAGEMENT_PORT = Integer.getInteger("test.mport", 8999);

    protected String _brokerLanguage = System.getProperty(BROKER_LANGUAGE, JAVA);
    protected String _broker = System.getProperty(BROKER, VM);
    private String _brokerClean = System.getProperty(BROKER_CLEAN, null);
    private String _brokerVersion = System.getProperty(BROKER_VERSION, VERSION_08);
    private String _output = System.getProperty(TEST_OUTPUT);

    protected File _outputFile;

    private Map<Integer, Process> _brokers = new HashMap<Integer, Process>();

    private InitialContext _initialContext;
    private AMQConnectionFactory _connectionFactory;

    private String _testName;

    // the connections created for a given test
    protected List<Connection> _connections = new ArrayList<Connection>();
    public static final String QUEUE = "queue";
    public static final String TOPIC = "topic";
    /** Map to hold test defined environment properties */
    private Map<String,String> _env;


    public QpidTestCase(String name)
    {
        super(name);
    }

    public QpidTestCase()
    {
        super("QpidTestCase");
    }

    public void runBare() throws Throwable
    {
        _testName = getClass().getSimpleName() + "." + getName();
        String qname = getClass().getName() + "." + getName();

        // Initalise this for each test run
        _env = new HashMap<String, String>();

        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        PrintStream out = null;
        PrintStream err = null;
        boolean redirected = _output != null && _output.length() > 0;
        if (redirected)
        {
            _outputFile = new File(String.format("%s/TEST-%s.out", _output, qname));
            out = new PrintStream(_outputFile);
            err = new PrintStream(String.format("%s/TEST-%s.err", _output, qname));
            System.setOut(out);
            System.setErr(err);
        }

        _logger.info("========== start " + _testName + " ==========");
        try
        {
            super.runBare();
        }
        finally
        {
            try
            {
                stopBroker();
            }
            catch (Exception e)
            {
                _logger.error("exception stopping broker", e);
            }
            _logger.info("==========  stop " + _testName + " ==========");

            if (redirected)
            {
                System.setErr(oldErr);
                System.setOut(oldOut);
                err.close();
                out.close();
            }
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        if (!_configFile.exists())
        {
            fail("Unable to test without config file:" + _configFile);
        }

        startBroker();
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

    private static final class Piper extends Thread
    {

        private LineNumberReader in;
        private String ready;
        private CountDownLatch latch;
        private boolean seenReady;
        private String stopped;
        private String stopLine;

        public Piper(InputStream in, String ready)
        {
            this(in, ready, null);
        }

        public Piper(InputStream in, String ready, String stopped)
        {
            this.in = new LineNumberReader(new InputStreamReader(in));
            this.ready = ready;
            this.stopped = stopped;
            this.seenReady = false;

            if (this.ready != null && !this.ready.equals(""))
            {
                this.latch = new CountDownLatch(1);
            }
            else
            {
                this.latch = null;
            }
        }

        public Piper(InputStream in)
        {
            this(in, null);
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException
        {
            if (latch == null)
            {
                return true;
            }
            else
            {
                latch.await(timeout, unit);
                return seenReady;
            }
        }

        public void run()
        {
            try
            {
                String line;
                while ((line = in.readLine()) != null)
                {
                    System.out.println(line);
                    if (latch != null && line.contains(ready))
                    {
                        seenReady = true;
                        latch.countDown();
                    }

                    if (latch != null && line.contains(stopped))
                    {
                        stopLine = line;
                    }
                }
            }
            catch (IOException e)
            {
                // this seems to happen regularly even when
                // exits are normal
            }
            finally
            {
                if (latch != null)
                {
                    latch.countDown();
                }
            }
        }

        public String getStopLine()
        {
            return stopLine;
        }
    }

    public void startBroker() throws Exception
    {
        startBroker(0);
    }

    /**
     * Return the management portin use by the broker on this main port
     * @param mainPort the broker's main port.
     * @return the management port that corresponds to the broker on the given port
     */
    protected int getManagementPort(int mainPort)
    {
        return mainPort + (DEFAULT_MANAGEMENT_PORT - DEFAULT_PORT);
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

    private int getPort(int port)
    {
        if (_broker.equals(VM))
        {
            return port == 0 ? DEFAULT_VM_PORT : port;
        }
        else if (!_broker.equals(EXTERNAL))
        {
            return port == 0 ? DEFAULT_PORT : port;
        }
        else
        {
            return port;
        }
    }

    private String getBrokerCommand(int port) throws MalformedURLException
    {
        return _broker
                .replace("@PORT", "" + port)
                .replace("@SSL_PORT", "" + (port - 1))
                .replace("@MPORT", "" + getManagementPort(port))
                .replace("@CONFIG_FILE", _configFile.toString());
    }

    public void startBroker(int port) throws Exception
    {
        port = getPort(port);

        Process process = null;
        if (_broker.equals(VM))
        {
            // create an in_VM broker
            ApplicationRegistry.initialise(new ConfigurationFileApplicationRegistry(_configFile), port);
            TransportConnection.createVMBroker(port);
        }
        else if (!_broker.equals(EXTERNAL))
        {
            String cmd = getBrokerCommand(port);
            _logger.info("starting broker: " + cmd);
            ProcessBuilder pb = new ProcessBuilder(cmd.split("\\s+"));
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();

            String qpidHome = System.getProperty(QPID_HOME);
            env.put(QPID_HOME, qpidHome);

            //Augment Path with bin directory in QPID_HOME.
            env.put("PATH", env.get("PATH").concat(File.pathSeparator + qpidHome + "/bin"));

            //Add the test name to the broker run.
            env.put("QPID_PNAME", "-DPNAME=\"" + _testName + "\"");
            env.put("QPID_WORK", System.getProperty("QPID_WORK"));

            // Add all the environment settings the test requested
            if (!_env.isEmpty())
            {
                for(Map.Entry<String,String> entry : _env.entrySet())
                {
                    env.put(entry.getKey() ,entry.getValue());
                }
            }

            process = pb.start();

            Piper p = new Piper(process.getInputStream(),
                                System.getProperty(BROKER_READY),
                                System.getProperty(BROKER_STOPPED));

            p.start();

            if (!p.await(30, TimeUnit.SECONDS))
            {
                _logger.info("broker failed to become ready:" + p.getStopLine());
                //Ensure broker has stopped
                process.destroy();
                cleanBroker();
                throw new RuntimeException("broker failed to become ready:"
                                           + p.getStopLine());
            }

            try
            {
                int exit = process.exitValue();
                _logger.info("broker aborted: " + exit);
                cleanBroker();
                throw new RuntimeException("broker aborted: " + exit);
            }
            catch (IllegalThreadStateException e)
            {
                // this is expect if the broker started succesfully
            }
        }

        _brokers.put(port, process);
    }

    public void cleanBroker()
    {
        if (_brokerClean != null)
        {
            _logger.info("clean: " + _brokerClean);

            try
            {
                ProcessBuilder pb = new ProcessBuilder(_brokerClean.split("\\s+"));
                pb.redirectErrorStream(true);
                Process clean = pb.start();
                new Piper(clean.getInputStream()).start();

                clean.waitFor();

                _logger.info("clean exited: " + clean.exitValue());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void stopBroker() throws Exception
    {
        stopBroker(0);
    }

    public void stopBroker(int port) throws Exception
    {
        port = getPort(port);

        _logger.info("stopping broker: " + getBrokerCommand(port));
        Process process = _brokers.remove(port);
        if (process != null)
        {
            process.destroy();
            process.waitFor();
            _logger.info("broker exited: " + process.exitValue());
        }
        else if (_broker.equals(VM))
        {
            TransportConnection.killVMBroker(port);
            ApplicationRegistry.remove(port);
        }
    }

    /**
     * Attempt to set the Java Broker to use the BDBMessageStore for persistence
     * Falling back to the DerbyMessageStore if
     *
     * @param virtualhost - The virtualhost to modify
     *
     * @throws ConfigurationException - when reading/writing existing configuration
     * @throws IOException            - When creating a temporary file.
     */
    protected void makeVirtualHostPersistent(String virtualhost)
            throws ConfigurationException, IOException
    {
        Class storeClass = DerbyMessageStore.class;

        Class bdb = null;
        try
        {
            bdb = Class.forName("org.apache.qpid.server.store.berkeleydb.BDBMessageStore");
        }
        catch (ClassNotFoundException e)
        {
            // No BDB store, we'll use Derby instead.
        }

        if (bdb != null)
        {
            storeClass = bdb;
        }

        // First we munge the config file and, if we're in a VM, set up an additional logfile
        XMLConfiguration configuration = new XMLConfiguration(_configFile);
        configuration.setProperty("virtualhosts.virtualhost." + virtualhost +
                                  ".store.class", storeClass.getName());
        configuration.setProperty("virtualhosts.virtualhost." + virtualhost +
                                  ".store." + DerbyMessageStore.ENVIRONMENT_PATH_PROPERTY,
                                  "${work}/" + virtualhost);

        File tmpFile = File.createTempFile("configFile", "test");
        tmpFile.deleteOnExit();
        configuration.save(tmpFile);
        _configFile = tmpFile;
    }

    /**
     * Get a property value from the current configuration file.
     *
     * @param property the property to lookup
     *
     * @return the requested String Value
     *
     * @throws org.apache.commons.configuration.ConfigurationException
     *
     */
    protected String getConfigurationStringProperty(String property) throws ConfigurationException
    {
        ServerConfiguration configuration = new ServerConfiguration(_configFile);
        return configuration.getConfig().getString(property);
    }

    /**
     * Set a configuration Property for this test run.
     *
     * This creates a new configuration based on the current configuration
     * with the specified property change.
     *
     * Multiple calls to this method will result in multiple temporary
     * configuration files being created.
     *
     * @param property the configuration property to set
     * @param value    the new value
     *
     * @throws ConfigurationException when loading the current config file
     * @throws IOException            when writing the new config file
     */
    protected void setConfigurationProperty(String property, String value)
            throws ConfigurationException, IOException
    {
        XMLConfiguration configuration = new XMLConfiguration(_configFile);

        // If we are modifying a virtualhost value then we need to do so in
        // the virtualhost.xml file as these values overwrite the values in
        // the main config.xml file
        if (property.startsWith("virtualhosts"))
        {
            // So locate the virtualhost.xml file and use the ServerConfiguration
            // flatConfig method to get the interpolated value.
            String vhostConfigFile = ServerConfiguration.
                    flatConfig(_configFile).getString("virtualhosts");

            // Load the vhostConfigFile
            XMLConfiguration vhostConfiguration = new XMLConfiguration(vhostConfigFile);

            // Set the value specified in to the vhostConfig.
            // Remembering that property will be 'virtualhosts.virtulhost....'
            // so we need to take off the 'virtualhosts.' from the start.
            vhostConfiguration.setProperty(property.substring(property.indexOf(".") + 1), value);

            // Write out the new virtualhost config file
            File tmpFile = File.createTempFile("virtualhost-configFile", ".xml");
            tmpFile.deleteOnExit();
            vhostConfiguration.save(tmpFile);

            // Change the property and value to be the new virtualhosts file
            // so that then update the value in the main config file.
            property = "virtualhosts";
            value = tmpFile.getAbsolutePath();
        }

        configuration.setProperty(property, value);

        // Write the new server config file
        File tmpFile = File.createTempFile("configFile", ".xml");
        tmpFile.deleteOnExit();
        configuration.save(tmpFile);

        _logger.info("Qpid Test Case now using configuration File:"
                     + tmpFile.getAbsolutePath());

        _configFile = tmpFile;
    }

    /**
     * Set a System property for the duration of this test.
     *
     * When the test run is complete the value will be reverted.
     *
     * @param property the property to set
     * @param value    the new value to use
     */
    protected void setSystemProperty(String property, String value)
    {
        if (!_setProperties.containsKey(property))
        {
            _setProperties.put(property, System.getProperty(property));
        }

        System.setProperty(property, value);
    }

    protected void revertSystemProperties()
    {
        for (String key : _setProperties.keySet())
        {
            String value = _setProperties.get(key);
            if (value != null)
            {
                System.setProperty(key, value);
            }
            else
            {
                System.clearProperty(key);
            }
        }
    }

    /**
     * Add an environtmen variable for the external broker environment
     *
     * @param property the property to set
     * @param value the value to set it to
     */
    protected void setBrokerEnvironment(String property, String value)
    {
        _env.put(property,value);
    }


    /**
     * Check whether the broker is an 0.8
     *
     * @return true if the broker is an 0_8 version, false otherwise.
     */
    public boolean isBroker08()
    {
        return _brokerVersion.equals(VERSION_08);
    }

    public boolean isBroker010()
    {
        return _brokerVersion.equals(VERSION_010);
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
        return !_broker.equals("vm");
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
     * TODO should be a way of setting that through maven
     *
     * @return an initial context
     *
     * @throws NamingException if there is an error getting the context
     */
    public InitialContext getInitialContext() throws NamingException
    {
        _logger.info("get InitialContext");
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
     * @return A conection factory
     *
     * @throws Exception if there is an error getting the tactory
     */
    public AMQConnectionFactory getConnectionFactory() throws NamingException
    {
        _logger.info("get ConnectionFactory");
        if (_connectionFactory == null)
        {
            if (Boolean.getBoolean("profile.use_ssl"))
            {
                _connectionFactory = getConnectionFactory("ssl");
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
     * @return A conection factory
     *
     * @throws Exception if there is an error getting the tactory
     */
    public AMQConnectionFactory getConnectionFactory(String factoryName) throws NamingException
    {
        if (_broker.equals(VM))
        {
            factoryName += ".vm";
        }

        return (AMQConnectionFactory) getInitialContext().lookup(factoryName);
    }

    public Connection getConnection() throws Exception
    {
        return getConnection("guest", "guest");
    }

    public Connection getConnection(ConnectionURL url) throws JMSException
    {
        Connection connection = new AMQConnectionFactory(url).createConnection("guest", "guest");

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
    public Connection getConnection(String username, String password) throws Exception
    {
        _logger.info("get Connection");
        Connection con = getConnectionFactory().createConnection(username, password);
        //add the connection in the lis of connections
        _connections.add(con);
        return con;
    }

    public Connection getConnection(String username, String password, String id) throws Exception
    {
        _logger.info("get Connection");
        Connection con;
        if (_broker.equals(VM))
        {
            con = new AMQConnection("vm://:1", username, password, id, "test");
        }
        else
        {
            con = getConnectionFactory().createConnection(username, password, id);
        }
        //add the connection in the lis of connections
        _connections.add(con);
        return con;
    }

    /**
     * Return a uniqueName for this test.
     * In this case it returns a queue Named by the TestCase and TestName
     * @return String name for a queue
     */
    protected String getTestQueueName()
    {
        return getClass().getSimpleName() + "-" + getName();
    }

    protected void tearDown() throws java.lang.Exception
    {
        // close all the connections used by this test.
        for (Connection c : _connections)
        {
            c.close();
        }

        revertSystemProperties();
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

    public List<Message> sendMessage(Session session, Destination destination,
                                     int count) throws Exception
    {
        return sendMessage(session, destination, count, 0);
    }

    public List<Message> sendMessage(Session session, Destination destination,
                                     int count, int batchSize) throws Exception
    {
        List<Message> messages = new ArrayList<Message>(count);

        MessageProducer producer = session.createProducer(destination);

        for (int i = 0; i < count; i++)
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
        if (session.getTransacted() && (batchSize > 0) &&
            (count / batchSize != 0))
        {
            session.commit();
        }

        return messages;
    }

    public Message createNextMessage(Session session, int msgCount) throws JMSException
    {
        return session.createMessage();
    }

    public ConnectionURL getConnectionURL() throws NamingException
    {
        return getConnectionFactory().getConnectionURL();
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

}
