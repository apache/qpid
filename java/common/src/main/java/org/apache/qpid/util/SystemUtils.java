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
package org.apache.qpid.util;

/**
 * SystemUtils provides some simple helper methods for working with the current
 * Operating System.
 *
 * It follows the convention of wrapping all checked exceptions as runtimes, so
 * code using these methods is free of try-catch blocks but does not expect to
 * recover from errors.
 */
public class SystemUtils
{

    public static final String UNKNOWN_OS = "unknown";
    public static final String UNKNOWN_VERSION = "na";
    public static final String UNKNOWN_ARCH = "unknown";

    private static final String _osName = System.getProperty("os.name", UNKNOWN_OS);
    private static final String _osVersion = System.getProperty("os.version", UNKNOWN_VERSION);
    private static final String _osArch = System.getProperty("os.arch", UNKNOWN_ARCH);

    private static final boolean _isWindows = _osName.toLowerCase().contains("windows");

    private SystemUtils()
    {
    }

    public final static String getOSName()
    {
        return _osName;
    }

    public final static String getOSVersion()
    {
        return _osVersion;
    }

    public final static String getOSArch()
    {
        return _osArch;
    }

    public final static boolean isWindows()
    {
        return _isWindows;
    }

    public final static String getOSConfigSuffix()
    {
        if (_osName.contains(" "))
        {
            return _osName.substring(0, _osName.indexOf(' ')).toLowerCase();
        }
        return _osName;
    }

    public final static String getOSString()
    {
        return _osName + " " + _osVersion + " " + _osArch;
    }
}
