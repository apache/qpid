/*
 *
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
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.subjects.LogSubjectFormat;

import java.text.MessageFormat;

/**
 * Management actor to use in {@link MBeanInvocationHandlerImpl} to log all management operational logging.
 */
public class ManagementActor extends AbstractManagementActor
{
    private String _lastThreadName = null;

    /**
     * The logString to be used for logging
     */
    private String _logStringContainingPrincipal;

    /** @param rootLogger The RootLogger to use for this Actor */
    public ManagementActor(RootMessageLogger rootLogger)
    {
        super(rootLogger, UNKNOWN_PRINCIPAL);
    }

    public ManagementActor(RootMessageLogger rootLogger, String principalName)
    {
        super(rootLogger, principalName);
    }

    private synchronized String getAndCacheLogString()
    {
        String currentName = Thread.currentThread().getName();

        String actor;
        String logString = _logStringContainingPrincipal;

        // Record the last thread name so we don't have to recreate the log string
        if (_logStringContainingPrincipal == null || !currentName.equals(_lastThreadName))
        {
            _lastThreadName = currentName;
            String principalName = getPrincipalName();

            // Management Thread names have this format.
            // RMI TCP Connection(2)-169.24.29.116
            // This is true for both LocalAPI and JMX Connections
            // However to be defensive lets test.
            String[] split = currentName.split("\\(");
            if (split.length == 2)
            {
                String ip = currentName.split("-")[1];
                actor = MessageFormat.format(LogSubjectFormat.MANAGEMENT_FORMAT, principalName, ip);
            }
            else
            {
                // This is a precautionary path as it is not expected to occur
                // however rather than adjusting the thread name of the two
                // tests that will use this it is safer all round to do this.
                // it is also currently used by tests :
                // AMQBrokerManagerMBeanTest
                // ExchangeMBeanTest
                actor = currentName;
            }

            logString = "[" + actor + "] ";
            if(principalName != UNKNOWN_PRINCIPAL )
            {
                _logStringContainingPrincipal = logString;
            }

        }
        return logString;
    }

    public String getLogMessage()
    {
        return getAndCacheLogString();
    }
}
