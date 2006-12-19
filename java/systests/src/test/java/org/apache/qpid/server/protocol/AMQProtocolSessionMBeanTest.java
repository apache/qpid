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
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.JMException;
import java.util.ArrayList;

/**
 * Test class to test MBean operations for AMQMinaProtocolSession.
 */
public class AMQProtocolSessionMBeanTest   extends TestCase
{
    private IoSession _mockIOSession;
    private MessageStore _messageStore = new SkeletonMessageStore();
    private AMQMinaProtocolSession _protocolSession;
    private AMQChannel _channel;
    private QueueRegistry _queueRegistry;
    private ExchangeRegistry _exchangeRegistry;
    private AMQProtocolSessionMBean _mbean;

    public void testChannels() throws Exception
    {
        // check the channel count is correct
        int channelCount = _mbean.channels().size();
        assertTrue(channelCount == 1);
        _protocolSession.addChannel(new AMQChannel(2, _messageStore, null));
        channelCount = _mbean.channels().size();
        assertTrue(channelCount == 2);

        // check the channel closing
        _mbean.closeChannel(1);
        TabularData channels = _mbean.channels();
        ArrayList<CompositeData> list = new ArrayList<CompositeData>(channels.values());
        channelCount = list.size();
        assertTrue(channelCount == 1);
        CompositeData channelData = list.get(0);
        assertEquals(channelData.get("Channel Id"), new Integer(2));

        // general properties test
        _mbean.setMaximumNumberOfChannels(1000L);
        assertTrue(_mbean.getMaximumNumberOfChannels() == 1000L);

        // check if the rollback and commit APIs
        AMQChannel channel3 = new AMQChannel(3, _messageStore, null);
        channel3.setTransactional(true);
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
        _protocolSession.addChannel(new AMQChannel(5, _messageStore, null));
        _mbean.closeConnection();
        try
        {
            _mbean.closeChannel(5);
            fail();
        }
        catch(JMException ex)
        {
            System.out.println("expected exception is thrown :" + ex.getMessage());
        }
    }
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _channel = new AMQChannel(1, _messageStore, null);
        _queueRegistry = new DefaultQueueRegistry();
        _exchangeRegistry = new DefaultExchangeRegistry(new DefaultExchangeFactory());
        _mockIOSession = new MockIoSession();
        _protocolSession = new AMQMinaProtocolSession(_mockIOSession, _queueRegistry, _exchangeRegistry, new AMQCodecFactory(true));
        _protocolSession.addChannel(_channel);
        _mbean = (AMQProtocolSessionMBean)_protocolSession.getManagedObject();
    }
}
