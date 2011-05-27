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

package org.apache.qpid.info.test;

import junit.framework.TestCase;
import org.apache.qpid.info.SystemInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Test the SystemInfo component */
public class SystemInfoTest extends TestCase
{

    /**
     * Ensure the list of required properties are returned by the
     * SystemInfo.getInfo call
     */
    public void testGetInfo()
    {
        Map<String, String> sysInfoMap = SystemInfo.getInfo();
        assertNotNull("SystemInfo.getInfo() returned null", sysInfoMap);
        List<String> sysInfoProps = Arrays.asList(
                "java.class.path",
                "java.vm.name", "java.class.version", "os.arch", "os.name",
                "os.version", "sun.arch.data.model", "user.dir", "user.name",
                "user.timezone", "hostname", "ip", "CPUCores", "Maximum_Memory",
                "Free_Memory");

        for (String tag : sysInfoProps)
        {
            assertNotNull("Map does not contain the tag: " + tag, sysInfoMap.get(tag));
        }
    }

}