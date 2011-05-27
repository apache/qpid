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

package org.apache.qpid.info;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Collector for system specific information
 */
public class SystemInfo
{

    private static Map<String, String> sysInfoMap = new TreeMap<String, String>();

    private static final List<String> sysProps = Arrays.asList(
            "java.class.path", "java.home", "java.vm.name", "java.vm.vendor",
            "java.vm.version", "java.class.version", "java.runtime.version",
            "os.arch", "os.name", "os.version", "sun.arch.data.model",
            "user.home", "user.dir", "user.name", "user.timezone");

    /**
     * getInfo collects all the properties specified in sysprops list
     * @return A Map<String,String>
     */
    public static Map<String, String> getInfo()
    {

        // Get the hostname
        try
        {
            InetAddress addr = InetAddress.getLocalHost();
            String hostname = addr.getHostName();
            sysInfoMap.put("hostname", hostname);
            sysInfoMap.put("ip", addr.getHostAddress());
        }
        catch (UnknownHostException e)
        {
            // drop everything to be silent
        }
        // Get the runtime info
        sysInfoMap.put("CPUCores", Runtime.getRuntime().availableProcessors()
                + "");
        sysInfoMap.put("Maximum_Memory", Runtime.getRuntime().maxMemory() + "");
        sysInfoMap.put("Free_Memory", Runtime.getRuntime().freeMemory() + "");

        // Gather the selected system props
        Properties sysprops = System.getProperties();
        String propName;
        for (Iterator<Entry<Object, Object>> it = sysprops.entrySet()
                .iterator(); it.hasNext();)
        {
            Entry<Object, Object> en = it.next();
            propName = en.getKey().toString();
            if (sysProps.indexOf(propName) >= 0)
            {
                sysInfoMap.put(propName, en.getValue().toString());
            }
        }

        return sysInfoMap;

    }

}
