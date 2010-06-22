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

import org.apache.qpid.info.util.XMLWriter;

import junit.framework.TestCase;

/*
 * This test verifies the XML writer custom class operations
 */
public class XMLWriterTest extends TestCase
{

    private XMLWriter xw = null;

    public void testXMLWriter()
    {
        xw = new XMLWriter(new StringBuffer("Test"));
        assertNotNull("XMLWriter could not instantiate", xw);
        assertEquals("XMLWriter.toString() failed","Test", xw.getXML().toString());
    }

    public void testWriteXMLHeader()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeXMLHeader();
        assertEquals("XMLWriter.writeXMLHeader(...) failed","<?xml version=\"1.0\"?>\n", xw.getXML().toString());
    }

    public void testWriteTag()
    {
        String INDEND = "    ";
        xw = new XMLWriter(new StringBuffer());
        assertNotNull("XMLWriter could not instantiate", xw);
        xw.writeTag("test", new HashMap<String, String>(), "TEST");
        assertEquals("XMLWriter.writeTag(...) failed","<test>\n" + INDEND + "TEST\n" + "</test>\n", xw.getXML()
                .toString());
    }

    public void testWriteOpenTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        HashMap<String, String> attr = new HashMap<String, String>();
        xw.writeOpenTag("test", attr);
        assertEquals("XMLWriter.writeOpenTag(...) failed","<test>\n", xw.getXML().toString());
        attr.put("id", "1");
        xw.writeOpenTag("test1", attr);
        assertEquals("XMLWriter.writeOpenTag(...) failed","<test>\n" + "<test1 id=\"1\">\n", xw.getXML().toString());
    }

    public void testWriteCloseTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeCloseTag("test");
        assertEquals("</test>\n", xw.getXML().toString());
    }

}
