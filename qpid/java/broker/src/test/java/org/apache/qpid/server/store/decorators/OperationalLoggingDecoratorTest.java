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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.apache.qpid.server.store.MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY;

import junit.framework.TestCase;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.decorators.OperationalLoggingDecorator;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

public class OperationalLoggingDecoratorTest extends TestCase
{
    private MessageStore _messageStore = mock(MessageStore.class);
    private LogActor _mockActor = mock(LogActor.class);
    private LogSubject _mockLogSubject = mock(LogSubject.class);
    private OperationalLoggingDecorator _operationalLoggingDecorator = new OperationalLoggingDecorator(_messageStore, _mockLogSubject);
    private InOrder _inOrder = inOrder(_mockActor, _messageStore);

    protected void setUp() throws Exception
    {
        super.setUp();
        CurrentActor.set(_mockActor);
    }

    public void testConfigureMessageStore() throws Exception
    {
        _operationalLoggingDecorator.configureMessageStore(null,null,null,null);

        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("MST-1001 : Created"));
        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("TXN-1001 : Created"));
        _inOrder.verify(_messageStore).configureMessageStore(anyString(), any(MessageStoreRecoveryHandler.class), any(TransactionLogRecoveryHandler.class), any(Configuration.class));
    }

    public void testConfigureMessageStoreWithStoreLocation() throws Exception
    {
        final String storeLocation = "/my/store/location";
        Configuration mockConfig = mock(Configuration.class);
        when(mockConfig.getString(ENVIRONMENT_PATH_PROPERTY)).thenReturn(storeLocation);

        _operationalLoggingDecorator.configureMessageStore(null,null,null, mockConfig);

        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("MST-1001 : Created"));
        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("TXN-1001 : Created"));
        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("MST-1002 : Store location : " + storeLocation));
        _inOrder.verify(_messageStore).configureMessageStore(anyString(), any(MessageStoreRecoveryHandler.class), any(TransactionLogRecoveryHandler.class), any(Configuration.class));
    }

    public void testConfigureConfigStore() throws Exception
    {
        _operationalLoggingDecorator.configureConfigStore(null,null,null);

        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("CFG-1001 : Created"));
        _inOrder.verify(_messageStore).configureConfigStore(anyString(), any(ConfigurationRecoveryHandler.class), any(Configuration.class));
    }

    public void testActivate() throws Exception
    {
        _operationalLoggingDecorator.activate();

        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("MST-1004 : Recovery Start"));
        _inOrder.verify(_messageStore).activate();
        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("MST-1006 : Recovery Complete"));
    }

    public void testClose() throws Exception
    {
        _operationalLoggingDecorator.close();

        _inOrder.verify(_mockActor).message(eq(_mockLogSubject), matchesLogMessage("MST-1003 : Closed"));
        _inOrder.verify(_messageStore).close();
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        CurrentActor.remove();
    }

    private LogMessage matchesLogMessage(String expectedLogMessage)
    {
        return argThat(new LogMessageArgumentMatcher(expectedLogMessage));
    }

    private final class LogMessageArgumentMatcher extends ArgumentMatcher<LogMessage>
    {
        private final String _expectedText;
        private String _description = null;
;
        public LogMessageArgumentMatcher(String _expectedLogMessage)
        {
            this._expectedText = _expectedLogMessage;
        }

        @Override
        public boolean matches(Object item)
        {
            LogMessage logMessage = (LogMessage) item;
            final String actualText = logMessage.toString();
            if (actualText.equals(_expectedText))
            {
                return true;
            }
            else
            {
                _description  = "Expected <" + _expectedText + "> but got <" + actualText + ">";
                return false;
            }
        }

        @Override
        public void describeTo(Description description)
        {
            if (description != null)
            {
                description.appendText(" : "+ _description);
            }
        }
    }
}