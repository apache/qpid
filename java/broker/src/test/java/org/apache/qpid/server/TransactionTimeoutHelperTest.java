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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.test.utils.QpidTestCase;

public class TransactionTimeoutHelperTest extends QpidTestCase
{
    private final LogMessage _logMessage = mock(LogMessage.class);
    private final LogActor _logActor = mock(LogActor.class);
    private final LogSubject _logSubject = mock(LogSubject.class);
    private TransactionTimeoutHelper _transactionTimeoutHelper;
    private RootMessageLogger _rootMessageLogger;

    public void testLogIfNecessary()
    {
        _transactionTimeoutHelper.logIfNecessary(99, 100, _logMessage, "");
        verifyZeroInteractions(_logActor, _logMessage);

        _transactionTimeoutHelper.logIfNecessary(101, 100, _logMessage, "");
        verify(_logActor).message(_logSubject, _logMessage);
    }

    public void testLogIfNecessaryWhenOperationalLoggingDisabled()
    {
        //disable the operational logging
        when(_rootMessageLogger.isMessageEnabled(
            same(_logActor), any(LogSubject.class), any(String.class)))
            .thenReturn(false);

        //verify the actor is never asked to log a message
        _transactionTimeoutHelper.logIfNecessary(101, 100, _logMessage, "");
        verify(_logActor, never()).message(any(LogMessage.class));
        verify(_logActor, never()).message(any(LogSubject.class), any(LogMessage.class));
    }

    public void testIsTimedOut()
    {
        assertFalse("Shouldn't have timed out", _transactionTimeoutHelper.isTimedOut(199,200));
        assertTrue("Should have timed out", _transactionTimeoutHelper.isTimedOut(201,200));
    }

    /**
     * If TransactionTimeout is disabled, the timeout will be 0. This test verifies
     * that the helper methods respond negatively in this scenario.
     */
    public void testTransactionTimeoutDisabled()
    {
        assertFalse("Shouldn't have timed out", _transactionTimeoutHelper.isTimedOut(201,0));

        _transactionTimeoutHelper.logIfNecessary(99, 0, _logMessage, "");
        verifyZeroInteractions(_logActor, _logMessage);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        CurrentActor.set(_logActor);

        _rootMessageLogger = mock(RootMessageLogger.class);
        when(_logActor.getRootMessageLogger()).thenReturn(_rootMessageLogger);

        when(_rootMessageLogger.isMessageEnabled(
                same(_logActor), any(LogSubject.class), any(String.class)))
                .thenReturn(true);

        _transactionTimeoutHelper = new TransactionTimeoutHelper(_logSubject);
    }

}
