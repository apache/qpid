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

import javax.jms.Connection;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 */
public class QpidTestCase extends TestCase
{

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
        if (Boolean.getBoolean("test.excludes"))
        {
            _logger.info("Some tests should be excluded, building the exclude list");
            String exclusionListURIs = System.getProperties().getProperty("test.excludesfile", "");
            String exclusionListString = System.getProperties().getProperty("test.excludeslist", "");
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
    private static final String BROKER = "broker";
    private static final String BROKER_CLEAN = "broker.clean";
    private static final String BROKER_VERSION = "broker.version";
    private static final String BROKER_READY = "broker.ready";
    private static final String TEST_OUTPUT = "test.output";

    // values
    protected static final String VM = "vm";
    protected static final String EXTERNAL = "external";
    private static final String VERSION_08 = "0-8";
    private static final String VERSION_010 = "0-10";

    private static final String QPID_HOME = "QPID_HOME";

    protected int DEFAULT_VM_PORT = 1;
    protected int DEFAULT_PORT = 5672;

    protected String _broker = System.getProperty(BROKER, VM);
    private String _brokerClean = System.getProperty(BROKER_CLEAN, null);
    private String _brokerVersion = System.getProperty(BROKER_VERSION, VERSION_08);
    private String _output = System.getProperty(TEST_OUTPUT);

    private Map<Integer,Process> _brokers = new HashMap<Integer,Process>();

    private InitialContext _initialContext;
    private AMQConnectionFactory _connectionFactory;

    private String _testName;

    // the connections created for a given test
    protected List<Connection> _connections = new ArrayList<Connection>();

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

        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        PrintStream out = null;
        PrintStream err = null;
        boolean redirected = _output != null && _output.length() > 0;
        if (redirected)
        {
            out = new PrintStream(String.format("%s/TEST-%s.out", _output, qname));
            err = new PrintStream(String.format("%s/TEST-%s.err", _output, qname));
            System.setOut(out);
            System.setErr(err);
        }

        _logger.info("========== start " + _testName + " ==========");
        startBroker();
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

    public void run(TestResult testResult)
    {
        if (_exclusionList != null && (_exclusionList.contains(getClass().getName() + "#*") ||
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

        public Piper(InputStream in, String ready)
        {
            this.in = new LineNumberReader(new InputStreamReader(in));
            this.ready = ready;
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
                return latch.await(timeout, unit);
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
                        latch.countDown();
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
    }

    public void startBroker(int port, ApplicationRegistry config) throws Exception
    {
        ApplicationRegistry.initialise(config, port);
        startBroker(port);
    }

    public void startBroker() throws Exception
    {
        startBroker(0);
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

    private String getBrokerCommand(int port)
    {
        return _broker
            .replace("@PORT", "" + port)
            .replace("@MPORT", "" + (port + (8999 - DEFAULT_PORT)));
    }

    public void startBroker(int port) throws Exception
    {
        port = getPort(port);

        Process process = null;
        if (_broker.equals(VM))
        {
            // create an in_VM broker
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

            process = pb.start();

            Piper p = new Piper(process.getInputStream(),
                                System.getProperty(BROKER_READY));

            p.start();

            if (!p.await(30, TimeUnit.SECONDS))
            {
                _logger.info("broker failed to become ready");
                cleanBroker();
                throw new RuntimeException("broker failed to become ready");
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

    protected void tearDown() throws java.lang.Exception
    {
        // close all the connections used by this test.
        for (Connection c : _connections)
        {
            c.close();
        }

        revertSystemProperties();
    }

}
