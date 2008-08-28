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
    private boolean failedOver = true;
    

    protected void setUp() throws java.lang.Exception
    {
        super.setUp();

        try
        {
            TransportConnection.createVMBroker(2);
        }
        catch (Exception e)
        {
            fail("Unable to create broker: " + e);
        }

    }

    /**
     * We are using failover factories, Note that 0.10 code path does not yet support failover.
     *
     * @return a connection 
     * @throws Exception
     */
    public Connection getConnection() throws Exception
    {
        Connection conn;
        if( _broker.equals(VM) )
        {
            conn = getConnectionFactory("vmfailover").createConnection("guest", "guest");
        }
        else
        {
            conn = getConnectionFactory("failover").createConnection("guest", "guest");
        }
        _connections.add(conn);
        return conn;
    }

    public void tearDown() throws Exception
    {
        if (!failedOver)
        {
            TransportConnection.killVMBroker(2);
            ApplicationRegistry.remove(2);
        }
        super.tearDown();
    }


    /**
     * Only used of VM borker.
     */
    public void failBroker()
    {
        failedOver = true;
        TransportConnection.killVMBroker(2);
        ApplicationRegistry.remove(2);
    }
}
