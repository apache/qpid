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

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.server.registry.ApplicationRegistry;

import javax.jms.Connection;

public class FailoverBaseCase extends QpidTestCase
{

    protected int FAILING_VM_PORT = 2;
    protected int FAILING_PORT = 5673;

    private boolean failedOver = false;

    private int getFailingPort()
    {
        if (_broker.equals(VM))
        {
            return FAILING_VM_PORT;
        }
        else
        {
            return FAILING_PORT;
        }
    }

    protected void setUp() throws java.lang.Exception
    {
        super.setUp();
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
        if (!failedOver)
        {
            stopBroker(getFailingPort());
        }
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
}
