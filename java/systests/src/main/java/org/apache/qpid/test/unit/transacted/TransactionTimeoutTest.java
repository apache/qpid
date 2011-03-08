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
 * This tests the behaviour of transactional sessions when the {@code transactionTimeout} configuration
 * is set for a virtual host.
 * 
 * A producer that is idle for too long or open for too long will have its connection closed and
 * any further operations will fail with a 408 resource timeout exception. Consumers will not
 * be affected by the transaction timeout configuration.
 */
public class TransactionTimeoutTest extends TransactionTimeoutTestCase
{
    public void testProducerIdle() throws Exception
    {
        try
        {
            sleep(2.0f);
    
            _psession.commit();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
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
            send(6, 0.5f);
    
            _psession.commit();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        monitor(0, 10);
        
        check(OPEN);
    }
    
    public void testProducerIdleCommitTwice() throws Exception
    {
        try
        {
            send(5, 0);
            
            sleep(1.0f);
            
            _psession.commit();
            
            send(5, 0);
            
            sleep(2.0f);
    
            _psession.commit();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        monitor(10, 0);
        
        check(IDLE);
    }
    
    public void testProducerOpenCommitTwice() throws Exception
    {
        try
        {
            send(5, 0);
            
            sleep(1.0f);
            
            _psession.commit();
            
            send(6, 0.5f);
    
            _psession.commit();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        // the presistent store generates more idle messages?
        monitor(isBrokerStorePersistent() ? 10 : 5, 10);
        
        check(OPEN);
    }
    
    public void testProducerIdleRollback() throws Exception
    {
        try
        {
            send(5, 0);
            
            sleep(2.0f);
    
            _psession.rollback();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        monitor(5, 0);
        
        check(IDLE);
    }
    
    public void testProducerIdleRollbackTwice() throws Exception
    {
        try
        {
            send(5, 0);
            
            sleep(1.0f);
            
            _psession.rollback();
            
            send(5, 0);
            
            sleep(2.0f);
    
            _psession.rollback();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }
        
        monitor(10, 0);
        
        check(IDLE);
    }
    
    public void testConsumerCommitClose() throws Exception
    {
        try
        {
            send(1, 0);
    
            _psession.commit();
    
            expect(1, 0);
            
            _csession.commit();
            
            sleep(3.0f);
    
            _csession.close();
        }
        catch (Exception e)
        {
            fail("should have succeeded: " + e.getMessage());
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
    }
    
    public void testConsumerIdleReceiveCommit() throws Exception
    {
        try
        {
            send(1, 0);
    
            _psession.commit();
    
            sleep(2.0f);
            
            expect(1, 0);
            
            sleep(2.0f);
    
            _csession.commit();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
    }
    
    public void testConsumerIdleCommit() throws Exception
    {
        try
        {
            send(1, 0);
    
            _psession.commit();
    
            expect(1, 0);
            
            sleep(2.0f);
    
            _csession.commit();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
    }
    
    public void testConsumerIdleRollback() throws Exception
    {
        try
        {
            send(1, 0);
    
            _psession.commit();
            
            expect(1, 0);
            
            sleep(2.0f);
    
            _csession.rollback();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
    }
    
    public void testConsumerOpenCommit() throws Exception
    {
        try
        {
            send(1, 0);
    
            _psession.commit();
            
            sleep(3.0f);
    
            _csession.commit();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
    }
    
    public void testConsumerOpenRollback() throws Exception
    {
        try
        {
            send(1, 0);
    
            _psession.commit();
    
            sleep(3.0f);
    
            _csession.rollback();
        }
        catch (Exception e)
        {
            fail("Should have succeeded");
        }
        
        assertTrue("Listener should not have received exception", _caught.getCount() == 1);
        
        monitor(0, 0);
    }
}
