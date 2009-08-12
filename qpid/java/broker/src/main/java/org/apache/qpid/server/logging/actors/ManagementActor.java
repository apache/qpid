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

import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;

import java.text.MessageFormat;

/**
 * NOTE: This actor is not thread safe.
 *
 * Sharing of a ManagementActor instance between threads may result in an
 * incorrect actor value being logged.
 *
 * This is due to the fact that calls to message will dynamically query the
 * thread name and use that to set the log format during each message() call.
 *
 * This is currently not an issue as each MBean operation creates a new Actor
 * that is unique for each operation.
 */
public class ManagementActor extends AbstractActor
{
    String _lastThreadName = null;

    /**
     * LOG FORMAT for the ManagementActor,
     * Uses a MessageFormat call to insert the requried values according to
     * these indicies:
     *
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     */
    public static final String MANAGEMENT_FORMAT = "mng:{0}({1})";

    /** @param rootLogger The RootLogger to use for this Actor */
    public ManagementActor(RootMessageLogger rootLogger)
    {
        super(rootLogger);

    }

    private void updateLogString()
    {
        String currentName = Thread.currentThread().getName();

        // Record the last thread name so we don't have to recreate the log string
        if (!currentName.equals(_lastThreadName))
        {
            _lastThreadName = currentName;

            System.err.println(currentName);
            // Management Threads have this format.
            //RMI TCP Connection(2)-169.24.29.116
            String connectionID = currentName.split("\\(")[1].split("\\)")[0];
            String ip = currentName.split("-")[1];

            _logString = "[" + MessageFormat.format(MANAGEMENT_FORMAT,
                                                    connectionID,
                                                    ip)
                         + "] ";
        }
    }

    @Override
    public void message(LogSubject subject, LogMessage message)
    {
        updateLogString();
        super.message(subject, message);
    }

    @Override
    public void message(LogMessage message)
    {
        updateLogString();
        super.message(message);
    }

}
