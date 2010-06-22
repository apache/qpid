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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.qpid.info.Info;
import org.apache.qpid.info.InfoServiceImpl;

import junit.framework.TestCase;

/*
 * This test verifies the invoke() method for the info service making sure that the parameters are returned
 */
public class InfoServiceImplTest extends TestCase
{

    InfoServiceImpl _isi = null;

    @SuppressWarnings("unchecked")
    public void testInvoke()
    {
        _isi = new InfoServiceImpl();
        assertNotNull(_isi);
        Info<? extends Map<String, String>> info = (Info<? extends Map<String, String>>) _isi
                .invoke("START");
        assertNotNull(info);
        Properties props = info.toProps();
        assertNotNull(props);
        List<String> infoProps = Arrays.asList("java.class.path",
                "java.vm.name", "java.class.version", "os.arch", "os.name",
                "os.version", "sun.arch.data.model", "user.dir", "user.name",
                "user.timezone");
        for (String tag : infoProps)
        {
            assertNotNull("Info.toProps() does not have the property: " + tag,
                    props.getProperty(tag));
        }
    }

}
