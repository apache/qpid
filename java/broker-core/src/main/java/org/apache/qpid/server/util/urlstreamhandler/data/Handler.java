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
package org.apache.qpid.server.util.urlstreamhandler.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import javax.xml.bind.DatatypeConverter;

public class Handler extends URLStreamHandler
{
    public static final String PROTOCOL_HANDLER_PROPERTY = "java.protocol.handler.pkgs";
    private static boolean _registered;

    @Override
    protected URLConnection openConnection(final URL u) throws IOException
    {
        return new DataUrlConnection(u);
    }

    public synchronized static void register()
    {
        if(!_registered)
        {
            String registeredPackages = System.getProperty(PROTOCOL_HANDLER_PROPERTY);
            String thisPackage = Handler.class.getPackage().getName();
            String packageToRegister = thisPackage.substring(0, thisPackage.lastIndexOf('.') );
            System.setProperty(PROTOCOL_HANDLER_PROPERTY,
                               registeredPackages == null
                                       ? packageToRegister
                                       : packageToRegister + "|" + registeredPackages);

            _registered = true;
        }



    }

    private static class DataUrlConnection extends URLConnection
    {
        private final byte[] _content;
        private final String _contentType;
        private final boolean _base64;

        public DataUrlConnection(final URL u) throws IOException
        {
            super(u);
            String externalForm = u.toExternalForm();
            if(externalForm.startsWith("data:"))
            {
                String[] parts = externalForm.substring(5).split(",",2);
                _base64 = parts[0].endsWith(";base64");
                if(_base64)
                {
                    _content = DatatypeConverter.parseBase64Binary(parts[1]);
                }
                else
                {
                    try
                    {
                        _content = URLDecoder.decode(parts[1], StandardCharsets.US_ASCII.name()).getBytes(StandardCharsets.US_ASCII);
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        throw new IOException(e);
                    }
                }
                String mediaType = (_base64
                        ? parts[0].substring(0,parts[0].length()-";base64".length())
                        : parts[0]).split(";")[0];

                _contentType = "".equals(mediaType) ? "text/plain" : mediaType;
            }
            else
            {
                throw new MalformedURLException("'"+externalForm+"' does not start with 'data:'");
            }
        }



        @Override
        public void connect() throws IOException
        {

        }

        @Override
        public int getContentLength()
        {
            return _content.length;
        }

        @Override
        public String getContentType()
        {
            return _contentType;
        }

        @Override
        public String getContentEncoding()
        {
            return _base64 ? "base64" : null;
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return new ByteArrayInputStream(_content);
        }
    }
}
