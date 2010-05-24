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

    protected void setUp() throws Exception
    {
        super.setUp();

    }

    protected void tearDown() throws Exception
    {
        super.tearDown();

    }

    public void testXMLWriter()
    {
        xw = new XMLWriter(new StringBuffer("Test"));
        assertNotNull(xw);
        assertEquals("Test", xw.getXML().toString());
    }

    public void testWriteXMLHeader()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeXMLHeader();
        assertEquals("<?xml version=\"1.0\"?>\n", xw.getXML().toString());
    }

    public void testWriteTag()
    {
        String INDEND = "    ";
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeTag("test", new HashMap<String, String>(), "TEST");
        assertEquals("<test>\n" + INDEND + "TEST\n" + "</test>\n", xw.getXML()
                .toString());
    }

    public void testWriteOpenTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        HashMap<String, String> attr = new HashMap<String, String>();
        xw.writeOpenTag("test", attr);
        assertEquals("<test>\n", xw.getXML().toString());
        attr.put("id", "1");
        xw.writeOpenTag("test1", attr);
        assertEquals("<test>\n" + "<test1 id=\"1\">\n", xw.getXML().toString());
    }

    public void testWriteCloseTag()
    {
        xw = new XMLWriter(new StringBuffer());
        assertNotNull(xw);
        xw.writeCloseTag("test");
        assertEquals("</test>\n", xw.getXML().toString());
    }

}
