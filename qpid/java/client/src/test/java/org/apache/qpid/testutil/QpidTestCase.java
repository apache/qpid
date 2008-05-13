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
package org.apache.qpid.testutil;

import junit.framework.TestCase;
import junit.framework.TestResult;

import javax.jms.Connection;
import javax.naming.InitialContext;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 */
public class QpidTestCase extends TestCase
{

    private static final Logger _logger = LoggerFactory.getLogger(QpidTestCase.class);

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
        if (Boolean.getBoolean("test.excludes"))
        {
            _logger.info("Some tests should be excluded, building the exclude list");
            String exclusionListURI = System.getProperties().getProperty("test.excludesfile", "");
            String exclusionListString = System.getProperties().getProperty("test.excludeslist", "");
            File file=new File(exclusionListURI);
            List<String> exclusionList = new ArrayList<String>();
            if (file.exists())
            {
                _logger.info("Using exclude file: " + exclusionListURI);
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
            else if( ! exclusionListString.equals(""))
            {
                _logger.info("Using excludeslist: " + exclusionListString);
                // the exclusion list may be specified as a string
                StringTokenizer t = new StringTokenizer(exclusionListString, " ");
                while (t.hasMoreTokens())
                {
                    exclusionList.add(t.nextToken());
                }
            }
            else
            {
                throw new RuntimeException("Aborting test: Cannot find excludes file nor excludes list");
            }
            _exclusionList = exclusionList;
        }
    }

    private static List<String> _exclusionList;

    // system properties
    private static final String BROKER = "broker";
    private static final String BROKER_CLEAN = "broker.clean";
    private static final String BROKER_VERSION  = "broker.version";
    private static final String BROKER_READY = "broker.ready";

    // values
    private static final String VM = "vm";
    private static final String EXTERNAL = "external";
    private static final String VERSION_08 = "0-8";
    private static final String VERSION_010 = "0-10";

    private String _broker = System.getProperty(BROKER, VM);
    private String _brokerClean = System.getProperty(BROKER_CLEAN, null);
    private String _brokerVersion = System.getProperty(BROKER_VERSION, VERSION_08);

    private Process _brokerProcess;

    private InitialContext _initialContext;
    private AMQConnectionFactory _connectionFactory;

    public void runBare() throws Throwable
    {
        String name = getClass().getSimpleName() + "." + getName();
        _logger.info("========== start " + name + " ==========");
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
            _logger.info("==========  stop " + name + " ==========");
        }
    }

    public void run(TestResult testResult)
    {
        if( _exclusionList != null && (_exclusionList.contains( getClass().getName() + "#*") ||
                _exclusionList.contains( getClass().getName() + "#" + getName())))
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

    public void startBroker() throws Exception
    {
        if (_broker.equals(VM))
        {
            // create an in_VM broker
            TransportConnection.createVMBroker(1);
        }
        else if (!_broker.equals(EXTERNAL))
        {
            _logger.info("starting broker: " + _broker);
            ProcessBuilder pb = new ProcessBuilder(_broker.split("\\s+"));
            pb.redirectErrorStream(true);
            _brokerProcess = pb.start();

            Piper p = new Piper(_brokerProcess.getInputStream(),
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
                int exit = _brokerProcess.exitValue();
                _logger.info("broker aborted: " + exit);
                cleanBroker();
                throw new RuntimeException("broker aborted: " + exit);
            }
            catch (IllegalThreadStateException e)
            {
                // this is expect if the broker started succesfully
            }
        }
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
        _logger.info("stopping broker: " + _broker);
        if (_brokerProcess != null)
        {
            _brokerProcess.destroy();
            _brokerProcess.waitFor();
            _logger.info("broker exited: " + _brokerProcess.exitValue());
            _brokerProcess = null;
        }
        else if (_broker.equals(VM))
        {
            TransportConnection.killAllVMBrokers();
        }
    }

    /**
     * Check whether the broker is an 0.8
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

    public void shutdownServer() throws Exception
    {
        stopBroker();
        startBroker();
    }
    /**
     * we assume that the environment is correctly set
     * i.e. -Djava.naming.provider.url="..//example010.properties"
     * TODO should be a way of setting that through maven
     *
     * @return an initial context
     * @throws Exception if there is an error getting the context
     */
    public InitialContext getInitialContext() throws Exception
    {
        _logger.info("get InitialContext");
        if (_initialContext == null)
        {
            _initialContext = new InitialContext();
        }
        return _initialContext;
    }

    /**
     * Get a connection factory for the currently used broker
     *
     * @return A conection factory
     * @throws Exception if there is an error getting the tactory
     */
    public AMQConnectionFactory getConnectionFactory() throws Exception
    {
        _logger.info("get ConnectionFactory");
        if (_connectionFactory == null)
        {
            _connectionFactory = (AMQConnectionFactory) getInitialContext().lookup("local");
        }
        return _connectionFactory;
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
     * @return a newly created connection
     * @throws Exception if there is an error getting the connection
     */
    public Connection getConnection(String username, String password) throws Exception
    {
        _logger.info("get Connection");
        Connection con;
        if (_broker.equals(VM))
        {
            con = new AMQConnection("vm://:1", username, password, "Test", "test");
        }
        else
        {
            con = getConnectionFactory().createConnection(username, password);
        }
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
            con = getConnectionFactory().createConnection(username, password);
        }
        return con;
    }

}
