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
package org.apache.qpid.server.protocol;

import junit.framework.TestCase;
import org.apache.mina.common.IoSession;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.txn.MemoryTransactionManager;
import org.apache.qpid.server.txn.TransactionManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.messageStore.MemoryMessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;

import javax.management.JMException;


/**
 * Test class to test MBean operations for AMQMinaProtocolSession.
 */
public class AMQProtocolSessionMBeanTest   extends TestCase
{
    private MessageStore _messageStore = new MemoryMessageStore();
    private TransactionManager _txm = new MemoryTransactionManager();
    private AMQMinaProtocolSession _protocolSession;
    private AMQChannel _channel;
    private AMQProtocolSessionMBean _mbean;

    public void testChannels() throws Exception
    {
        // check the channel count is correct
        int channelCount = _mbean.channels().size();
        assertTrue(channelCount == 1);
        AMQQueue queue = new org.apache.qpid.server.queue.AMQQueue(new AMQShortString("testQueue_" + System.currentTimeMillis()),
                                                                   false,
                                                                   new AMQShortString("test"),
                                                                   true,
                                                                   _protocolSession.getVirtualHost());
        AMQChannel channel = new AMQChannel(_protocolSession,2,_txm, _messageStore, null);
        channel.setDefaultQueue(queue);
        _protocolSession.addChannel(channel);
        channelCount = _mbean.channels().size();
        assertTrue(channelCount == 2);

        // general properties test
        _mbean.setMaximumNumberOfChannels(1000L);
        assertTrue(_mbean.getMaximumNumberOfChannels() == 1000L);

        // check APIs
        AMQChannel channel3 = new AMQChannel(_protocolSession,3,_txm,  _messageStore, null);
        channel3.setLocalTransactional();
        _protocolSession.addChannel(channel3);
        _mbean.rollbackTransactions(2);
        _mbean.rollbackTransactions(3);
        _mbean.commitTransactions(2);
        _mbean.commitTransactions(3);

        // This should throw exception, because the channel does't exist
        try
        {
            _mbean.commitTransactions(4);
            fail();
        }
        catch (JMException ex)
        {
            System.out.println("expected exception is thrown :" + ex.getMessage());     
        }

        // check if closing of session works
        _protocolSession.addChannel(new AMQChannel(_protocolSession,5, _txm, _messageStore, null));
        _mbean.closeConnection();
        try
        {
            channelCount = _mbean.channels().size();
            assertTrue(channelCount == 0);
            // session is now closed so adding another channel should throw an exception
            _protocolSession.addChannel(new AMQChannel(_protocolSession,6, _txm, _messageStore, null));
            fail();
        }
        catch(AMQException ex)
        {
            System.out.println("expected exception is thrown :" + ex.getMessage());
        }
    }
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _protocolSession = new AMQMinaProtocolSession(new MockIoSession(),
                                                      appRegistry.getVirtualHostRegistry(),
                                                      new AMQCodecFactory(true),
                                                      null);
        _protocolSession.setVirtualHost(appRegistry.getVirtualHostRegistry().getVirtualHost("test"));
        _channel = new AMQChannel(_protocolSession,1, _txm, _messageStore, null);
        _protocolSession.addChannel(_channel);
        _mbean = (AMQProtocolSessionMBean)_protocolSession.getManagedObject();
    }
}
