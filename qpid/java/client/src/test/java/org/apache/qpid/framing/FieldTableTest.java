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
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Enumeration;
import java.util.Properties;

import junit.framework.TestCase;

public class FieldTableTest extends TestCase
{
    public void testDataDump() throws IOException, AMQFrameDecodingException
    {
        byte[] data = readBase64("content.txt");
        System.out.println("Got " + data.length + " bytes of data");
        for (int i = 0; i < 100; i++)
        {
            System.out.print((char) data[i]);
        }
        System.out.println();
        int size = 4194304;
        ByteBuffer buffer = ByteBuffer.allocate(data.length);
        buffer.put(data);
        buffer.flip();
        FieldTable table = new FieldTable(buffer, size);
    }

    /*
    public void testCase1() throws AMQFrameDecodingException, IOException
    {
        doTestEncoding(load("FieldTableTest.properties"));
    }

    public void testCase2() throws AMQFrameDecodingException, IOException
    {
        doTestEncoding(load("FieldTableTest2.properties"));
    }
    */
    void doTestEncoding(FieldTable table) throws AMQFrameDecodingException
    {
        assertEquivalent(table, encodeThenDecode(table));
    }

    public void assertEquivalent(FieldTable table1, FieldTable table2)
    {
        for (Object o : table1.keySet())
        {
            String key = (String) o;
            assertEquals("Values for " + key + " did not match", table1.get(key), table2.get(key));
            //System.out.println("Values for " + key + " matched (" + table1.get(key) + ")");
        }
    }

    FieldTable encodeThenDecode(FieldTable table) throws AMQFrameDecodingException
    {
        ContentHeaderBody header = new ContentHeaderBody();
        header.classId = 6;
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        header.properties = properties;

        properties.setHeaders(table);
        int size = header.getSize();

        //encode
        ByteBuffer buffer = ByteBuffer.allocate(size);
        header.writePayload(buffer);

        //decode
        buffer.flip();

        header = new ContentHeaderBody();
        header.populateFromBuffer(buffer, size);

        return ((BasicContentHeaderProperties) header.properties).getHeaders();
    }

    byte[] readBase64(String name) throws IOException
    {
        String content = read(new InputStreamReader(getClass().getResourceAsStream(name)));

        return org.apache.commons.codec.binary.Base64.decodeBase64(content.getBytes());
    }

    FieldTable load(String name) throws IOException
    {
        return populate(new FieldTable(), read(name));
    }

    Properties read(String name) throws IOException
    {
        Properties p = new Properties();
        p.load(getClass().getResourceAsStream(name));
        return p;
    }

    FieldTable populate(FieldTable table, Properties properties)
    {
        for (Enumeration i = properties.propertyNames(); i.hasMoreElements();)
        {
            String key = (String) i.nextElement();
            String value = properties.getProperty(key);
            try{
                int ival = Integer.parseInt(value);
                table.put(key, (long) ival);
            }
            catch(NumberFormatException e)
            {
                table.put(key, value);
            }
        }
        return table;
    }

    static String read(Reader in) throws IOException
    {
        return read(in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in));
    }

    static String read(BufferedReader in) throws IOException
    {
        StringBuffer buffer = new StringBuffer();
        String line = in.readLine();
        while (line != null){
            buffer.append(line).append(" ");
            line = in.readLine();
        }
        return buffer.toString();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(FieldTableTest.class);
    }
}
