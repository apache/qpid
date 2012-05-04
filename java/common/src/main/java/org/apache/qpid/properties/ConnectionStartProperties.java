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
package org.apache.qpid.properties;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import org.apache.qpid.transport.util.Logger;

/**
 * Constants for the various properties 0-10 clients can
 * set values for during the ConnectionStartOk reply.
 */
public class ConnectionStartProperties
{
    private static final Logger LOGGER = Logger.get(ConnectionStartProperties.class);

    public static final String CLIENT_ID_0_10 = "clientName";
    public static final String CLIENT_ID_0_8 = "instance";

    public static final String VERSION_0_8 = "version";
    public static final String VERSION_0_10 = "qpid.client_version";

    public static final String PROCESS = "qpid.client_process";

    public static final String PID = "qpid.client_pid";

    public static final String PLATFORM = "platform";

    public static final String PRODUCT ="product";

    public static final String SESSION_FLOW = "qpid.session_flow";

    public static int getPID()
    {
        RuntimeMXBean rtb = ManagementFactory.getRuntimeMXBean();
        String processName = rtb.getName();
        if (processName != null && processName.indexOf('@') > 0)
        {
            try
            {
                return Integer.parseInt(processName.substring(0,processName.indexOf('@')));
            }
            catch(Exception e)
            {
                LOGGER.warn("Unable to get the PID due to error",e);
                return -1;
            }
        }
        else
        {
            LOGGER.warn("Unable to get the PID due to unsupported format : " + processName);
            return -1;
        }
    }

    public static String getPlatformInfo()
    {
        StringBuilder fullSystemInfo = new StringBuilder(System.getProperty("java.runtime.name"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("java.runtime.version"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("java.vendor"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("os.arch"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("os.name"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("os.version"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("sun.os.patch.level"));

        return fullSystemInfo.toString();
    }
}
