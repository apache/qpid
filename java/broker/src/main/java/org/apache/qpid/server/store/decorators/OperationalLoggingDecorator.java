/*
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
package org.apache.qpid.server.store.decorators;

import static org.apache.qpid.server.store.MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;

public class OperationalLoggingDecorator extends AbstractDecorator
{
    protected final LogSubject _logSubject;

    public OperationalLoggingDecorator(final MessageStore decoratedStore, LogSubject logSubject)
    {
        super(decoratedStore);
        _logSubject = logSubject;
    }

    @Override
    public void configureMessageStore(String name,
            MessageStoreRecoveryHandler messageRecoveryHandler,
            TransactionLogRecoveryHandler tlogRecoveryHandler,
            Configuration config) throws Exception
    {
        CurrentActor.get().message(_logSubject, MessageStoreMessages.CREATED());
        CurrentActor.get().message(_logSubject, TransactionLogMessages.CREATED());

        if (config != null && config.getString(ENVIRONMENT_PATH_PROPERTY) != null)
        {
            CurrentActor.get().message(_logSubject, MessageStoreMessages.STORE_LOCATION(config.getString(ENVIRONMENT_PATH_PROPERTY)));
        }

        _decoratedStore.configureMessageStore(name, messageRecoveryHandler,
                tlogRecoveryHandler, config);
    }

    @Override
    public void configureConfigStore(String name,
            ConfigurationRecoveryHandler recoveryHandler, Configuration config) throws Exception
    {
        CurrentActor.get().message(_logSubject, ConfigStoreMessages.CREATED());

        _decoratedStore.configureConfigStore(name, recoveryHandler, config);
    }

    @Override
    public void activate() throws Exception
    {
        CurrentActor.get().message(_logSubject, MessageStoreMessages.RECOVERY_START());
        _decoratedStore.activate();
        CurrentActor.get().message(_logSubject, MessageStoreMessages.RECOVERY_COMPLETE());
    }

    @Override
    public void close() throws Exception
    {
        CurrentActor.get().message(_logSubject,MessageStoreMessages.CLOSED());
        _decoratedStore.close();
    }
}
