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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.BindingRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.ExchangeRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.QueueRecoveryHandler;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.test.utils.QpidTestCase;

public abstract class MessageStoreTestCase extends QpidTestCase
{
    private ConfigurationRecoveryHandler _recoveryHandler;
    private QueueRecoveryHandler _queueRecoveryHandler;
    private ExchangeRecoveryHandler _exchangeRecoveryHandler;
    private BindingRecoveryHandler _bindingRecoveryHandler;
    private MessageStoreRecoveryHandler _messageStoreRecoveryHandler;
    private StoredMessageRecoveryHandler _storedMessageRecoveryHandler;
    private TransactionLogRecoveryHandler _logRecoveryHandler;
    private TransactionLogRecoveryHandler.QueueEntryRecoveryHandler _queueEntryRecoveryHandler;
    private TransactionLogRecoveryHandler.DtxRecordRecoveryHandler _dtxRecordRecoveryHandler;

    private MessageStore _store;
    private Configuration _storeConfiguration;

    public void setUp() throws Exception
    {
        super.setUp();

        _recoveryHandler = mock(ConfigurationRecoveryHandler.class);
        _queueRecoveryHandler = mock(QueueRecoveryHandler.class);
        _exchangeRecoveryHandler = mock(ExchangeRecoveryHandler.class);
        _bindingRecoveryHandler = mock(BindingRecoveryHandler.class);
        _storedMessageRecoveryHandler = mock(StoredMessageRecoveryHandler.class);
        _logRecoveryHandler = mock(TransactionLogRecoveryHandler.class);
        _messageStoreRecoveryHandler = mock(MessageStoreRecoveryHandler.class);
        _queueEntryRecoveryHandler = mock(TransactionLogRecoveryHandler.QueueEntryRecoveryHandler.class);
        _dtxRecordRecoveryHandler = mock(TransactionLogRecoveryHandler.DtxRecordRecoveryHandler.class);

        when(_messageStoreRecoveryHandler.begin()).thenReturn(_storedMessageRecoveryHandler);
        when(_recoveryHandler.begin(isA(MessageStore.class))).thenReturn(_exchangeRecoveryHandler);
        when(_exchangeRecoveryHandler.completeExchangeRecovery()).thenReturn(_queueRecoveryHandler);
        when(_queueRecoveryHandler.completeQueueRecovery()).thenReturn(_bindingRecoveryHandler);
        when(_logRecoveryHandler.begin(any(MessageStore.class))).thenReturn(_queueEntryRecoveryHandler);
        when(_queueEntryRecoveryHandler.completeQueueEntryRecovery()).thenReturn(_dtxRecordRecoveryHandler);

        _storeConfiguration = new PropertiesConfiguration();
        setUpStoreConfiguration(_storeConfiguration);

        _store = createMessageStore();
        ((DurableConfigurationStore)_store).configureConfigStore(getTestName(), _recoveryHandler, _storeConfiguration);
        _store.configureMessageStore(getTestName(), _messageStoreRecoveryHandler, _logRecoveryHandler, _storeConfiguration);
    }

    protected abstract void setUpStoreConfiguration(Configuration storeConfiguration) throws Exception;

    protected abstract MessageStore createMessageStore();

    public MessageStore getStore()
    {
        return _store;
    }

    public Configuration getStoreConfiguration()
    {
        return _storeConfiguration;
    }
}
