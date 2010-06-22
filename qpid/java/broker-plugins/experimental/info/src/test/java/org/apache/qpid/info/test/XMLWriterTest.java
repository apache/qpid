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
import org.apache.qpid.info.util.XMLWriter;

import java.util.HashMap;

/*
 * This test verifies the XML writer custom class operations
 */

public class XMLWriterTest extends TestCase
{

    private XMLWriter xw = null;

    /** Test constructor arg is returned via getXML() */
    public void testXMLWriter()
    {
        StringBuffer input = new StringBuffer("Test");
        xw = new XMLWriter(input);
        assertNotNull("XMLWriter could not instantiate", xw);
        assertEquals("XMLWriter.getXML() failed", input, xw.getXML());
    }

    /** Test header generation */
    public void testWriteXMLHeader()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeXMLHeader();
        assertEquals("XMLWriter.writeXMLHeader(...) failed", "<?xml version=\"1.0\"?>\n", xw.getXML().toString());
    }

    /** Test tag created and written correctly */
    public void testWriteTag()
    {
        String INDENT = "    ";
        xw = new XMLWriter(new StringBuffer());
        assertNotNull("XMLWriter could not instantiate", xw);
        xw.writeTag("test", new HashMap<String, String>(), "TEST");
        assertEquals("XMLWriter.writeTag(...) failed", "<test>\n" + INDENT + "TEST\n" + "</test>\n", xw.getXML()
                .toString());
    }

    /** Test tag created and written correctly */
    public void testWriteTagWithNullAttribute()
    {
        String INDENT = "    ";
        xw = new XMLWriter(new StringBuffer());
        assertNotNull("XMLWriter could not instantiate", xw);
        xw.writeTag("test", null, "TEST");
        assertEquals("XMLWriter.writeTag(...) failed", "<test>\n" + INDENT + "TEST\n" + "</test>\n", xw.getXML()
                .toString());
    }

    /** Test tag created and written correctly with attribute */
    public void testWriteTagWithAttribute()
    {
        String INDENT = "    ";
        xw = new XMLWriter(new StringBuffer());
        assertNotNull("XMLWriter could not instantiate", xw);
        HashMap<String, String> attr = new HashMap<String, String>();
        attr.put("id", "1");

        xw.writeTag("test", attr, "TEST");
        assertEquals("XMLWriter.writeTag(...) failed", "<test id=\"1\">\n" + INDENT + "TEST\n" + "</test>\n", xw.getXML()
                .toString());
    }

    /** Test open tag with an empty attribute map. Just creates an open tag */
    public void testWriteOpenTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        HashMap<String, String> attr = new HashMap<String, String>();
        xw.writeOpenTag("test", attr);
        assertEquals("XMLWriter.writeOpenTag(...) failed", "<test>\n", xw.getXML().toString());
    }

    /** Test open tag with a null attribute map. Just creates an open tag */
    public void testNullAtrributeOnTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeOpenTag("test", null);
        assertEquals("XMLWriter.writeOpenTag(...) failed", "<test>\n", xw.getXML().toString());
    }

    /** Test that setting an attribute value on the tag is correctly outputted. */
    public void testAtrributeOnTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        HashMap<String, String> attr = new HashMap<String, String>();

        attr.put("id", "1");
        xw.writeOpenTag("test1", attr);
        assertEquals("XMLWriter.writeOpenTag(...) failed", "<test1 id=\"1\">\n", xw.getXML().toString());
    }

    /** Test Close Tag is correctly written */
    public void testWriteCloseTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeCloseTag("test");
        assertEquals("</test>\n", xw.getXML().toString());
    }

}
