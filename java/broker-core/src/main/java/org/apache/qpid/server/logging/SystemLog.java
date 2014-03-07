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
package org.apache.qpid.server.logging;

public class SystemLog
{
    private static RootMessageLogger _rootMessageLogger = new NullRootMessageLogger();

    /**
     * Logs the specified LogMessage about the LogSubject
     *
     * @param subject The subject that is being logged
     * @param message The message to log
     */
    public static void message(LogSubject subject, LogMessage message)
    {
        getRootMessageLogger().message(subject, message);
    }

    /**
     * Logs the specified LogMessage
     *
     * @param message The message to log
     */
    public static void message(LogMessage message)
    {
        getRootMessageLogger().message((message));
    }

    private synchronized static RootMessageLogger getRootMessageLogger()
    {
        final RootMessageLogger rootMessageLogger = _rootMessageLogger;
        return rootMessageLogger == null ? new NullRootMessageLogger() : rootMessageLogger;
    }

    public static synchronized void setRootMessageLogger(final RootMessageLogger rootMessageLogger)
    {
        _rootMessageLogger = rootMessageLogger;
    }
}
