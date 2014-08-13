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
package org.apache.qpid.systest.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.zip.GZIPInputStream;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class CompressedResponsesRestTest extends QpidRestTestCase
{

    private boolean _compress;

    @Override
    public void setUp() throws Exception
    {
    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getBrokerConfiguration().setObjectAttribute(Plugin.class,
                                                    TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                                                    "compressResponses",
                                                    _compress);
    }

    public void testCompressionOffAcceptOff() throws Exception
    {
        doCompressionTest(false, false);
    }

    public void testCompressionOffAcceptOn() throws Exception
    {
        doCompressionTest(false, true);
    }

    public void testCompressionOnAcceptOff() throws Exception
    {
        doCompressionTest(true, false);
    }

    public void testCompressionOnAcceptOn() throws Exception
    {
        doCompressionTest(true, true);

    }

    private void doCompressionTest(final boolean allowCompression,
                                   final boolean acceptCompressed) throws Exception
    {
        final boolean expectCompression = allowCompression && acceptCompressed;
        _compress = allowCompression;
        super.setUp();

        HttpURLConnection conn = getRestTestHelper().openManagementConnection("/service/metadata", "GET");
        if(acceptCompressed)
        {
            conn.setRequestProperty("Accept-Encoding", "gzip");
        }

        conn.connect();

        String contentEncoding = conn.getHeaderField("Content-Encoding");

        if(expectCompression)
        {
            assertEquals("gzip", contentEncoding);
        }
        else
        {
            if(contentEncoding != null)
            {
                assertEquals("identity", contentEncoding);
            }
        }

        ByteArrayOutputStream contentBuffer = new ByteArrayOutputStream();

        InputStream connectionInputStream = conn.getInputStream();
        byte[] buf = new byte[1024];
        int read;
        while((read = connectionInputStream.read(buf))!= -1)
        {
            contentBuffer.write(buf,0,read);
        }

        InputStream jsonStream;

        if(expectCompression)
        {
            jsonStream = new GZIPInputStream(new ByteArrayInputStream(contentBuffer.toByteArray()));
        }
        else
        {
            jsonStream = new ByteArrayInputStream(contentBuffer.toByteArray());
        }

        ObjectMapper mapper = new ObjectMapper();
        try
        {
            mapper.readValue(jsonStream, LinkedHashMap.class);
        }
        catch (JsonParseException | JsonMappingException e)
        {
            fail("Message was not in correct format");
        }
    }


}
