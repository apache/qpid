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
package org.apache.qpid.util;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ByteBufferInputStreamTest extends TestCase
{
    private byte[] _data = {2, 1, 5, 3, 4};
    private ByteBufferInputStream _inputStream;

    public void setUp() throws Exception
    {
        _inputStream = new ByteBufferInputStream(ByteBuffer.wrap(_data));
    }

    public void testRead() throws IOException
    {
        for (int i = 0; i < _data.length; i++)
        {
            assertEquals("Unexpected byte at position " + i, _data[i], _inputStream.read());
        }
        assertEquals("EOF not reached", -1, _inputStream.read());
    }

    public void testReadByteArray() throws IOException
    {
        byte[] readBytes = new byte[_data.length];
        int length = _inputStream.read(readBytes, 0, 2);

        byte[] expected = new byte[_data.length];
        System.arraycopy(_data, 0, expected, 0, 2);

        assertTrue("Unexpected data", Arrays.equals(expected, readBytes));
        assertEquals("Unexpected length", 2, length);

        length = _inputStream.read(readBytes, 2, 3);

        assertTrue("Unexpected data", Arrays.equals(_data, readBytes));
        assertEquals("Unexpected length", 3, length);

        length = _inputStream.read(readBytes);
        assertEquals("EOF not reached", -1, length);
    }

    public void testSkip() throws IOException
    {
        _inputStream.skip(3);
        byte[] readBytes = new byte[_data.length - 3];
        int length = _inputStream.read(readBytes);

        byte[] expected = new byte[_data.length - 3];
        System.arraycopy(_data, 3, expected, 0, _data.length - 3);

        assertTrue("Unexpected data", Arrays.equals(expected, readBytes));
        assertEquals("Unexpected length", _data.length - 3, length);
    }

    public void testAvailable() throws IOException
    {
        int available = _inputStream.available();
        assertEquals("Unexpected number of available bytes", _data.length, available);
        byte[] readBytes = new byte[_data.length];
        _inputStream.read(readBytes);
        available = _inputStream.available();
        assertEquals("Unexpected number of available bytes", 0, available);
    }

    public void testMarkReset() throws IOException
    {
        _inputStream.mark(0);
        byte[] readBytes = new byte[_data.length];
        int length = _inputStream.read(readBytes);
        assertEquals("Unexpected length", _data.length, length);
        assertEquals("Unexpected number of available bytes", 0, _inputStream.available());

        _inputStream.reset();
        readBytes = new byte[_data.length];
        length = _inputStream.read(readBytes);
        assertEquals("Unexpected length", _data.length, length);
        assertEquals("Unexpected number of available bytes", 0, _inputStream.available());
    }

    public void testMarkSupported() throws IOException
    {
        assertTrue("Unexpected mark supported", _inputStream.markSupported());
    }

}
