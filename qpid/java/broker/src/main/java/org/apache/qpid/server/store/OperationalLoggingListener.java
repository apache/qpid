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
package org.apache.qpid.server.store;

import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;

public class OperationalLoggingListener implements EventListener
{
    protected final LogSubject _logSubject;
    private MessageStore _store;

    private OperationalLoggingListener(final MessageStore store, LogSubject logSubject)
    {
        _logSubject = logSubject;
        store.addEventListener(this, Event.BEFORE_INIT, Event.AFTER_INIT, Event.BEFORE_ACTIVATE, Event.AFTER_ACTIVATE, Event.AFTER_CLOSE);
        _store = store;
    }

    public void event(Event event)
    {
        switch(event)
        {
            case BEFORE_INIT:
                CurrentActor.get().message(_logSubject, ConfigStoreMessages.CREATED());
                break;
            case AFTER_INIT:
                CurrentActor.get().message(_logSubject, MessageStoreMessages.CREATED());
                CurrentActor.get().message(_logSubject, TransactionLogMessages.CREATED());
                String storeLocation = _store.getStoreLocation();
                if (storeLocation != null)
                {
                    CurrentActor.get().message(_logSubject, MessageStoreMessages.STORE_LOCATION(storeLocation));
                }
                break;
            case BEFORE_ACTIVATE:
                CurrentActor.get().message(_logSubject, MessageStoreMessages.RECOVERY_START());
                break;
            case AFTER_ACTIVATE:
                CurrentActor.get().message(_logSubject, MessageStoreMessages.RECOVERY_COMPLETE());
                break;
            case AFTER_CLOSE:
                CurrentActor.get().message(_logSubject,MessageStoreMessages.CLOSED());
                break;
            
        }
    }

    public static void listen(final MessageStore store, LogSubject logSubject)
    {
        new OperationalLoggingListener(store, logSubject);
    }
}
