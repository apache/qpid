/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.requestreply1;

import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import org.junit.After;
import org.apache.log4j.Logger;
import junit.framework.JUnit4TestAdapter;

public class VmRequestReply
{
    private static final Logger _logger = Logger.getLogger(VmRequestReply.class);

    @Before
    public void startVmBrokers()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create VM Broker: " + e.getMessage());
        }
    }

    @After
    public void stopVmBrokers()
    {
        TransportConnection.killVMBroker(1);
    }

    @Test
    public void simpleClient() throws Exception
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

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(VmRequestReply.class);
    }

    public static void main(String[] args)
    {
        VmRequestReply rr = new VmRequestReply();
        try
        {
            rr.simpleClient();
        }
        catch (Exception e)
        {
            _logger.error("Error: " + e, e);
        }
    }
}
