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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.MessageStoreFactory;

public class SlowMessageStore implements MessageStore, DurableConfigurationStore
{
    private static final Logger _logger = Logger.getLogger(SlowMessageStore.class);

    public static final String TYPE = "SLOW";
    public static final String DELAYS = "delays";
    public static final String REAL_STORE = "realStore";

    private static final String DEFAULT_DELAY = "default";
    private static final String PRE = "pre";
    private static final String POST = "post";

    private HashMap<String, Long> _preDelays = new HashMap<String, Long>();
    private HashMap<String, Long> _postDelays = new HashMap<String, Long>();
    private long _defaultDelay = 0L;
    private MessageStore _realStore = null;
    private DurableConfigurationStore _durableConfigurationStore = null;

    private Map<EventListener, Event[]> _eventListeners = new ConcurrentHashMap<EventListener, Event[]>();

    // ***** MessageStore Interface.

    @Override
    public void configureConfigStore(VirtualHost virtualHost, ConfigurationRecoveryHandler recoveryHandler)
    {
        _logger.info("Starting SlowMessageStore on Virtualhost:" + virtualHost.getName());

        Map<String, Object> messageStoreSettings = virtualHost.getMessageStoreSettings();
        Object delaysAttr = messageStoreSettings.get(DELAYS);

        @SuppressWarnings({ "unchecked" })
        Map<String,Object> delays = (delaysAttr instanceof Map) ? (Map<String,Object>) delaysAttr : Collections.<String,Object>emptyMap();
        configureDelays(delays);

        final Object realStoreAttr = messageStoreSettings.get(REAL_STORE);
        String messageStoreType = realStoreAttr == null ? MemoryMessageStore.TYPE : realStoreAttr.toString();

        if (delays.containsKey(DEFAULT_DELAY))
        {
            _defaultDelay = Long.parseLong(String.valueOf(delays.get(DEFAULT_DELAY)));
        }

        _realStore = MessageStoreFactory.FACTORY_LOADER.get(messageStoreType).createMessageStore();

        if (!_eventListeners.isEmpty())
        {
            for (Iterator<Map.Entry<EventListener, Event[]>> it = _eventListeners.entrySet().iterator(); it.hasNext();)
            {
                Map.Entry<EventListener, Event[]> entry = it.next();
                _realStore.addEventListener(entry.getKey(), entry.getValue());
                it.remove();
            }
        }

        if (_realStore instanceof DurableConfigurationStore)
        {
            _durableConfigurationStore = (DurableConfigurationStore)_realStore;
            _durableConfigurationStore.configureConfigStore(virtualHost, recoveryHandler);
        }

    }

    private void configureDelays(Map<String, Object> delays)
    {

        for(Map.Entry<String, Object> entry : delays.entrySet())
        {
            String key = entry.getKey();
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

    @Override
    public void configureMessageStore(VirtualHost virtualHost, MessageStoreRecoveryHandler messageRecoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler)
    {
        _realStore.configureMessageStore(virtualHost, messageRecoveryHandler, tlogRecoveryHandler);
    }

    @Override
    public void close()
    {
        doPreDelay("close");
        _realStore.close();
        doPostDelay("close");
    }

    @Override
    public <M extends StorableMessageMetaData> StoredMessage<M> addMessage(M metaData)
    {
        return _realStore.addMessage(metaData);
    }

    @Override
    public void create(UUID id, String type, Map<String, Object> attributes) throws StoreException
    {
        doPreDelay("create");
        _durableConfigurationStore.create(id, type, attributes);
        doPostDelay("create");
    }

    @Override
    public void remove(UUID id, String type) throws StoreException
    {
        doPreDelay("remove");
        _durableConfigurationStore.remove(id, type);
        doPostDelay("remove");
    }

    @Override
    public UUID[] removeConfiguredObjects(final UUID... objects) throws StoreException
    {
        doPreDelay("remove");
        UUID[] removed = _durableConfigurationStore.removeConfiguredObjects(objects);
        doPostDelay("remove");
        return removed;
    }

    @Override
    public void update(UUID id, String type, Map<String, Object> attributes) throws StoreException
    {
        doPreDelay("update");
        _durableConfigurationStore.update(id, type, attributes);
        doPostDelay("update");
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws StoreException
    {
        doPreDelay("update");
        _durableConfigurationStore.update(createIfNecessary, records);
        doPostDelay("update");
    }

    @Override
    public Transaction newTransaction()
    {
        doPreDelay("beginTran");
        Transaction txn = new SlowTransaction(_realStore.newTransaction());
        doPostDelay("beginTran");
        return txn;
    }

    @Override
    public boolean isPersistent()
    {
        return _realStore.isPersistent();
    }

    private class SlowTransaction implements Transaction
    {
        private final Transaction _underlying;

        private SlowTransaction(Transaction underlying)
        {
            _underlying = underlying;
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            doPreDelay("enqueueMessage");
            _underlying.enqueueMessage(queue, message);
            doPostDelay("enqueueMessage");
        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            doPreDelay("dequeueMessage");
            _underlying.dequeueMessage(queue, message);
            doPostDelay("dequeueMessage");
        }

        @Override
        public void commitTran()
        {
            doPreDelay("commitTran");
            _underlying.commitTran();
            doPostDelay("commitTran");
        }

        @Override
        public StoreFuture commitTranAsync()
        {
            doPreDelay("commitTran");
            StoreFuture future = _underlying.commitTranAsync();
            doPostDelay("commitTran");
            return future;
        }

        @Override
        public void abortTran()
        {
            doPreDelay("abortTran");
            _underlying.abortTran();
            doPostDelay("abortTran");
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId)
        {
            _underlying.removeXid(format, globalId, branchId);
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
        {
            _underlying.recordXid(format, globalId, branchId, enqueues, dequeues);
        }
    }

    @Override
    public void activate()
    {
       _realStore.activate();
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        if (_realStore == null)
        {
            _eventListeners .put(eventListener, events);
        }
        else
        {
            _realStore.addEventListener(eventListener, events);
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _realStore.getStoreLocation();
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }

    @Override
    public void onDelete()
    {
        _realStore.onDelete();
    }

}
