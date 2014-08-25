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


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import junit.framework.TestCase;

public class GZIPUtilsTest extends TestCase
{
    public void testCompressUncompress() throws Exception
    {
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte)'a');
        byte[] compressed = GZIPUtils.compressBufferToArray(ByteBuffer.wrap(data));
        assertTrue("Compression didn't compress", compressed.length < data.length);
        byte[] uncompressed = GZIPUtils.uncompressBufferToArray(ByteBuffer.wrap(compressed));
        assertTrue("Compression not reversible", Arrays.equals(data,uncompressed));
    }

    public void testUncompressNonZipReturnsNull() throws Exception
    {
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte)'a');
        assertNull("Non zipped data should not uncompress", GZIPUtils.uncompressBufferToArray(ByteBuffer.wrap(data)));
    }

    public void testUncompressStreamWithErrorReturnsNull() throws Exception
    {
        InputStream is = new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                throw new IOException();
            }
        };
        assertNull("Stream error should return null", GZIPUtils.uncompressStreamToArray(is));
    }


    public void testUncompressNullStreamReturnsNull() throws Exception
    {
        assertNull("Null Stream should return null", GZIPUtils.uncompressStreamToArray(null));
    }
    public void testUncompressNullBufferReturnsNull() throws Exception
    {
        assertNull("Null buffer should return null", GZIPUtils.uncompressBufferToArray(null));
    }

    public void testCompressNullArrayReturnsNull()
    {
        assertNull(GZIPUtils.compressBufferToArray(null));
    }

    public void testNonHeapBuffers() throws Exception
    {

        byte[] data = new byte[1024];
        Arrays.fill(data, (byte)'a');
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        directBuffer.put(data);
        directBuffer.flip();

        byte[] compressed = GZIPUtils.compressBufferToArray(directBuffer);

        assertTrue("Compression didn't compress", compressed.length < data.length);

        directBuffer.clear();
        directBuffer.position(1);
        directBuffer = directBuffer.slice();
        directBuffer.put(compressed);
        directBuffer.flip();

        byte[] uncompressed = GZIPUtils.uncompressBufferToArray(directBuffer);

        assertTrue("Compression not reversible", Arrays.equals(data,uncompressed));

    }
}
