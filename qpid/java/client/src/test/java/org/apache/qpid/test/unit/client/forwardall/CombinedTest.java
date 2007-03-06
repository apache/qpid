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
package org.apache.qpid.test.unit.client.forwardall;

import junit.framework.TestCase;

import org.apache.qpid.testutil.VMBrokerSetup;
import org.apache.log4j.Logger;

/**
 * Runs the Service's and Client parts of the test in the same process
 * as the broker
 */
public class CombinedTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(CombinedTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    protected void tearDown() throws Exception
    {
        ServiceCreator.closeAll();
        super.tearDown();
    }

    public void testForwardAll() throws Exception
    {
        int services = 2;
        ServiceCreator.start("vm://:1", services);

        _logger.info("Starting client...");

        new Client("vm://:1", services).shutdownWhenComplete();

        _logger.info("Completed successfully!");
    }

    public static junit.framework.Test suite()
    {
        return new VMBrokerSetup(new junit.framework.TestSuite(CombinedTest.class));
    }
}
