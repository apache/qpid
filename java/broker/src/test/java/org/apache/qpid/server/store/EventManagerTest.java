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
import static org.apache.qpid.server.store.Event.AFTER_ACTIVATE;
import static org.apache.qpid.server.store.Event.BEFORE_ACTIVATE;
import junit.framework.TestCase;

public class EventManagerTest extends TestCase
{
    private EventManager _eventManager = new EventManager();
    private EventListener _mockListener = mock(EventListener.class);

    public void testEventListenerFires()
    {
        _eventManager.addEventListener(_mockListener, BEFORE_ACTIVATE);
        _eventManager.notifyEvent(BEFORE_ACTIVATE);
        verify(_mockListener).event(BEFORE_ACTIVATE);
    }

    public void testEventListenerDoesntFire()
    {
        _eventManager.addEventListener(_mockListener, BEFORE_ACTIVATE);
        _eventManager.notifyEvent(AFTER_ACTIVATE);
        verifyZeroInteractions(_mockListener);
    }

    public void testEventListenerFiresMulitpleTimes()
    {
        _eventManager.addEventListener(_mockListener, BEFORE_ACTIVATE);
        _eventManager.addEventListener(_mockListener, AFTER_ACTIVATE);

        _eventManager.notifyEvent(BEFORE_ACTIVATE);
        verify(_mockListener).event(BEFORE_ACTIVATE);

        _eventManager.notifyEvent(AFTER_ACTIVATE);
        verify(_mockListener).event(AFTER_ACTIVATE);
    }

    public void testMultipleListenersFireForSameEvent()
    {
        final EventListener mockListener1 = mock(EventListener.class);
        final EventListener mockListener2 = mock(EventListener.class);

        _eventManager.addEventListener(mockListener1, BEFORE_ACTIVATE);
        _eventManager.addEventListener(mockListener2, BEFORE_ACTIVATE);
        _eventManager.notifyEvent(BEFORE_ACTIVATE);

        verify(mockListener1).event(BEFORE_ACTIVATE);
        verify(mockListener2).event(BEFORE_ACTIVATE);
    }
}
