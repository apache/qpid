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

import java.io.IOException;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.apache.qpid.restapi.HttpTransaction;
import org.apache.qpid.restapi.Server;

/**
 * Delegator is an implementation of com.sun.net.httpserver.HttpHandler that is used to delegate to Server objects
 * and thus provide an abstraction between the Web Server implementation (e.g. HttpServer or Servlets) and the
 * implementation neutral Server and HttpTransaction interfaces.
 * <p>
 * In order to replace the HttpServer implementation with a Servlet based implementation all that should be necessary
 * is a concrete implementation of HttpTransaction that wraps HttpServletRequest and HttpServletResponse and subclasses
 * of HttpServlet that delegate to the appropriate Server instances from the appropriate context in a similar way
 * to the Delegator class here.
 * 
 * @author Fraser Adams
 */
public class Delegator implements HttpHandler
{
    private final Server _server;

    /**
     * Construct a Delegator instance that delegates to the specified Server instance.
     * @param server the Server instance that this Delegator delegates to.
     */
    public Delegator(Server server)
    {
        _server = server;
    }

    /**
     * Implements the HttpHandler handle interface and delegates to the Server instance.
     * @param exchange the HttpExchange exchange object passed by the HttpServer. This will be used to construct
     *        an HttpTransaction instance that will be passed to the Server.
     */
    public void handle(final HttpExchange exchange) throws IOException
    {
        HttpTransaction tx = new HttpExchangeTransaction(exchange);
        String method = tx.getMethod();
        if (method.equals("GET"))
        {
            _server.doGet(tx);
        }
        else if (method.equals("POST"))
        {
            _server.doPost(tx);
        }
        else if (method.equals("PUT"))
        {
            _server.doPut(tx);
        }
        else if (method.equals("DELETE"))
        {
            _server.doDelete(tx);
        }
        else
        {
            tx.sendResponse(HTTP_BAD_METHOD, "text/plain", "405 Bad Method.");
        }
    }
}


