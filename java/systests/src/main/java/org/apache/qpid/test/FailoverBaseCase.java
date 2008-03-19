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
package org.apache.qpid.test;

import org.apache.qpid.test.VMTestCase;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;

import javax.naming.spi.InitialContextFactory;
import javax.naming.NamingException;
import java.util.Hashtable;
import java.util.Map;

public class FailoverBaseCase extends VMTestCase
{
    private boolean failedOver = true;

    public void setUp() throws Exception
    {
        // Make Broker 2 the first one so we can kill it and allow VMTestCase to clean up vm://:1
        _brokerlist = "vm://:2?connectdelay='150';vm://:1?connectdelay='150'";
        _clientID = this.getClass().getName();
        _virtualhost = "/test";

        _connections.put("connection1", "amqp://guest:guest@" + _clientID + _virtualhost + "?brokerlist='vm://:1'");
        _connections.put("connection2", "amqp://guest:guest@" + _clientID + _virtualhost + "?brokerlist='vm://:2'");

        try
        {
            TransportConnection.createVMBroker(2);
        }
        catch (Exception e)
        {
            fail("Unable to create broker: " + e);
        }

        super.setUp();
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


    public void failBroker()
    {
        failedOver = true;
        TransportConnection.killVMBroker(2);
        ApplicationRegistry.remove(2);
    }
}
