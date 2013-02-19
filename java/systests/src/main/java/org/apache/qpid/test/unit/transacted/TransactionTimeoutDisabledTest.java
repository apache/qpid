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
package org.apache.qpid.test.unit.transacted;

/**
 * This verifies that the default behaviour is not to time out transactions.
 */
public class TransactionTimeoutDisabledTest extends TransactionTimeoutTestCase
{
    @Override
    protected void configure() throws Exception
    {
        // Setup housekeeping every second
        setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".housekeeping.checkPeriod", "100");

        // No transaction timeout configuration.
    }

    public void testProducerIdleCommit() throws Exception
    {
        try
        {
            send(5, 0);
            
            sleep(2.0f);

            _psession.commit();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());
        
        monitor(0, 0);
    }

    public void testProducerOpenCommit() throws Exception
    {
        try
        {
            send(5, 0.3f);

            _psession.commit();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());
        
        monitor(0, 0);
    }
}
