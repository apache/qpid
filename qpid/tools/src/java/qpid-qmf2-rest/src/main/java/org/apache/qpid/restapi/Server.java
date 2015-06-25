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

/**
 * A Server represents a handler that runs within a Web server, which is invoked to process HTTP exchanges.
 * Servers receive and respond to requests from Web clients that are encapsulated into HttpTransactions.
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
public interface Server
{
    /**
     * Called by the Web Server to allow a Server to handle a GET request.
     * <p>
     * The GET method should be safe, that is, without any side effects for which users are held responsible. For   
     * example, most form queries have no side effects. If a client request is intended to change stored data, the 
     * request should use some other HTTP method.
     * <p>
     * The GET method should also be idempotent, meaning that it can be safely repeated. Sometimes making a method safe 
     * also makes it idempotent. For example, repeating queries is both safe and idempotent, but buying a product online 
     * or modifying data is neither safe nor idempotent.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doGet(HttpTransaction tx) throws IOException;

    /**
     * Called by the Web Server to allow a Server to handle a POST request.
     * <p>
     * The HTTP POST method allows the client to send data of unlimited length to the Web server a single time and is 
     * useful when posting information such as credit card numbers. 
     * <p>
     * This method does not need to be either safe or idempotent. Operations requested through POST can have side
     * effects for which the user can be held accountable, for example, updating stored data or buying items online. 
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doPost(HttpTransaction tx) throws IOException;

    /**
     * Called by the Web Server to allow a Server to handle a PUT request.
     * <p>
     * The PUT operation allows a client to place a file on the server and is similar to sending a file by FTP.
     * <p>
     * This method does not need to be either safe or idempotent. Operations that doPut performs can have side effects 
     * for which the user can be held accountable. When using this method, it may be useful to save a copy of the 
     * affected URL in temporary storage. 
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doPut(HttpTransaction tx) throws IOException;

    /**
     * Called by the Web Server to allow a Server to handle a DELETE request.
     * <p>
     * The DELETE operation allows a client to remove a document or Web page from the server.
     * <p>
     * This method does not need to be either safe or idempotent. Operations requested through DELETE can have side 
     * effects for which users can be held accountable. When using this method, it may be useful to save a copy of the 
     * affected URL in temporary storage.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doDelete(HttpTransaction tx) throws IOException;
}


