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
package org.apache.qpid.restapi;

import java.io.IOException;
import java.io.InputStream;

/**
 * An HttpTransaction encapsulates an HTTP request received and a response to be generated in one HTTP request/response
 * "transaction". It provides methods for examining the request from the client, and for building and sending the 
 * response from the server.
 * <p>
 * Note that the HttpTransaction interface isn't intended to be completely general, rather it is intended to abstract
 * the services needed in org.apache.qpid.restapi in such a way as to be neutral as to whether the Web Server is
 * based on com.sun.net.httpserver.HttpServer or javax.servlet.http.HttpServlet.
 * <p>
 * The Server and HttpTransaction interfaces are intended to provide abstractions to enable the "business logic" to
 * be isolated from the actual Web Server implementation choice, so for example a concrete HttpTransaction implementation
 * could be created by wrapping a com.sun.net.httpserver.HttpExchange, but equally another implementation could wrap
 * javax.servlet.http.HttpServletRequest and javax.servlet.http.HttpServletResponse so for example an HttpServlet
 * could delegate to a Server instance passing the HttpTransaction it constructed from the HttpServletRequest and
 * HttpServletResponse.
 *
 * @author Fraser Adams
 */
public interface HttpTransaction
{
    /**
     * Log the HTTP request information (primarily for debugging purposes)
     */
    public void logRequest();

    /**
     * Return the content passed in the request from the client as a Stream.
     * @return the content passed in the request from the client as a Stream.
     */
    public InputStream getRequestStream() throws IOException;

    /**
     * Return the content passed in the request from the client as a String.
     * @return the content passed in the request from the client as a String.
     */
    public String getRequestString() throws IOException;

    /**
     * Return the content passed in the request from the client as a byte[].
     * @return the content passed in the request from the client as a byte[].
     */
    public byte[] getRequest() throws IOException;

    /**
     * Send the content passed as a String as an HTTP response back to the client.
     * @param status the HTTP status code e.g. 200 for OK.
     * @param mimeType the mimeType of the response content e.g. text/plain, text/xml, image/jpeg etc.
     * @param content the content of the response passed as a String.
     */
    public void sendResponse(final int status, final String mimeType, final String content) throws IOException;

    /**
     * Send the content passed as a byte[] as an HTTP response back to the client.
     * @param status the HTTP status code e.g. 200 for OK.
     * @param mimeType the mimeType of the response content e.g. text/plain, text/xml, image/jpeg etc.
     * @param content the content of the response passed as a byte[].
     */
    public void sendResponse(final int status, final String mimeType, final byte[] content) throws IOException;

    /**
     * Send the content passed as an InputStream as an HTTP response back to the client.
     * @param status the HTTP status code e.g. 200 for OK.
     * @param mimeType the mimeType of the response content e.g. text/plain, text/xml, image/jpeg etc.
     * @param is the content of the response passed as an InputStream.
     */
    public void sendResponse(final int status, final String mimeType, final InputStream is) throws IOException;

    /**
     * Returns the Internet Protocol (IP) address of the client or last proxy that sent the request.
     * @return the Internet Protocol (IP) address of the client or last proxy that sent the request.
     */
    public String getRemoteAddr();

    /**
     * Returns the fully qualified name of the client or the last proxy that sent the request.
     * @return the fully qualified name of the client or the last proxy that sent the request.
     */
    public String getRemoteHost();

    /**
     * Returns the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     * @return the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     */
    public int getRemotePort();

    /**
     * Returns a String containing the name of the current authenticated user. If the user has not been authenticated, 
     * the method returns null.
     * @return a String containing the name of the user making this request; null if the user has not been authenticated.
     */
    public String getPrincipal();

    /**
     * Returns the name of the HTTP method with which this request was made, for example, GET, POST, or PUT.
     * @return a String specifying the name of the method with which this request was made.
     */
    public String getMethod();

    /**
     * Returns the part of this request's URL from the protocol name up to the query string in the first line of
     * the HTTP request.
     * @return a String containing the part of the URL from the protocol name up to the query string.
     */
    public String getRequestURI();

    /**
     * Sets a response header with the given name and value. If the header had already been set, the new value
     * overwrites the previous one.
     * @param name a String specifying the header name.
     * @param value a String specifying the header value. If it contains octet string, it should be encoded according
     *        to RFC 2047.
     */
    public void setHeader(final String name, final String value);

    /**
     * Returns the value of the specified request header as a String. If the request did not include a header of the 
     * specified name, this method returns null. If there are multiple headers with the same name, this method returns 
     * the first head in the request. The header name is case insensitive. You can use this method with any request 
     * header.
     * @param name a String specifying the header name.
     * @return a String containing the value of the requested header, or null if the request does not have a header of 
     *         that name.
     */
    public String getHeader(final String name);

    /**
     * Returns the String value of the specified cookie.
     * @param name a String specifying the cookie name.
     */
    public String getCookie(final String name);

    /**
     * Adds the specified cookie to the response. This method can be called multiple times to set more than one cookie.
     * @param name a String specifying the cookie name.
     * @param value a String specifying the cookie value.
     */
    public void addCookie(final String name, final String value);
}


