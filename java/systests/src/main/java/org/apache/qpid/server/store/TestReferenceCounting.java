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
package org.apache.qpid.server.store;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.queue.AMQMessageHandle;

/**
 * Tests that reference counting works correctly with AMQMessage and the message store
 */
public class TestReferenceCounting extends TestCase
{
    private TestMemoryMessageStore _store;

    private StoreContext _storeContext = new StoreContext();


    protected void setUp() throws Exception
    {
        super.setUp();
        _store = new TestMemoryMessageStore();
    }

    /**
     * Check that when the reference count is decremented the message removes itself from the store
     */
    public void testMessageGetsRemoved() throws AMQException
    {
        ContentHeaderBody chb = createPersistentContentHeader();

        MessagePublishInfo info = new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return null;
            }

            public void setExchange(AMQShortString exchange)
            {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public boolean isImmediate()
            {
                return false;
            }

            public boolean isMandatory()
            {
                return false;
            }

            public AMQShortString getRoutingKey()
            {
                return null;
            }
        };


        final long messageId = _store.getNewMessageId();
        AMQMessageHandle messageHandle = (new MessageHandleFactory()).createMessageHandle(messageId, _store, true);
        messageHandle.setPublishAndContentHeaderBody(_storeContext,info, chb);
        AMQMessage message = new AMQMessage(messageHandle,
                                             _storeContext,info);

        message = message.takeReference();

        // we call routing complete to set up the handle
 //       message.routingComplete(_store, _storeContext, new MessageHandleFactory());


        assertEquals(1, _store.getMessageMetaDataMap().size());
        message.decrementReference(_storeContext);
        assertEquals(1, _store.getMessageMetaDataMap().size());
    }

    private ContentHeaderBody createPersistentContentHeader()
    {
        ContentHeaderBody chb = new ContentHeaderBody();
        BasicContentHeaderProperties bchp = new BasicContentHeaderProperties();
        bchp.setDeliveryMode((byte)2);
        chb.properties = bchp;
        return chb;
    }

    public void testMessageRemains() throws AMQException
    {

        MessagePublishInfo info = new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return null;
            }

            public void setExchange(AMQShortString exchange)
            {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public boolean isImmediate()
            {
                return false;
            }

            public boolean isMandatory()
            {
                return false;
            }

            public AMQShortString getRoutingKey()
            {
                return null;
            }
        };

        final Long messageId = _store.getNewMessageId();
        final ContentHeaderBody chb = createPersistentContentHeader();
        AMQMessageHandle messageHandle = (new MessageHandleFactory()).createMessageHandle(messageId, _store, true);
        messageHandle.setPublishAndContentHeaderBody(_storeContext,info,chb);
        AMQMessage message = new AMQMessage(messageHandle,
                                             _storeContext,
                                            info);
        
        
        message = message.takeReference();
        // we call routing complete to set up the handle
     //   message.routingComplete(_store, _storeContext, new MessageHandleFactory());



        assertEquals(1, _store.getMessageMetaDataMap().size());
        message = message.takeReference();
        message.decrementReference(_storeContext);
        assertEquals(1, _store.getMessageMetaDataMap().size());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TestReferenceCounting.class);
    }
}
