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

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ChannelMessages;

public class TransactionTimeoutHelper
{
    private static final Logger LOGGER = Logger.getLogger(TransactionTimeoutHelper.class);

    public static final String IDLE_TRANSACTION_ALERT = "IDLE TRANSACTION ALERT";
    public static final String OPEN_TRANSACTION_ALERT = "OPEN TRANSACTION ALERT";

    private final LogSubject _logSubject;

    public TransactionTimeoutHelper(final LogSubject logSubject)
    {
        _logSubject = logSubject;
    }

    public void logIfNecessary(final long timeSoFar, final long warnTimeout,
                               final LogMessage message, final String alternateLogPrefix)
    {
        if (isTimedOut(timeSoFar, warnTimeout))
        {
            LogActor logActor = CurrentActor.get();
            if(logActor.getRootMessageLogger().isMessageEnabled(logActor, _logSubject, message.getLogHierarchy()))
            {
                logActor.message(_logSubject, message);
            }
            else
            {
                LOGGER.warn(alternateLogPrefix + " " + _logSubject.toLogString() + " " + timeSoFar + " ms");
            }
        }
    }

    public boolean isTimedOut(long timeSoFar, long timeout)
    {
        return timeout > 0L && timeSoFar > timeout;
    }
}
