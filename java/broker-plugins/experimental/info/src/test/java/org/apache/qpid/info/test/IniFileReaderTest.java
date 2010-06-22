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
import org.apache.qpid.info.util.IniFileReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * Test the Loading of the ini file reader by first writing
 * out a correct ini file.
 */
public class IniFileReaderTest extends TestCase
{

    public void testLoad()
    {
        IniFileReader ifr = new IniFileReader();
        File iniFile = null;
        try
        {
            iniFile = File.createTempFile("temp", "ini");
            iniFile.deleteOnExit();
            BufferedWriter writer = new BufferedWriter(new FileWriter(iniFile));
            writer.write("# Global Comment1\n");
            writer.write("globalprop1=globalval1\n");
            writer.write("globalprop2=globalval2\n");
            writer.write("\n");
            writer.write("[Section1] # Comment on Section\n");
            writer.write("key1=val1 # Comment on Value\n");
            writer.write("key2=val2\n");
            writer.write("\n");
            writer.write("#Section2 Comment\n");
            writer.write("[Section2]\n");
            writer.write("key3=val3\n");
            writer.write("key4=val4\n");
            writer.write("key5=val5\n");
            writer.write("\n");
            writer.write("[Section3]\n");
            writer.write("key6=val6\n");
            writer.write("key7=val7\n");
            writer.write("\n");
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            fail("Unable to create temporary File");
        }
        ifr.load(iniFile.getAbsolutePath());
        Map<String, Properties> sections = ifr.getSections();
        assertNotNull("Sections not null", sections);
        assertEquals("Have 4 sections", sections.keySet().size(), 4);
        assertTrue("Get globalprop1", sections.get("").getProperty("globalprop1").equals("globalval1"));
        assertTrue("Get globalprop2", sections.get("").getProperty("globalprop2").equals("globalval2"));
        assertNotNull("Section1 not null", sections.get("Section1"));
        assertEquals("Section1 has 2 properties", sections.get("Section1").size(), 2);
        assertTrue("Section1 key1 has val1", sections.get("Section1").getProperty("key1").equals("val1"));
        assertTrue("Section1 key2 has val2", sections.get("Section1").getProperty("key2").equals("val2"));
        assertEquals("Section2 has 3 properties", sections.get("Section2").size(), 3);
        assertTrue("Section2 key3 has val3", sections.get("Section2").getProperty("key3").equals("val3"));
        assertTrue("Section2 key4 has val4", sections.get("Section2").getProperty("key4").equals("val4"));
        assertTrue("Section2 key5 has val5", sections.get("Section2").getProperty("key5").equals("val5"));
        assertEquals("Section3 has 2 properties", sections.get("Section3").size(), 2);
        assertTrue("Section3 key6 has val6", sections.get("Section3").getProperty("key6").equals("val6"));
        assertTrue("Section3 key7 has val7", sections.get("Section3").getProperty("key7").equals("val7"));
    }

    /**
     * Test to ensure that the loading of a file with an unclosed section header
     * fails to parse.
     *
     * Section needs to be fully enclosed in square brackets '[<name>]'
     */
    public void testIncompleteSection1Load()
    {
        IniFileReader ifr = new IniFileReader();
        File iniFile = null;
        try
        {
            iniFile = File.createTempFile(getName(), "ini");
            iniFile.deleteOnExit();
            BufferedWriter writer = new BufferedWriter(new FileWriter(iniFile));
            writer.write("# Global Comment1\n");
            writer.write("globalprop1=globalval1\n");
            writer.write("globalprop2=globalval2\n");
            writer.write("\n");
            writer.write("[Section1\n");  // Note '[Section1' not complete
            writer.write("key1=val1\n");
            writer.write("key2=val2\n");
            writer.write("\n");
            writer.close();            
        }
        catch (IOException e)
        {
            e.printStackTrace();
            fail("Unable to create temporary File");
        }
        try
        {
            ifr.load(iniFile.getAbsolutePath());
            fail("File should fail to parse");
        }
        catch (IllegalArgumentException iae)
        {
            assertEquals("Incorrect Exception", "Section1 is not closed", iae.getMessage());
        }

    }

}
