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
 */
package org.apache.qpid.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.qpid.server.logging.messages.ChannelMessages.IDLE_TXN_LOG_HIERARCHY;
import static org.apache.qpid.server.logging.messages.ChannelMessages.OPEN_TXN_LOG_HIERARCHY;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.TransactionTimeoutHelper.CloseAction;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.test.utils.QpidTestCase;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;

public class TransactionTimeoutHelperTest extends QpidTestCase
{
    private final LogActor _logActor = mock(LogActor.class);
    private final LogSubject _logSubject = mock(LogSubject.class);
    private final ServerTransaction _transaction = mock(ServerTransaction.class);
    private final CloseAction _closeAction = mock(CloseAction.class);
    private TransactionTimeoutHelper _transactionTimeoutHelper;
    private long _now;

    public void testNotTransactional() throws Exception
    {
        when(_transaction.isTransactional()).thenReturn(false);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, 5, 10, 5, 10);

        verifyZeroInteractions(_logActor, _closeAction);
    }

    public void testOpenTransactionProducesWarningOnly() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);

        configureMockTransaction(sixtyOneSecondsAgo, sixtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, SECONDS.toMillis(30), 0, 0, 0);

        verify(_logActor).message(same(_logSubject), isLogMessage(OPEN_TXN_LOG_HIERARCHY, "CHN-1007 : Open Transaction : 61,\\d{3} ms"));
        verifyZeroInteractions(_closeAction);
    }

    public void testOpenTransactionProducesTimeoutActionOnly() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);

        configureMockTransaction(sixtyOneSecondsAgo, sixtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, 0, SECONDS.toMillis(30), 0, 0);

        verify(_closeAction).doTimeoutAction("Open transaction timed out");
        verifyZeroInteractions(_logActor);
    }

    public void testOpenTransactionProducesWarningAndTimeoutAction() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);

        configureMockTransaction(sixtyOneSecondsAgo, sixtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, SECONDS.toMillis(15), SECONDS.toMillis(30), 0, 0);

        verify(_logActor).message(same(_logSubject), isLogMessage(OPEN_TXN_LOG_HIERARCHY, "CHN-1007 : Open Transaction : 61,\\d{3} ms"));
        verify(_closeAction).doTimeoutAction("Open transaction timed out");
    }

    public void testIdleTransactionProducesWarningOnly() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);
        final long thirtyOneSecondsAgo = _now - SECONDS.toMillis(31);

        configureMockTransaction(sixtyOneSecondsAgo, thirtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, 0, 0, SECONDS.toMillis(30), 0);

        verify(_logActor).message(same(_logSubject), isLogMessage(IDLE_TXN_LOG_HIERARCHY, "CHN-1008 : Idle Transaction : 31,\\d{3} ms"));
        verifyZeroInteractions(_closeAction);
    }

    public void testIdleTransactionProducesTimeoutActionOnly() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);
        final long thirtyOneSecondsAgo = _now - SECONDS.toMillis(31);

        configureMockTransaction(sixtyOneSecondsAgo, thirtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, 0, 0, 0, SECONDS.toMillis(30));

        verify(_closeAction).doTimeoutAction("Idle transaction timed out");
        verifyZeroInteractions(_logActor);
    }

    public void testIdleTransactionProducesWarningAndTimeoutAction() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);
        final long thirtyOneSecondsAgo = _now - SECONDS.toMillis(31);

        configureMockTransaction(sixtyOneSecondsAgo, thirtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, 0, 0, SECONDS.toMillis(15), SECONDS.toMillis(30));

        verify(_logActor).message(same(_logSubject), isLogMessage(IDLE_TXN_LOG_HIERARCHY, "CHN-1008 : Idle Transaction : 31,\\d{3} ms"));
        verify(_closeAction).doTimeoutAction("Idle transaction timed out");
    }

    public void testIdleAndOpenWarnings() throws Exception
    {
        final long sixtyOneSecondsAgo = _now - SECONDS.toMillis(61);
        final long thirtyOneSecondsAgo = _now - SECONDS.toMillis(31);

        configureMockTransaction(sixtyOneSecondsAgo, thirtyOneSecondsAgo);

        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, SECONDS.toMillis(60), 0, SECONDS.toMillis(30), 0);

        verify(_logActor).message(same(_logSubject), isLogMessage(IDLE_TXN_LOG_HIERARCHY, "CHN-1008 : Idle Transaction : 31,\\d{3} ms"));
        verify(_logActor).message(same(_logSubject), isLogMessage(OPEN_TXN_LOG_HIERARCHY, "CHN-1007 : Open Transaction : 61,\\d{3} ms"));
        verifyZeroInteractions(_closeAction);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        CurrentActor.set(_logActor);

        _transactionTimeoutHelper = new TransactionTimeoutHelper(_logSubject, _closeAction);
        _now = System.currentTimeMillis();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    private void configureMockTransaction(final long startTime, final long updateTime)
    {
        when(_transaction.isTransactional()).thenReturn(true);
        when(_transaction.getTransactionStartTime()).thenReturn(startTime);
        when(_transaction.getTransactionUpdateTime()).thenReturn(updateTime);
    }

    private LogMessage isLogMessage(String expectedLogHierarchy, String expectedText)
    {
        return argThat(new IsLogMessage(expectedLogHierarchy, expectedText));
    }

    class IsLogMessage extends ArgumentMatcher<LogMessage>
    {
        private final String _expectedLogHierarchy;
        private final String _expectedLogMessageMatches;
        private String _hierarchyMatchesFailure;
        private String _logMessageMatchesFailure;

        public IsLogMessage(String expectedLogHierarchy, String expectedLogMessageMatches)
        {
            _expectedLogHierarchy = expectedLogHierarchy;
            _expectedLogMessageMatches = expectedLogMessageMatches;
        }

        public boolean matches(Object arg)
        {
            LogMessage logMessage = (LogMessage)arg;

            boolean hierarchyMatches = logMessage.getLogHierarchy().equals(_expectedLogHierarchy);
            boolean logMessageMatches = logMessage.toString().matches(_expectedLogMessageMatches);

            if (!hierarchyMatches)
            {
                _hierarchyMatchesFailure = "LogHierarchy does not match. Expected " + _expectedLogHierarchy + " actual " + logMessage.getLogHierarchy();
            }

            if (!logMessageMatches)
            {
                _logMessageMatchesFailure = "LogMessage does not match. Expected " + _expectedLogMessageMatches + " actual " + logMessage.toString();
            }

            return hierarchyMatches && logMessageMatches;
        }

        @Override
        public void describeTo(Description description)
        {
            if (_hierarchyMatchesFailure != null)
            {
                description.appendText(_hierarchyMatchesFailure);
            }
            if (_logMessageMatchesFailure != null)
            {
                description.appendText(_logMessageMatchesFailure);
            }
        }
    }
}
