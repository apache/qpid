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
import java.io.BufferedReader;
import java.io.InputStreamReader;

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

    /* this clas logger */
    private static final Logger _logger = LoggerFactory.getLogger(QpidTestCase.class);

    /* Test properties */
    private static final String SHEL = "broker_shel";
    private static final String BROKER_PATH = "broker_path";
    private static final String BROKER_PARAM = "broker_param";
    private static final String BROKER_VERSION  = "broker_version";
    public static final String BROKER_08 = "08";
    private static final String BROKER_VM = "vm";
    private static final String EXT_BROKER = "ext" ;
    /**
     * The process where the remote broker is running.
     */
    private Process _brokerProcess;

    /* The test property values */
    // The default broker is an in-VM one
    private String _shel = BROKER_VM;
    private String _brokerPath = "";
    private String _brokerParams = "";
    private String _brokerVersion = "08" ;

    /* The broker communication objects */
    private InitialContext _initialContext;
    private AMQConnectionFactory _connectionFactory;

    //--------- JUnit support

    protected void setUp() throws Exception
    {
        super.setUp();
        // get the propeties if they are set
         if (System.getProperties().containsKey(BROKER_VERSION ))
        {
            _brokerVersion = System.getProperties().getProperty(BROKER_VERSION );
        }
        if (System.getProperties().containsKey(SHEL))
        {
            _shel = System.getProperties().getProperty(SHEL);
        }
        if (System.getProperties().containsKey(BROKER_PATH))
        {
            _brokerPath = System.getProperties().getProperty(BROKER_PATH);
        }
        if (System.getProperties().containsKey(BROKER_PARAM))
        {
            _brokerParams = System.getProperties().getProperty(BROKER_PARAM);
        }
        if (!_shel.equals(BROKER_VM) && ! _shel.equals(EXT_BROKER) )
        {
            // start a new broker
            startBroker();
        }
        else if ( ! _shel.equals(EXT_BROKER) )
        {
            // create an in_VM broker
            TransportConnection.createVMBroker(1);
        }
        _logger.info("=========================================");
        _logger.info("broker version " + _brokerVersion + " ==== " + _shel + " " + _brokerPath + " " + _brokerParams);
    }

    /**
     * This method _is invoked after each test case.
     *
     * @throws Exception
     */
    protected void tearDown() throws Exception
    {
          killBroker();
         super.tearDown();
    }

    public void killBroker()
    {
        _logger.info("Kill broker");
        if (_brokerProcess != null)
        {
            // destroy the currently running broker
            _brokerProcess.destroy();
            _brokerProcess = null;
        }
        else   if ( _shel.equals(BROKER_VM))
        {
            TransportConnection.killAllVMBrokers();
        }
    }

    //--------- Util method

    /**
     * This method starts a remote server by spawning an external process.
     *
     * @throws Exception If the broker cannot be started
     */
    public void startBroker() throws Exception
    {
        _logger.info("Starting broker: " + _shel + " " + _brokerPath + "  " + _brokerParams + "");
        Runtime rt = Runtime.getRuntime();
        _brokerProcess = rt.exec(_shel + " " + _brokerPath + "  " + _brokerParams + "");
        BufferedReader reader = new BufferedReader(new InputStreamReader(_brokerProcess.getInputStream()));
        if (reader.ready())
        {
            //bad, we had an error starting the broker
            throw new Exception("Problem when starting the broker: " + reader.readLine());
        }
        // We need to wait for th ebroker to start ideally we would need to ping it
        synchronized(this)
        {
            this.wait(1000);
        }
    }

    /**
     * Check whether the broker is an 0.8
     * @return true if the broker is an 0_8 version, false otherwise. 
     */
    public boolean isBroker08()
    {
        return _brokerVersion.equals(BROKER_08);
    }

    /**
     * Stop the currently running broker.
     */
    public void stopBroker()
    {
        _logger.info("Stopping broker");
        // stooping the broker
        if (_brokerProcess != null)
        {
            _brokerProcess.destroy();
        }
        _initialContext = null;
        _connectionFactory = null;
    }

     public void shutdownServer() throws Exception
    {
        killBroker();
        setUp();
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
        if (_shel.equals(BROKER_VM))
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
        if (_shel.equals(BROKER_VM))
        {
            con = new AMQConnection("vm://:1", username, password, id, "test");
        }
        else
        {
            con = getConnectionFactory().createConnection(username, password);
        }
        return con;
    }

    public void testfoo()
    {
        //do nothing, just to avoid maven to report an error  
    }
}
