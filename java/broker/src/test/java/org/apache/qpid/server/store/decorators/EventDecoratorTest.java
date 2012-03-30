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

import static org.mockito.Mockito.*;

import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.decorators.EventDecorator;
import org.mockito.InOrder;

import junit.framework.TestCase;

public class EventDecoratorTest extends TestCase
{
    private MessageStore _mockStore = mock(MessageStore.class);
    private EventListener _mockListener = mock(EventListener.class);

    private EventDecorator _eventDecorator = new EventDecorator(_mockStore);
    private InOrder _orderMock = inOrder(_mockListener, _mockStore);

    public void testBeforeActivateDecoration() throws Exception
    {
        _eventDecorator.addEventListener(_mockListener, Event.BEFORE_ACTIVATE);
        _eventDecorator.activate();

        _orderMock.verify(_mockListener).event(Event.BEFORE_ACTIVATE);
        _orderMock.verify(_mockStore).activate();
    }

    public void testAfterActivateDecoration() throws Exception
    {
        _eventDecorator.addEventListener(_mockListener, Event.AFTER_ACTIVATE);
        _eventDecorator.activate();

        _orderMock.verify(_mockStore).activate();
        _orderMock.verify(_mockListener).event(Event.AFTER_ACTIVATE);
    }

    public void testBeforeAfterActivateDecoration() throws Exception
    {
        _eventDecorator.addEventListener(_mockListener, Event.BEFORE_ACTIVATE);
        _eventDecorator.addEventListener(_mockListener, Event.AFTER_ACTIVATE);
        _eventDecorator.activate();

        _orderMock.verify(_mockListener).event(Event.BEFORE_ACTIVATE);
        _orderMock.verify(_mockStore).activate();
        _orderMock.verify(_mockListener).event(Event.AFTER_ACTIVATE);
    }

    public void testBeforeAfterCloseDecoration() throws Exception
    {
        _eventDecorator.addEventListener(_mockListener, Event.BEFORE_CLOSE);
        _eventDecorator.addEventListener(_mockListener, Event.AFTER_CLOSE);
        _eventDecorator.close();

        _orderMock.verify(_mockListener).event(Event.BEFORE_CLOSE);
        _orderMock.verify(_mockStore).close();
        _orderMock.verify(_mockListener).event(Event.AFTER_CLOSE);
    }
}
