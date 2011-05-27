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
    private HashMap<String, String> _infoPayLoad = null;

    private Info<HashMap<String, String>> _info = null;

    protected void setUp() throws Exception
    {
        super.setUp();
        _infoPayLoad = new HashMap<String, String>();
        _infoPayLoad.put("test", "Test");
        _info = new Info<HashMap<String, String>>(_infoPayLoad);
    }

    /*
     * Test the conversion toString() of the Info object
     */
    public void testToString()
    {
        assertNotNull("toString() returned null", _info.toString());
        assertEquals("toString() did not return the proper string",
                "test=Test\n", _info.toString());
    }

    /*
     * Test the conversion toProps() of the Info object
     */
    public void testToProps()
    {
        Properties props = new Properties();
        props.put("test", "Test");
        assertNotNull("toProperties() returned null", _info.toProps());
        assertEquals("toProperties not returned the proper object", props, _info
                .toProps());
    }

    /*
     * Test the conversion toStringBuffer() of the Info object
     */
    public void testToStringBuffer()
    {
        StringBuffer sb = new StringBuffer("test=Test\n");
        assertNotNull(_info.toStringBuffer());
        assertEquals(sb.toString(), _info.toStringBuffer().toString());
    }

    /*
     * Test conversion toXML() of the info object
     */
    public void testToXML()
    {
        String INDENT = "    ";
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\"?>\n");
        sb.append("<qpidinfo>\n");
        sb.append("<test>\n");
        sb.append(INDENT + "Test\n");
        sb.append("</test>\n");
        sb.append("</qpidinfo>\n");
        assertEquals("toString() does not return the proper string", _info
                .toXML().toString(), sb.toString());
    }

    /*
     * Test the conversion toMap() of the Info object
     */
    public void testToMap()
    {
        HashMap<String, String> thm = _info.toMap();
        assertFalse("toMap() returned empty map", thm.isEmpty());
        assertEquals("testToMap did not returned 1", 1, thm.size());
        assertTrue("toMap() returned a map not containing expected key: test",
                thm.containsKey("test"));
        assertTrue(
                "toMap() returned a map not containing the value for key test: Test",
                thm.containsValue("Test"));

    }

}
