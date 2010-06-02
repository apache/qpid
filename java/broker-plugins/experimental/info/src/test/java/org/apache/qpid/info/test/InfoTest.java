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

import java.util.HashMap;
import java.util.Properties;
import junit.framework.TestCase;
import org.apache.qpid.info.Info;

/*
 * This test verifies the toString(), toProps(), toXML() and toStringBuffer() methods of the Info object
 * 
 */
public class InfoTest extends TestCase
{
    private HashMap<String, String> infoPayLoad = null;

    private Info<HashMap<String, String>> info = null;

    protected void setUp() throws Exception
    {
        super.setUp();
        infoPayLoad = new HashMap<String, String>();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        info = null;
        infoPayLoad = null;
    }

    public void testInfo()
    {
        info = new Info<HashMap<String, String>>(infoPayLoad);
        assertNotNull(info);
    }

    public void testToString()
    {
        infoPayLoad.clear();
        infoPayLoad.put("test", "Test");
        info = new Info<HashMap<String, String>>(infoPayLoad);
        assertNotNull(info.toString());
        assertEquals("test=Test\n", info.toString());
    }

    public void testToProps()
    {
        Properties props = new Properties();
        props.put("test", "Test");
        infoPayLoad.clear();
        infoPayLoad.put("test", "Test");
        info = new Info<HashMap<String, String>>(infoPayLoad);
        assertNotNull(info.toProps());
        assertEquals(props, info.toProps());
    }

    public void testToStringBuffer()
    {
        StringBuffer sb = new StringBuffer("test=Test\n");
        infoPayLoad.clear();
        infoPayLoad.put("test", "Test");
        info = new Info<HashMap<String, String>>(infoPayLoad);
        assertNotNull(info.toStringBuffer());
        assertEquals(sb.toString(), info.toStringBuffer().toString());
    }

    public void testToXML()
    {
        String INDEND = "    ";
        infoPayLoad.clear();
        infoPayLoad.put("test", "Test");
        info = new Info<HashMap<String, String>>(infoPayLoad);
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\"?>\n");
        sb.append("<qpidinfo>\n");
        sb.append("<test>\n");
        sb.append(INDEND + "Test\n");
        sb.append("</test>\n");
        sb.append("</qpidinfo>\n");
        assertEquals(info.toXML().toString(), sb.toString());
    }

}
