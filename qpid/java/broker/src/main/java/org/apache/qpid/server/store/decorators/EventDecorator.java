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

import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.EventManager;
import org.apache.qpid.server.store.MessageStore;

public class EventDecorator extends AbstractDecorator
{
    protected final EventManager _eventManager;

    public EventDecorator(MessageStore store)
    {
        super(store);
        _eventManager = new EventManager();
    }

    @Override
    public void activate() throws Exception
    {
        _eventManager.notifyEvent(Event.BEFORE_ACTIVATE);
        _decoratedStore.activate();
        _eventManager.notifyEvent(Event.AFTER_ACTIVATE);
    }

    @Override
    public void close() throws Exception
    {
        _eventManager.notifyEvent(Event.BEFORE_CLOSE);
        _decoratedStore.close();
        _eventManager.notifyEvent(Event.AFTER_CLOSE);
    }

    @Override
    public void addEventListener(EventListener eventListener, Event event)
    {
        _eventManager.addEventListener(eventListener, event);
    }
}
