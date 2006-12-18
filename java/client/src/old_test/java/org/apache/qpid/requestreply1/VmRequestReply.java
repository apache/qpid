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
package org.apache.qpid.requestreply1;

import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.test.VMBrokerSetup;
import org.apache.log4j.Logger;

import junit.framework.TestCase;

public class VmRequestReply extends TestCase
{
    private static final Logger _logger = Logger.getLogger(VmRequestReply.class);

    public void testSimpleClient() throws Exception
    {
        ServiceProvidingClient serviceProvider = new ServiceProvidingClient("vm://:1", "guest", "guest",
                                                                            "serviceProvidingClient", "/test",
                                                                            "serviceQ");

        ServiceRequestingClient serviceRequester = new ServiceRequestingClient("vm://:1", "myClient", "guest", "guest",
                                                                               "/test", "serviceQ", 5000, 512);

        serviceProvider.run();
        Object waiter = new Object();
        serviceRequester.run(waiter);
        synchronized (waiter)
        {
            while (!serviceRequester.isCompleted())
            {
                waiter.wait();
            }
        }
    }

    public static void main(String[] args)
    {
        VmRequestReply rr = new VmRequestReply();
        try
        {
            rr.testSimpleClient();
        }
        catch (Exception e)
        {
            _logger.error("Error: " + e, e);
        }
    }

    public static junit.framework.Test suite()
    {
        return new VMBrokerSetup(new junit.framework.TestSuite(VmRequestReply.class));
    }
}
