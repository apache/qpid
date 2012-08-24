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

import java.text.MessageFormat;

import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.subjects.LogSubjectFormat;

/**
 * HttpManagement actor to use in {@link AbstractServlet} to log all http management operational logging.
 * 
 * An instance is required per http Session.
 */
public class HttpManagementActor extends AbstractManagementActor
{
    private String _cachedLogString;
    private String _lastPrincipalName;
    private String _address;

    public HttpManagementActor(RootMessageLogger rootLogger, String ip, int port)
    {
        super(rootLogger, UNKNOWN_PRINCIPAL);
        _address = ip + ":" + port;
    }

    private synchronized String getAndCacheLogString()
    {
        String principalName = getPrincipalName();

        if(!principalName.equals(_lastPrincipalName))
        {
            _lastPrincipalName = principalName;
             _cachedLogString = "[" + MessageFormat.format(LogSubjectFormat.MANAGEMENT_FORMAT, principalName, _address) + "] ";
        }

        return _cachedLogString;
    }

    public String getLogMessage()
    {
        return getAndCacheLogString();
    }
}
