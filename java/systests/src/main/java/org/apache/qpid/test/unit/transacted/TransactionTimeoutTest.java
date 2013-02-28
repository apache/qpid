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

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;

/**
 * This tests the behaviour of transactional sessions when the {@code transactionTimeout} configuration
 * is set for a virtual host.
 * 
 * A producer that is idle for too long or open for too long will have its connection/session(0-10) closed and
 * any further operations will fail with a 408 resource timeout exception. Consumers will not
 * be affected by the transaction timeout configuration.
 */
public class TransactionTimeoutTest extends TransactionTimeoutTestCase
{

    protected void configure() throws Exception
    {
        // Setup housekeeping every 100ms
        setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".housekeeping.checkPeriod", "100");

        if (getName().contains("ProducerIdle"))
        {
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openWarn", "0");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openClose", "0");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleWarn", "500");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleClose", "1500");
        }
        else if (getName().contains("ProducerOpen"))
        {
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openWarn", "1000");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openClose", "2000");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleWarn", "0");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleClose", "0");
        }
        else
        {
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openWarn", "1000");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.openClose", "2000");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleWarn", "500");
            setVirtualHostConfigurationProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".transactionTimeout.idleClose", "1000");
        }
    }

    public void testProducerIdle() throws Exception
    {
        sleep(2.0f);

        _psession.commit();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }

    public void testProducerIdleCommit() throws Exception
    {
        send(5, 0);
        // Idle for more than idleClose to generate idle-warns and cause a close.
        sleep(2.0f);

        try
        {
            _psession.commit();
            fail("Exception not thrown");
        }
        catch (Exception e)
        {
            _exception = e;
        }

        monitor(10, 0);

        check(IDLE);
    }

    public void testProducerIdleCommitTwice() throws Exception
    {
        send(5, 0);
        // Idle for less than idleClose to generate idle-warns
        sleep(1.0f);

        _psession.commit();

        send(5, 0);
        // Now idle for more than idleClose to generate more idle-warns and cause a close.
        sleep(2.0f);

        try
        {
            _psession.commit();
            fail("Exception not thrown");
        }
        catch (Exception e)
        {
            _exception = e;
        }

        monitor(15, 0);

        check(IDLE);
    }

    public void testProducerIdleRollback() throws Exception
    {
        send(5, 0);
        // Now idle for more than idleClose to generate more idle-warns and cause a close.
        sleep(2.0f);
        try
        {
            _psession.rollback();
            fail("Exception not thrown");
        }
        catch (Exception e)
        {
            _exception = e;
        }

        monitor(10, 0);

        check(IDLE);
    }

    public void testProducerIdleRollbackTwice() throws Exception
    {
        send(5, 0);
        // Idle for less than idleClose to generate idle-warns
        sleep(1.0f);
        _psession.rollback();
        send(5, 0);
        // Now idle for more than idleClose to generate more idle-warns and cause a close.
        sleep(2.0f);
        try
        {
            _psession.rollback();
            fail("should fail");
        }
        catch (Exception e)
        {
            _exception = e;
        }

        monitor(15, 0);
        
        check(IDLE);
    }

    public void testProducerOpenCommit() throws Exception
    {
        try
        {
            // Sleep between sends to cause open warns and then cause a close.
            send(6, 0.5f);
            _psession.commit();
            fail("Exception not thrown");
        }
        catch (Exception e)
        {
            _exception = e;
        }

        monitor(0, 10);

        check(OPEN);
    }
    
    public void testProducerOpenCommitTwice() throws Exception
    {
        send(5, 0);
        sleep(1.0f);
        _psession.commit();

        try
        {
            // Now sleep between sends to cause open warns and then cause a close.
            send(6, 0.5f);
            _psession.commit();
            fail("Exception not thrown");
        }
        catch (Exception e)
        {
            _exception = e;
        }

        monitor(0, 10);
        
        check(OPEN);
    }

    public void testConsumerCommitClose() throws Exception
    {
        send(1, 0);

        _psession.commit();

        expect(1, 0);

        _csession.commit();

        sleep(3.0f);

        _csession.close();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }
    
    public void testConsumerIdleReceiveCommit() throws Exception
    {
        send(1, 0);

        _psession.commit();

        sleep(2.0f);

        expect(1, 0);

        sleep(2.0f);

        _csession.commit();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }

    public void testConsumerIdleCommit() throws Exception
    {
        send(1, 0);

        _psession.commit();

        expect(1, 0);

        sleep(2.0f);

        _csession.commit();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }
    
    public void testConsumerIdleRollback() throws Exception
    {
        send(1, 0);

        _psession.commit();

        expect(1, 0);

        sleep(2.0f);

        _csession.rollback();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }

    public void testConsumerOpenCommit() throws Exception
    {
        send(1, 0);

        _psession.commit();

        sleep(3.0f);

        _csession.commit();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }
    
    public void testConsumerOpenRollback() throws Exception
    {
        send(1, 0);
        
        _psession.commit();

        sleep(3.0f);

        _csession.rollback();

        assertEquals("Listener should not have received exception", 0, getNumberOfDeliveredExceptions());

        monitor(0, 0);
    }

    /**
     * Tests that sending an unroutable persistent message does not result in a long running store transaction [warning].
     */
    public void testTransactionCommittedOnNonRoutableQueuePersistentMessage() throws Exception
    {
        checkTransactionCommittedOnNonRoutableQueueMessage(DeliveryMode.PERSISTENT);
    }

    /**
     * Tests that sending an unroutable transient message does not result in a long running store transaction [warning].
     */
    public void testTransactionCommittedOnNonRoutableQueueTransientMessage() throws Exception
    {
        checkTransactionCommittedOnNonRoutableQueueMessage(DeliveryMode.NON_PERSISTENT);
    }

    private void checkTransactionCommittedOnNonRoutableQueueMessage(int deliveryMode) throws JMSException, Exception
    {
        Queue nonExisting = _psession.createQueue(getTestQueueName() + System.currentTimeMillis());
        MessageProducer producer = _psession.createProducer(nonExisting);
        Message message = _psession.createMessage();
        producer.send(message, deliveryMode, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
        _psession.commit();

        // give time to house keeping thread to log messages
        sleep(3f);
        monitor(0, 0);
    }
}
