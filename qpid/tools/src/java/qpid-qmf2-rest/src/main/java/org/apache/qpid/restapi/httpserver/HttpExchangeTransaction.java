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
package org.apache.qpid.restapi.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import org.apache.qpid.restapi.HttpTransaction;

/**
 * This class provides an implementation of the HttpTransaction interface that wraps com.sun.net.httpserver.HttpExchange
 * in order to provide an implementation neutral facade to the Server classes.
 *
 * @author Fraser Adams
 */
public final class HttpExchangeTransaction implements HttpTransaction
{
    final HttpExchange _exchange;

    /**
     * Construct an HttpExchangeTransaction from an HttpExchange object.
     */
    public HttpExchangeTransaction(final HttpExchange exchange)
    {
        _exchange = exchange;
    }

    /**
     * Log the HTTP request information (primarily for debugging purposes)
     */
    public void logRequest()
    {
        System.out.println(_exchange.getRequestMethod() + " " + _exchange.getRequestURI());
        for (Map.Entry<String, List<String>> header : _exchange.getRequestHeaders().entrySet())
        {
            System.out.println(header);
        }
        System.out.println("From: " + getRemoteHost() + ":" + getRemotePort());
    }

    /**
     * Return the content passed in the request from the client as a Stream.
     * @return the content passed in the request from the client as a Stream.
     */
    public InputStream getRequestStream() throws IOException
    {
        return _exchange.getRequestBody();
    }

    /**
     * Return the content passed in the request from the client as a String.
     * @return the content passed in the request from the client as a String.
     */
    public String getRequestString() throws IOException
    {
        return new String(getRequest());
    }


    /**
     * Return the content passed in the request from the client as a byte[].
     * @return the content passed in the request from the client as a byte[].
     */
    public byte[] getRequest() throws IOException
    {
        InputStream is = _exchange.getRequestBody();

        // Convert InputStream to byte[].
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer, 0, 1024)) != -1)
        {
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }

    /**
     * Send the content passed as a String as an HTTP response back to the client.
     * @param status the HTTP status code e.g. 200 for OK.
     * @param mimeType the mimeType of the response content e.g. text/plain, text/xml, image/jpeg etc.
     * @param content the content of the response passed as a String.
     */
    public void sendResponse(final int status, final String mimeType, final String content) throws IOException
    {
        if (content == null)
        { // If response length has the value -1 then no response body is being sent.
            _exchange.getResponseHeaders().set("Content-Type", mimeType);
            _exchange.sendResponseHeaders(status, -1);
            _exchange.close();
        }
        else
        {
            sendResponse(status, mimeType, content.getBytes());
        }
    }

    /**
     * Send the content passed as a byte[] as an HTTP response back to the client.
     * @param status the HTTP status code e.g. 200 for OK.
     * @param mimeType the mimeType of the response content e.g. text/plain, text/xml, image/jpeg etc.
     * @param content the content of the response passed as a byte[].
     */
    public void sendResponse(final int status, final String mimeType, final byte[] content) throws IOException
    {
        _exchange.getResponseHeaders().set("Content-Type", mimeType);
        if (content == null)
        { // If response length has the value -1 then no response body is being sent. 
            _exchange.sendResponseHeaders(status, -1);
            _exchange.close();
        }
        else
        {
            _exchange.sendResponseHeaders(status, content.length);
            OutputStream os = _exchange.getResponseBody();
            os.write(content);
            os.flush();
            os.close();
            _exchange.close();
        }   
    }

    /**
     * Send the content passed as an InputStream as an HTTP response back to the client.
     * @param status the HTTP status code e.g. 200 for OK.
     * @param mimeType the mimeType of the response content e.g. text/plain, text/xml, image/jpeg etc.
     * @param is the content of the response passed as an InputStream.
     */
    public void sendResponse(final int status, final String mimeType, final InputStream is) throws IOException
    {
        _exchange.getResponseHeaders().set("Content-Type", mimeType);
        if (is == null)
        { // If response length has the value -1 then no response body is being sent.
            _exchange.sendResponseHeaders(status, -1);
            _exchange.close();
        }
        else
        {
            _exchange.sendResponseHeaders(status, 0); // For a stream we set to zero to force chunked transfer encoding.
            OutputStream os = _exchange.getResponseBody();

            byte[] buffer = new byte[8192];
            while (true)
            {
                int read = is.read(buffer, 0, buffer.length);
                if (read == -1) // Loop until EOF is reached
                {
                    break;
                }
                os.write(buffer, 0, read);
            }
          
            os.flush();
            os.close();
            _exchange.close();
        }
    }

    /**
     * Returns the Internet Protocol (IP) address of the client or last proxy that sent the request.
     * @return the Internet Protocol (IP) address of the client or last proxy that sent the request.
     */
    public String getRemoteAddr()
    {
        return _exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    /**
     * Returns the fully qualified name of the client or the last proxy that sent the request.
     * @return the fully qualified name of the client or the last proxy that sent the request.
     */
    public String getRemoteHost()
    {
        return _exchange.getRemoteAddress().getHostName();
    }

    /**
     * Returns the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     * @return the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     */
    public int getRemotePort()
    {
        return _exchange.getRemoteAddress().getPort();
    }

    /**
     * Returns a String containing the name of the current authenticated user. If the user has not been authenticated, 
     * the method returns null.
     * @return a String containing the name of the user making this request; null if the user has not been authenticated.
     */
    public String getPrincipal()
    {
        HttpPrincipal principal = _exchange.getPrincipal();
        return principal == null ? null : principal.getUsername();
    }

    /**
     * Returns the name of the HTTP method with which this request was made, for example, GET, POST, or PUT.
     * @return a String specifying the name of the method with which this request was made.
     */
    public String getMethod()
    {
        return _exchange.getRequestMethod();
    }

    /**
     * Returns the part of this request's URL from the protocol name up to the query string in the first line of
     * the HTTP request.
     * @return a String containing the part of the URL from the protocol name up to the query string.
     */
    public String getRequestURI()
    {
        return _exchange.getRequestURI().getPath();
    }

    /**
     * Sets a response header with the given name and value. If the header had already been set, the new value
     * overwrites the previous one.
     * @param name a String specifying the header name.
     * @param value a String specifying the header value. If it contains octet string, it should be encoded according
     *        to RFC 2047.
     */
    public void setHeader(final String name, final String value)
    {
        _exchange.getResponseHeaders().set(name, value);
    }

    /**
     * Returns the value of the specified request header as a String. If the request did not include a header of the 
     * specified name, this method returns null. If there are multiple headers with the same name, this method returns 
     * the first head in the request. The header name is case insensitive. You can use this method with any request 
     * header.
     * @param name a String specifying the header name.
     * @return a String containing the value of the requested header, or null if the request does not have a header of 
     *         that name.
     */
    public String getHeader(final String name)
    {
        return _exchange.getRequestHeaders().getFirst(name);
    }

    /**
     * Returns the String value of the specified cookie.
     * @param name a String specifying the cookie name.
     */
    public String getCookie(final String name)
    {
        Headers headers = _exchange.getRequestHeaders();
        if (!headers.containsKey("Cookie"))
        {
            return null;
        }

        List<String> values = headers.get("cookie");
        for (String value : values)
        {
            String[] cookies = value.split(";");
            for (String cookie : cookies)
            {
                String[] cdata = cookie.split("=");
                if (cdata[0].trim().equals(name))
                {
                    //return URLDecode(cdata[1]);
                    return cdata[1];
                }
            }
        }
        return null;
    }

    /**
     * Adds the specified cookie to the response. This method can be called multiple times to set more than one cookie.
     * @param name a String specifying the cookie name.
     * @param value a String specifying the cookie value.
     */
    public void addCookie(final String name, final String value)
    {
        //String data = name + "=" + URLEncode(value) + "; path=/";
        String data = name + "=" + value + "; path=/";
        _exchange.getResponseHeaders().add("Set-Cookie", data);
    }
}


