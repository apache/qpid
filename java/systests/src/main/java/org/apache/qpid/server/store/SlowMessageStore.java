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

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;

import java.util.HashMap;
import java.util.Iterator;

public class SlowMessageStore implements MessageStore
{
    private static final Logger _logger = Logger.getLogger(SlowMessageStore.class);
    private static final String DELAYS = "delays";
    private HashMap<String, Long> _preDelays = new HashMap<String, Long>();
    private HashMap<String, Long> _postDelays = new HashMap<String, Long>();
    private long _defaultDelay = 0L;
    private MessageStore _realStore = new MemoryMessageStore();
    private static final String PRE = "pre";
    private static final String POST = "post";
    private String DEFAULT_DELAY = "default";

    public void configure(VirtualHost virtualHost, String base, Configuration config) throws Exception
    {
        _logger.info("Starting SlowMessageStore on Virtualhost:" + virtualHost.getName());
        Configuration delays = config.subset(base + "." + DELAYS);

        configureDelays(delays);

        String messageStoreClass = config.getString(base + ".store.class");

        if (delays.containsKey(DEFAULT_DELAY))
        {
            _defaultDelay = delays.getLong(DEFAULT_DELAY);
        }

        if (messageStoreClass != null)
        {
            Class clazz = Class.forName(messageStoreClass);

            Object o = clazz.newInstance();

            if (!(o instanceof MessageStore))
            {
                throw new ClassCastException("Message store class must implement " + MessageStore.class + ". Class " + clazz +
                                             " does not.");
            }
            _realStore = (MessageStore) o;
            _realStore.configure(virtualHost, base + ".store", config);
        }
        else
        {
            _realStore.configure(virtualHost, base + ".store", config);
        }
    }

    private void configureDelays(Configuration config)
    {
        Iterator delays = config.getKeys();

        while (delays.hasNext())
        {
            String key = (String) delays.next();
            if (key.endsWith(PRE))
            {
                _preDelays.put(key.substring(0, key.length() - PRE.length() - 1), config.getLong(key));
            }
            else if (key.endsWith(POST))
            {
                _postDelays.put(key.substring(0, key.length() - POST.length() - 1), config.getLong(key));
            }
        }
    }

    private void doPostDelay(String method)
    {
        long delay = lookupDelay(_postDelays, method);
        doDelay(delay);
    }

    private void doPreDelay(String method)
    {
        long delay = lookupDelay(_preDelays, method);
        doDelay(delay);
    }

    private long lookupDelay(HashMap<String, Long> delays, String method)
    {
        Long delay = delays.get(method);
        return (delay == null) ? _defaultDelay : delay;
    }

    private void doDelay(long delay)
    {
        if (delay > 0)
        {
            long start = System.nanoTime();
            try
            {

                Thread.sleep(delay);
            }
            catch (InterruptedException e)
            {
                _logger.warn("Interrupted : " + e);
            }

            long slept = (System.nanoTime() - start) / 1000000;
            
            if (slept >= delay)
            {
                _logger.info("Done sleep for:" + slept+":"+delay);
            }
            else
            {
                _logger.info("Only sleep for:" + slept + " re-sleeping");
                doDelay(delay - slept);
            }
        }
    }

    // ***** MessageStore Interface.

    public void close() throws Exception
    {
        doPreDelay("close");
        _realStore.close();
        doPostDelay("close");
    }

    public void removeMessage(StoreContext storeContext, Long messageId) throws AMQException
    {
        doPreDelay("removeMessage");
        _realStore.removeMessage(storeContext, messageId);
        doPostDelay("removeMessage");
    }

    public void createExchange(Exchange exchange) throws AMQException
    {
        doPreDelay("createExchange");
        _realStore.createExchange(exchange);
        doPostDelay("createExchange");
    }

    public void removeExchange(Exchange exchange) throws AMQException
    {
        doPreDelay("removeExchange");
        _realStore.removeExchange(exchange);
        doPostDelay("removeExchange");
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        doPreDelay("bindQueue");
        _realStore.bindQueue(exchange, routingKey, queue, args);
        doPostDelay("bindQueue");
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        doPreDelay("unbindQueue");
        _realStore.unbindQueue(exchange, routingKey, queue, args);
        doPostDelay("unbindQueue");
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
        createQueue(queue, null);
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
    {
        doPreDelay("createQueue");
        _realStore.createQueue(queue, arguments);
        doPostDelay("createQueue");
    }

    public void removeQueue(AMQQueue queue) throws AMQException
    {
        doPreDelay("removeQueue");
        _realStore.removeQueue(queue);
        doPostDelay("removeQueue");
    }

    public void enqueueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        doPreDelay("enqueueMessage");
        _realStore.enqueueMessage(context, queue, messageId);
        doPostDelay("enqueueMessage");
    }

    public void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        doPreDelay("dequeueMessage");
        _realStore.dequeueMessage(context, queue, messageId);
        doPostDelay("dequeueMessage");
    }

    public void beginTran(StoreContext context) throws AMQException
    {
        doPreDelay("beginTran");
        _realStore.beginTran(context);
        doPostDelay("beginTran");
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        doPreDelay("commitTran");
        _realStore.commitTran(context);
        doPostDelay("commitTran");
    }

    public void abortTran(StoreContext context) throws AMQException
    {
        doPreDelay("abortTran");
        _realStore.abortTran(context);
        doPostDelay("abortTran");
    }

    public boolean inTran(StoreContext context)
    {
        doPreDelay("inTran");
        boolean b = _realStore.inTran(context);
        doPostDelay("inTran");
        return b;
    }

    public Long getNewMessageId()
    {
        doPreDelay("getNewMessageId");
        Long l = _realStore.getNewMessageId();
        doPostDelay("getNewMessageId");
        return l;
    }

    public void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody) throws AMQException
    {
        doPreDelay("storeContentBodyChunk");
        _realStore.storeContentBodyChunk(context, messageId, index, contentBody, lastContentBody);
        doPostDelay("storeContentBodyChunk");
    }

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException
    {
        doPreDelay("storeMessageMetaData");
        _realStore.storeMessageMetaData(context, messageId, messageMetaData);
        doPostDelay("storeMessageMetaData");
    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException
    {
        doPreDelay("getMessageMetaData");
        MessageMetaData mmd = _realStore.getMessageMetaData(context, messageId);
        doPostDelay("getMessageMetaData");
        return mmd;
    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException
    {
        doPreDelay("getContentBodyChunk");
        ContentChunk c = _realStore.getContentBodyChunk(context, messageId, index);
        doPostDelay("getContentBodyChunk");
        return c;
    }

    public boolean isPersistent()
    {
        return _realStore.isPersistent();
    }

}
