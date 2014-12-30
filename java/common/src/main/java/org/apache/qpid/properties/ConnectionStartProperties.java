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

import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.SystemUtils;

/**
 * Constants for the various properties clients can
 * set values for during the ConnectionStartOk reply.
 */
public class ConnectionStartProperties
{
    private static final Logger LOGGER = Logger.get(ConnectionStartProperties.class);

    /**
     * Used for 0-8/0-9/0-9-1 connections to choose to close
     * the connection when a transactional session receives a 'mandatory' message which
     * can't be routed rather than returning the message.
     */
    public static final String QPID_CLOSE_WHEN_NO_ROUTE = "qpid.close_when_no_route";

    public static final String QPID_MESSAGE_COMPRESSION_SUPPORTED = "qpid.message_compression_supported";


    public static final String CLIENT_ID_0_10 = "clientName";
    public static final String CLIENT_ID_0_8 = "instance";

    public static final String VERSION_0_8 = "version";
    public static final String VERSION_0_10 = "qpid.client_version";

    public static final String PROCESS = "qpid.client_process";

    public static final String PID = "qpid.client_pid";

    public static final String PLATFORM = "platform";

    public static final String PRODUCT ="product";

    public static final String SESSION_FLOW = "qpid.session_flow";

    public static final String QPID_CONFIRMED_PUBLISH_SUPPORTED = "qpid.confirmed_publish_supported";

    public static final int _pid;

    public static final String _platformInfo;

    static
    {

        _pid = SystemUtils.getProcessPidAsInt();

        if (_pid == -1)
        {
            LOGGER.warn("Unable to get the process's PID");
        }

        StringBuilder fullSystemInfo = new StringBuilder(System.getProperty("java.runtime.name"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("java.runtime.version"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("java.vendor"));
        fullSystemInfo.append(", ");
        fullSystemInfo.append(SystemUtils.getOSArch());
        fullSystemInfo.append(", ");
        fullSystemInfo.append(SystemUtils.getOSName());
        fullSystemInfo.append(", ");
        fullSystemInfo.append(SystemUtils.getOSVersion());
        fullSystemInfo.append(", ");
        fullSystemInfo.append(System.getProperty("sun.os.patch.level"));

        _platformInfo = fullSystemInfo.toString();
    }

    public static int getPID()
    {
        return _pid;
    }

    public static String getPlatformInfo()
    {
        return _platformInfo;
    }
}
