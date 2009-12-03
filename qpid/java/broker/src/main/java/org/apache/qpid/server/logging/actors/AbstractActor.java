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

import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;

/**
 * This AbstractActor is the basis for all the actors instances.
 *
 * It provides the common initialisation and message methods.
 *
 * It is abstract as it leaves the setting of _logString to the instances.
 *
 * The setting of the _logString is done so that the LogActor that the log
 * messages was sent to can be identified in the log output.
 *
 * When a log message is created it is written to the RootMessageLogger in the
 * format:
 *
 *     _logString [+ subject] + message
 *
 */
public abstract class AbstractActor implements LogActor
{
    protected String _logString;
    protected RootMessageLogger _rootLogger;

    public AbstractActor(RootMessageLogger rootLogger)
    {
        if(rootLogger == null)
        {
            throw new NullPointerException("RootMessageLogger cannot be null");
        }
        _rootLogger = rootLogger;
    }

    public void message(LogSubject subject, LogMessage message)
    {
        if (_rootLogger.isMessageEnabled(this, subject))
        {
            _rootLogger.rawMessage(_logString + String.valueOf(subject) + message);
        }
    }

    public void message(LogMessage message)
    {
        if (_rootLogger.isMessageEnabled(this))
        {
            _rootLogger.rawMessage(_logString + message);
        }
    }

    public RootMessageLogger getRootMessageLogger()
    {
        return _rootLogger;
    }

    public String toString()
    {
        return _logString;
    }

}
