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

/**
 *
 * @author sorin
 *
 *  Implementation for Info service
 */

package org.apache.qpid.info;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


public class InfoServiceImpl implements InfoService
{

    SortedMap<String, String> infoMap = new TreeMap<String, String>();

    /**
     * invoke method collects all the information from System and Application
     * and encapsulates them in an Info object
     * @return An instance of an Info object
     */
    public Info<? extends Map<String,?>> invoke(String action)
    {
        // Record the action (STARTUP/SHUTDOWN)
        infoMap.put("action",action);

        // Record the current time stamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
        infoMap.put("time", sdf.format(Calendar.getInstance().getTime()));

        // Add the system specific properties 
        infoMap.putAll(SystemInfo.getInfo());
        
        // Add the application specific properties
        infoMap.putAll(AppInfo.getInfo());

        return new Info<SortedMap<String, String>>(infoMap);
    }

}
