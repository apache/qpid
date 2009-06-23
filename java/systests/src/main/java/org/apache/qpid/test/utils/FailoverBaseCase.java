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
    public static int FAILING_PORT = DEFAULT_PORT + 1;

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
        cleanBroker();
        FileUtils.deleteDirectory(System.getProperty("java.io.tmpdir")+"/"+getFailingPort());
        System.setProperty("QPID_WORK", System.getProperty("java.io.tmpdir")+"/"+getFailingPort());
        startBroker(getFailingPort());
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
            getConnectionFactory("failover").createConnection("guest", "guest");
        _connections.add(conn);
        return conn;
    }

    public void tearDown() throws Exception
    {
        int port;
        if (_broker.equals(VM))
        {
            port = FAILING_VM_PORT;
        }
        else
        {
            port = FAILING_PORT;
        }
        stopBroker(port);
        FileUtils.deleteDirectory(System.getProperty("java.io.tmpdir")+"/"+getFailingPort());
        super.tearDown();
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
