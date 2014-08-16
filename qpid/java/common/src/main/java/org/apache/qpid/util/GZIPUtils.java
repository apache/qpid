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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GZIPUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GZIPUtils.class);

    public static final String GZIP_CONTENT_ENCODING = "gzip";


    /**
     * Return a new byte array with the compressed contents of the input buffer
     *
     * @param input byte buffer to compress
     * @return a byte array containing the compressed data, or null if the input was null or there was an unexpected
     * IOException while compressing
     */
    public static byte[] compressBufferToArray(ByteBuffer input)
    {
        if(input != null)
        {
            try (ByteArrayOutputStream compressedBuffer = new ByteArrayOutputStream())
            {
                try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(compressedBuffer))
                {
                    if (input.hasArray())
                    {
                        gzipOutputStream.write(input.array(),
                                               input.arrayOffset() + input.position(),
                                               input.remaining());
                    }
                    else
                    {

                        byte[] data = new byte[input.remaining()];

                        input.duplicate().get(data);

                        gzipOutputStream.write(data);
                    }
                }
                return compressedBuffer.toByteArray();
            }
            catch (IOException e)
            {
                LOGGER.warn("Unexpected IOException when attempting to compress with gzip", e);
            }
        }
        return null;
    }

    public static byte[] uncompressBufferToArray(ByteBuffer contentBuffer)
    {
        if(contentBuffer != null)
        {
            try (ByteBufferInputStream input = new ByteBufferInputStream(contentBuffer))
            {
                return uncompressStreamToArray(input);
            }
        }
        else
        {
            return null;
        }
    }

    public static byte[] uncompressStreamToArray(InputStream stream)
    {
        if(stream != null)
        {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(stream))
            {
                ByteArrayOutputStream inflatedContent = new ByteArrayOutputStream();
                int read;
                byte[] buf = new byte[4096];
                while ((read = gzipInputStream.read(buf)) != -1)
                {
                    inflatedContent.write(buf, 0, read);
                }
                return inflatedContent.toByteArray();
            }
            catch (IOException e)
            {

                LOGGER.warn("Unexpected IOException when attempting to uncompress with gzip", e);
            }
        }
        return null;
    }
}
