/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.logging;

/**
 * The RootMessageLogger is used by the LogActors to query if
 * logging is enabled for the requested message and to provide the actual
 * message that should be logged.
 */
public interface RootMessageLogger
{
    /**
     * Determine whether the MessageLogger is enabled
     * 
     * @return boolean true if enabled.
     */
    boolean isEnabled();
    
    /**
     * Determine if the LogSubject and the LogActor should be
     * generating log messages.
     * @param actor   The actor requesting the logging
     * @param subject The subject of this log request
     * @param logHierarchy The log hierarchy for this request
     *
     * @return boolean true if the message should be logged.
     */
    boolean isMessageEnabled(LogActor actor, LogSubject subject, String logHierarchy);

    /**
     * Determine if  the LogActor should be generating log messages.
     *
     * @param actor   The actor requesting the logging
     * @param logHierarchy The log hierarchy for this request
     *
     * @return boolean true if the message should be logged.
     */
    boolean isMessageEnabled(LogActor actor, String logHierarchy);

    /**
     * Log the raw message to the configured logger.
     *
     * @param message   The message to log
     * @param logHierarchy The log hierarchy for this request
     */
    public void rawMessage(String message, String logHierarchy);

    /**
     * Log the raw message to the configured logger.
     * Along with a formated stack trace from the Throwable.
     *
     * @param message   The message to log
     * @param throwable Optional Throwable that should provide stact trace
     * @param logHierarchy The log hierarchy for this request
     */
    void rawMessage(String message, Throwable throwable, String logHierarchy);
}