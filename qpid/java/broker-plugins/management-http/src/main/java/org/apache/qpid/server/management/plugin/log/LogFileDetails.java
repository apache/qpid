/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.qpid.server.management.plugin.log;

import java.io.File;

public class LogFileDetails
{
    private String _name;
    private File _location;
    private String _mimeType;
    private long _size;
    private long _lastModified;
    private String _appenderName;

    public LogFileDetails(String name, String appenderName, File location, String mimeType, long fileSize, long lastUpdateTime)
    {
        super();
        _name = name;
        _location = location;
        _mimeType = mimeType;
        _size = fileSize;
        _lastModified = lastUpdateTime;
        _appenderName = appenderName;
    }

    public String getName()
    {
        return _name;
    }

    public File getLocation()
    {
        return _location;
    }

    public String getMimeType()
    {
        return _mimeType;
    }

    public long getSize()
    {
        return _size;
    }

    public long getLastModified()
    {
        return _lastModified;
    }

    public String getAppenderName()
    {
        return _appenderName;
    }

    @Override
    public String toString()
    {
        return "LogFileDetails [name=" + _name + "]";
    }

}
