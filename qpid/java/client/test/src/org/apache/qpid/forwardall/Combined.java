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
package org.apache.qpid.forwardall;

import junit.framework.JUnit4TestAdapter;
import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import org.junit.After;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.vmbroker.AMQVMBrokerCreationException;

/**
 * Runs the Service's and Client parts of the test in the same process
 * as the broker
 */
public class Combined
{
    @Test
    public void forwardAll() throws Exception
    {
        int services = 2;
        ServiceCreator.start("vm://:1", services);

        //give them time to get registered etc.
        System.out.println("Services started, waiting for them to initialise...");
        Thread.sleep(5 * 1000);
        System.out.println("Starting client...");

        new Client("vm://:1", services).waitUntilComplete();

        System.out.println("Completed successfully!");
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(Combined.class);
    }
}
