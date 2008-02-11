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

import javax.jms.Connection;
import javax.naming.InitialContext;
import java.io.InputStream;
import java.io.IOException;

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

    // system properties
    private static final String BROKER = "broker";
    private static final String BROKER_VERSION  = "broker.version";

    // values
    private static final String VM = "vm";
    private static final String EXTERNAL = "external";
    private static final String VERSION_08 = "0-8";
    private static final String VERSION_010 = "0-10";

    private String _broker = System.getProperty(BROKER, VM);
    private String _brokerVersion = System.getProperty(BROKER_VERSION, VERSION_08);

    private Process _brokerProcess;

    private InitialContext _initialContext;
    private AMQConnectionFactory _connectionFactory;

    protected void setUp() throws Exception
    {
        super.setUp();
        startBroker();
    }

    protected void tearDown() throws Exception
    {
        stopBroker();
        super.tearDown();
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

            new Thread()
            {
                private InputStream in = _brokerProcess.getInputStream();

                public void run()
                {
                    try
                    {
                        byte[] buf = new byte[4*1024];
                        int n;
                        while ((n = in.read(buf)) != -1)
                        {
                            System.out.write(buf, 0, n);
                        }
                    }
                    catch (IOException e)
                    {
                        _logger.info("redirector", e);
                    }
                }
            }.start();

            Thread.sleep(1000);

            try
            {
                int exit = _brokerProcess.exitValue();
                throw new RuntimeException("broker aborted: " + exit);
            }
            catch (IllegalThreadStateException e)
            {
                // this is expect if the broker started succesfully
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
