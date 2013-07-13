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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.apache.log4j.Logger;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.VirtualHost;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class SlowMessageStore implements MessageStore, DurableConfigurationStore
{
    private static final Logger _logger = Logger.getLogger(SlowMessageStore.class);
    private static final String DELAYS = "delays";
    private HashMap<String, Long> _preDelays = new HashMap<String, Long>();
    private HashMap<String, Long> _postDelays = new HashMap<String, Long>();
    private long _defaultDelay = 0L;
    private MessageStore _realStore = new MessageStoreCreator().createMessageStore("Memory");
    private DurableConfigurationStore _durableConfigurationStore = (DurableConfigurationStore) _realStore;
    private static final String PRE = "pre";
    private static final String POST = "post";
    private String DEFAULT_DELAY = "default";

    // ***** MessageStore Interface.

    public void configureConfigStore(String name,
                                     ConfigurationRecoveryHandler recoveryHandler,
                                     VirtualHost virtualHost) throws Exception
    {
        _logger.info("Starting SlowMessageStore on Virtualhost:" + name);

        Object delaysAttr = virtualHost.getAttribute("slowMessageStoreDelays");

        Map delays = (delaysAttr instanceof Map) ? (Map) delaysAttr : Collections.emptyMap();
        configureDelays(delays);

        final Object realStoreAttr = virtualHost.getAttribute("realStore");
        String messageStoreClass = realStoreAttr == null ? null : realStoreAttr.toString();

        if (delays.containsKey(DEFAULT_DELAY))
        {
            _defaultDelay = Long.parseLong(String.valueOf(delays.get(DEFAULT_DELAY)));
        }

        if (messageStoreClass != null)
        {
            Class<?> clazz = Class.forName(messageStoreClass);

            Object o = clazz.newInstance();

            if (!(o instanceof MessageStore))
            {
                throw new ClassCastException("Message store class must implement " + MessageStore.class + ". Class " + clazz +
                                             " does not.");
            }
            _realStore = (MessageStore) o;
            if(o instanceof DurableConfigurationStore)
            {
                _durableConfigurationStore = (DurableConfigurationStore)o;
            }
        }
        _durableConfigurationStore.configureConfigStore(name, recoveryHandler, virtualHost);

    }

    private void configureDelays(Map<Object, Object> config)
    {

        for(Map.Entry<Object, Object> entry : config.entrySet())
        {
            String key = String.valueOf(entry.getKey());
            if (key.startsWith(PRE))
            {
                _preDelays.put(key.substring(PRE.length()), Long.parseLong(String.valueOf(entry.getValue())));
            }
            else if (key.startsWith(POST))
            {
                _postDelays.put(key.substring(POST.length()), Long.parseLong(String.valueOf(entry.getValue())));
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


    public void configureMessageStore(String name,
                                      MessageStoreRecoveryHandler messageRecoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler) throws Exception
    {
        _realStore.configureMessageStore(name, messageRecoveryHandler, tlogRecoveryHandler);
    }

    public void close() throws Exception
    {
        doPreDelay("close");
        _realStore.close();
        doPostDelay("close");
    }

    public <M extends StorableMessageMetaData> StoredMessage<M> addMessage(M metaData)
    {
        return _realStore.addMessage(metaData);
    }


    @Override
    public void create(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException
    {
        doPreDelay("create");
        _durableConfigurationStore.create(id, type, attributes);
        doPostDelay("create");
    }

    @Override
    public void remove(UUID id, String type) throws AMQStoreException
    {
        doPreDelay("remove");
        _durableConfigurationStore.remove(id, type);
        doPostDelay("remove");
    }

    @Override
    public void update(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException
    {
        doPreDelay("update");
        _durableConfigurationStore.update(id, type, attributes);
        doPostDelay("update");
    }

    public Transaction newTransaction()
    {
        doPreDelay("beginTran");
        Transaction txn = new SlowTransaction(_realStore.newTransaction());
        doPostDelay("beginTran");
        return txn;
    }


    public boolean isPersistent()
    {
        return _realStore.isPersistent();
    }

    public void storeMessageHeader(Long messageNumber, ServerMessage message)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void storeContent(Long messageNumber, long offset, ByteBuffer body)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public ServerMessage getMessage(Long messageNumber)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private class SlowTransaction implements Transaction
    {
        private final Transaction _underlying;

        private SlowTransaction(Transaction underlying)
        {
            _underlying = underlying;
        }

        public void enqueueMessage(TransactionLogResource queue, EnqueableMessage message)
                throws AMQStoreException
        {
            doPreDelay("enqueueMessage");
            _underlying.enqueueMessage(queue, message);
            doPostDelay("enqueueMessage");
        }

        public void dequeueMessage(TransactionLogResource queue, EnqueableMessage message)
                throws AMQStoreException
        {
            doPreDelay("dequeueMessage");
            _underlying.dequeueMessage(queue, message);
            doPostDelay("dequeueMessage");
        }

        public void commitTran()
                throws AMQStoreException
        {
            doPreDelay("commitTran");
            _underlying.commitTran();
            doPostDelay("commitTran");
        }

        public StoreFuture commitTranAsync()
                throws AMQStoreException
        {
            doPreDelay("commitTran");
            StoreFuture future = _underlying.commitTranAsync();
            doPostDelay("commitTran");
            return future;
        }

        public void abortTran()
                throws AMQStoreException
        {
            doPreDelay("abortTran");
            _underlying.abortTran();
            doPostDelay("abortTran");
        }

        public void removeXid(long format, byte[] globalId, byte[] branchId) throws AMQStoreException
        {
            _underlying.removeXid(format, globalId, branchId);
        }

        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
                throws AMQStoreException
        {
            _underlying.recordXid(format, globalId, branchId, enqueues, dequeues);
        }
    }

    @Override
    public void activate() throws Exception
    {
       _realStore.activate();
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _realStore.addEventListener(eventListener, events);
    }

    @Override
    public String getStoreLocation()
    {
        return _realStore.getStoreLocation();
    }

    @Override
    public String getStoreType()
    {
        return "SLOW";
    }

    @Override
    public void onDelete()
    {
        _realStore.onDelete();
    }

}
