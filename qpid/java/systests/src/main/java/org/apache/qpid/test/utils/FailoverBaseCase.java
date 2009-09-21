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
package org.apache.qpid.test.utils;

import javax.jms.Connection;

import org.apache.qpid.util.FileUtils;

public class FailoverBaseCase extends QpidTestCase
{

    public static int FAILING_VM_PORT = 2;
    public static int FAILING_PORT = Integer.parseInt(System.getProperty("test.port.alt"));

    protected int failingPort;
    
    private boolean failedOver = false;

    public FailoverBaseCase()
    {
        if (_broker.equals(VM))
        {
            failingPort = FAILING_VM_PORT;
        }
        else
        {
        	failingPort = FAILING_PORT;
        }
    }
    
    protected int getFailingPort()
    {
        return failingPort;
    }

    protected void setUp() throws java.lang.Exception
    {
        super.setUp();
        setSystemProperty("QPID_WORK", System.getProperty("java.io.tmpdir")+"/"+getFailingPort());
        startBroker(failingPort);
    }

    /**
     * We are using failover factories
     *
     * @return a connection 
     * @throws Exception
     */
    public Connection getConnection() throws Exception
    {
        Connection conn =
        	(Boolean.getBoolean("profile.use_ssl"))?
        			getConnectionFactory("failover.ssl").createConnection("guest", "guest"):		
        			getConnectionFactory("failover").createConnection("guest", "guest");
        _connections.add(conn);
        return conn;
    }

    public void tearDown() throws Exception
    {
    	stopBroker(_broker.equals(VM)?FAILING_PORT:FAILING_PORT);
        super.tearDown();
        FileUtils.deleteDirectory(System.getProperty("java.io.tmpdir")+"/"+getFailingPort());
    }


    /**
     * Only used of VM borker.
     */
    public void failBroker()
    {
        failedOver = true;
        try
        {
            stopBroker(getFailingPort());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    
    protected void setFailingPort(int p)
    {
        failingPort = p;
    }
}
