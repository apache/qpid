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
 * This verifies that changing the {@code transactionTimeout} configuration will alter
 * the behaviour of the transaction open and idle logging, and that when the connection
 * will be closed.
 */
public class TransactionTimeoutConfigurationTest extends TransactionTimeoutTestCase
{
    @Override
    protected void configure() throws Exception
    {
        // Setup housekeeping every second
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".housekeeping.expiredMessageCheckPeriod", "100");
        
        // Set transaction timout properties.
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openWarn", "200");
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openClose", "1000");
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleWarn", "100");
        setConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleClose", "500");
    }

    public void testProducerIdleCommit() throws Exception
    {
        try
        {
            send(5, 0);
            
            sleep(2.0f);

            _psession.commit();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        monitor(5, 0);
        
        check(IDLE);
    }

    public void testProducerOpenCommit() throws Exception
    {
        try
        {
            send(5, 0.3f);

            _psession.commit();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        monitor(6, 3);
        
        check(OPEN);
    }
}
