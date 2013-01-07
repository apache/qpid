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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.txn.ServerTransaction;

public class TransactionTimeoutHelper
{
    private static final String OPEN_TRANSACTION_TIMEOUT_ERROR = "Open transaction timed out";
    private static final String IDLE_TRANSACTION_TIMEOUT_ERROR = "Idle transaction timed out";

    private final LogSubject _logSubject;

    private final CloseAction _closeAction;

    public TransactionTimeoutHelper(final LogSubject logSubject, final CloseAction closeAction)
    {
        _logSubject = logSubject;
        _closeAction = closeAction;
    }

    public void checkIdleOrOpenTimes(ServerTransaction transaction, long openWarn, long openClose, long idleWarn, long idleClose) throws AMQException
    {
        if (transaction.isTransactional())
        {
            final long transactionUpdateTime = transaction.getTransactionUpdateTime();
            if(transactionUpdateTime > 0)
            {
                long idleTime = System.currentTimeMillis() - transactionUpdateTime;
                boolean closed = logAndCloseIfNecessary(idleTime, idleWarn, idleClose, ChannelMessages.IDLE_TXN(idleTime), IDLE_TRANSACTION_TIMEOUT_ERROR);
                if (closed)
                {
                    return; // no point proceeding to check the open time
                }
            }

            final long transactionStartTime = transaction.getTransactionStartTime();
            if(transactionStartTime > 0)
            {
                long openTime = System.currentTimeMillis() - transactionStartTime;
                logAndCloseIfNecessary(openTime, openWarn, openClose, ChannelMessages.OPEN_TXN(openTime), OPEN_TRANSACTION_TIMEOUT_ERROR);
            }
        }
    }

    /**
     * @return true iff closeTimeout was exceeded
     */
    private boolean logAndCloseIfNecessary(final long timeSoFar,
            final long warnTimeout, final long closeTimeout,
            final LogMessage warnMessage, final String closeMessage) throws AMQException
    {
        if (isTimedOut(timeSoFar, warnTimeout))
        {
            LogActor logActor = CurrentActor.get();
            logActor.message(_logSubject, warnMessage);
        }

        if(isTimedOut(timeSoFar, closeTimeout))
        {
            _closeAction.doTimeoutAction(closeMessage);
            return true;
        }
        else
        {
            return false;
        }
    }

    private boolean isTimedOut(long timeSoFar, long timeout)
    {
        return timeout > 0L && timeSoFar > timeout;
    }

    public interface CloseAction
    {
        void doTimeoutAction(String reason) throws AMQException;
    }

}
