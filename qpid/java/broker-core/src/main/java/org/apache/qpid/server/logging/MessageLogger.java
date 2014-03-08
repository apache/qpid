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
public interface MessageLogger
{
    /**
     * Determine whether the MessageLogger is enabled
     * 
     * @return boolean true if enabled.
     */
    boolean isEnabled();

    /**
     * Determine if  the LogActor should be generating log messages.
     *
     * @param logHierarchy The log hierarchy for this request
     *
     * @return boolean true if the message should be logged.
     */
    boolean isMessageEnabled(String logHierarchy);

    void message(LogMessage message);

    void message(LogSubject subject, LogMessage message);



}