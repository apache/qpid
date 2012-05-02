package org.apache.qpid.server.store;

import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class OperationalLoggingListenerTest extends TestCase
{


    public static final String STORE_LOCATION = "The moon!";

    protected void setUp() throws Exception
    {
        super.setUp();
        
    }

    public void testOperationalLoggingWithStoreLocation() throws Exception
    {
        TestMessageStore messageStore = new TestMessageStore();
        LogSubject logSubject = LOG_SUBJECT;

        OperationalLoggingListener.listen(messageStore, logSubject);

        performTests(messageStore, true);

    }

    public void testOperationalLogging() throws Exception
    {
        TestMessageStore messageStore = new TestMessageStore();
        LogSubject logSubject = LOG_SUBJECT;

        OperationalLoggingListener.listen(messageStore, logSubject);

        performTests(messageStore, false);
    }

    private void performTests(TestMessageStore messageStore, boolean setStoreLocation)
    {
        final List<LogMessage> messages = new ArrayList<LogMessage>();

        CurrentActor.set(new TestActor(messages));

        if(setStoreLocation)
        {
            messageStore.setStoreLocation(STORE_LOCATION);
        }


        messageStore.attainState(State.CONFIGURING);
        assertEquals("Unexpected number of operational log messages on configuring", 1, messages.size());
        assertEquals(messages.remove(0).toString(), ConfigStoreMessages.CREATED().toString());

        messageStore.attainState(State.CONFIGURED);
        assertEquals("Unexpected number of operational log messages on CONFIGURED", setStoreLocation ? 3 : 2, messages.size());
        assertEquals(messages.remove(0).toString(), MessageStoreMessages.CREATED().toString());
        assertEquals(messages.remove(0).toString(), TransactionLogMessages.CREATED().toString());
        if(setStoreLocation)
        {
            assertEquals(messages.remove(0).toString(), MessageStoreMessages.STORE_LOCATION(STORE_LOCATION).toString());
        }

        messageStore.attainState(State.RECOVERING);
        assertEquals("Unexpected number of operational log messages on RECOVERING", 1, messages.size());
        assertEquals(messages.remove(0).toString(), MessageStoreMessages.RECOVERY_START().toString());


        messageStore.attainState(State.ACTIVE);
        assertEquals("Unexpected number of operational log messages on ACTIVE", 1, messages.size());
        assertEquals(messages.remove(0).toString(), MessageStoreMessages.RECOVERY_COMPLETE().toString());

        messageStore.attainState(State.CLOSING);
        assertEquals("Unexpected number of operational log messages on CLOSING", 0, messages.size());

        messageStore.attainState(State.CLOSED);
        assertEquals("Unexpected number of operational log messages on CLOSED", 1, messages.size());
        assertEquals(messages.remove(0).toString(), MessageStoreMessages.CLOSED().toString());
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        CurrentActor.remove();
    }


    private static final LogSubject LOG_SUBJECT = new LogSubject()
    {
        public String toLogString()
        {
            return "";
        }
    };

    private static final class TestMessageStore extends NullMessageStore
    {

        private final EventManager _eventManager = new EventManager();
        private final StateManager _stateManager = new StateManager(_eventManager);
        private String _storeLocation;

        public void attainState(State state)
        {
            _stateManager.attainState(state);
        }

        @Override
        public String getStoreLocation()
        {
            return _storeLocation;
        }

        public void setStoreLocation(String storeLocation)
        {
            _storeLocation = storeLocation;
        }

        @Override
        public void addEventListener(EventListener eventListener, Event... events)
        {
            _eventManager.addEventListener(eventListener, events);
        }
    }

    private static class TestActor implements LogActor
    {
        private final List<LogMessage> _messages;

        public TestActor(List<LogMessage> messages)
        {
            _messages = messages;
        }

        public void message(LogSubject subject, LogMessage message)
        {
            _messages.add(message);
        }

        public void message(LogMessage message)
        {
            _messages.add(message);
        }

        public RootMessageLogger getRootMessageLogger()
        {
            return null;
        }

        public String getLogMessage()
        {
            return null;
        }
    }
}
