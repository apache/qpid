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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.apache.qpid.server.store.Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL;
import static org.apache.qpid.server.store.Event.PERSISTENT_MESSAGE_SIZE_OVERFULL;
import junit.framework.TestCase;

public class EventManagerTest extends TestCase
{
    private EventManager _eventManager = new EventManager();
    private EventListener _mockListener = mock(EventListener.class);

    public void testEventListenerFires()
    {
        _eventManager.addEventListener(_mockListener, PERSISTENT_MESSAGE_SIZE_OVERFULL);
        _eventManager.notifyEvent(PERSISTENT_MESSAGE_SIZE_OVERFULL);
        verify(_mockListener).event(PERSISTENT_MESSAGE_SIZE_OVERFULL);
    }

    public void testEventListenerDoesntFire()
    {
        _eventManager.addEventListener(_mockListener, PERSISTENT_MESSAGE_SIZE_OVERFULL);
        _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
        verifyZeroInteractions(_mockListener);
    }

    public void testEventListenerFiresMultipleTimes()
    {
        _eventManager.addEventListener(_mockListener, PERSISTENT_MESSAGE_SIZE_OVERFULL);
        _eventManager.addEventListener(_mockListener, PERSISTENT_MESSAGE_SIZE_UNDERFULL);

        _eventManager.notifyEvent(PERSISTENT_MESSAGE_SIZE_OVERFULL);
        verify(_mockListener).event(PERSISTENT_MESSAGE_SIZE_OVERFULL);

        _eventManager.notifyEvent(PERSISTENT_MESSAGE_SIZE_UNDERFULL);
        verify(_mockListener).event(PERSISTENT_MESSAGE_SIZE_UNDERFULL);
    }

    public void testMultipleListenersFireForSameEvent()
    {
        final EventListener mockListener1 = mock(EventListener.class);
        final EventListener mockListener2 = mock(EventListener.class);

        _eventManager.addEventListener(mockListener1, PERSISTENT_MESSAGE_SIZE_OVERFULL);
        _eventManager.addEventListener(mockListener2, PERSISTENT_MESSAGE_SIZE_OVERFULL);
        _eventManager.notifyEvent(PERSISTENT_MESSAGE_SIZE_OVERFULL);

        verify(mockListener1).event(PERSISTENT_MESSAGE_SIZE_OVERFULL);
        verify(mockListener2).event(PERSISTENT_MESSAGE_SIZE_OVERFULL);
    }
}
