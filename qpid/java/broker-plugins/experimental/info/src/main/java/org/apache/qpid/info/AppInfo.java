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

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.registry.ApplicationRegistry;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

/** AppInfo class is gathering application specific information */
public class AppInfo
{

    private static final List<String> appProps = Arrays.asList("QPID_HOME",
                                                               "QPID_WORK");

    private static Map<String, String> appInfoMap = new TreeMap<String, String>();

    /**
     * getInfo method retrieves a key-value map for specific application properties
     *
     * @return Map<String,String>
     */
    public static Map<String, String> getInfo()
    {

        // Gather the selected app props
        Properties sysprops = System.getProperties();
        String propName;
        for (Iterator<Entry<Object, Object>> it = sysprops.entrySet()
                .iterator(); it.hasNext();)
        {
            Entry<Object, Object> en = it.next();
            propName = en.getKey().toString();
            if (appProps.indexOf(propName) >= 0)
            {
                appInfoMap.put(propName, en.getValue().toString());
            }
        }

        ServerConfiguration sc;
        try
        {
            sc = ApplicationRegistry.getInstance().getConfiguration();
            if (null != sc)
            {
                appInfoMap.put("jmxport", sc.getJMXManagementPort() + "");
                appInfoMap.put("port", sc.getPorts().toString());
                appInfoMap.put("version", QpidProperties.getReleaseVersion());
                appInfoMap.put("vhosts", "standalone");
                appInfoMap.put("KeystorePath", sc.getKeystorePath());
                appInfoMap.put("PluginDirectory", sc.getPluginDirectory());
                appInfoMap.put("CertType", sc.getCertType());
                appInfoMap.put("QpidWork", sc.getQpidWork());
                appInfoMap.put("Bind", sc.getBind());
            }
        }
        catch (Exception e)
        {
            // drop everything to be silent
        }
        return appInfoMap;

    }

}
