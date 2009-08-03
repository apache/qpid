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

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.LogMonitor;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.logging.subjects.AbstractTestLogSubject;

import java.io.IOException;

public class AbstractTestLogging extends QpidTestCase
{
    protected LogMonitor _monitor;


    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _monitor = new LogMonitor(_outputFile);
    }

    /**
     * assert that the requested log message has not occured
     * @param log
     * @throws IOException
     */
    public void assertLoggingNotYetOccured(String log) throws IOException
    {
        // Ensure the alert has not occured yet
        assertEquals("Message has already occured:"+log, 0,
                     _monitor.findMatches(log).size());
    }

    protected int extractConnectionID(String log)
    {
        int conIDStart = log.indexOf("con:") + 4;
        int conIDEnd = log.indexOf("(", conIDStart);
        return Integer.parseInt(log.substring(conIDStart, conIDEnd));
    }
    
}
