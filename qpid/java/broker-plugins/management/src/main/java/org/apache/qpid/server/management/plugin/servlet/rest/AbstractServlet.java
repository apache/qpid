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

package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.codec.binary.Base64;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;

public abstract class AbstractServlet extends HttpServlet
{
    private Subject _subject;
    private final Broker _broker;

    protected AbstractServlet(Broker broker)
    {
        _broker = broker;
    }

    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException
    {
        setAuthorizedSubject(request);
        try
        {
            onGet(request, resp);
        }
        finally
        {
            clearAuthorizedSubject();
        }
    }

    protected void onGet(HttpServletRequest request, HttpServletResponse resp) throws IOException, ServletException
    {
        super.doGet(request, resp);
    }

    private void clearAuthorizedSubject()
    {
        _subject = null;
        org.apache.qpid.server.security.SecurityManager.setThreadSubject(null);
    }


    private void setAuthorizedSubject(HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        Subject subject = (Subject) session.getAttribute("subject");

        if(subject == null)
        {
            Principal principal = request.getUserPrincipal();
            if(principal != null)
            {
                subject = new Subject(false, Collections.singleton(principal),Collections.emptySet(),
                                      Collections.emptySet());
            }
            else
            {
                String header = request.getHeader("Authorization");
                if (header != null)
                {
                    String[] tokens = header.split("\\s");
                    if(tokens.length >= 2
                       && "BASIC".equalsIgnoreCase(tokens[0]))
                    {
                        String[] credentials = (new String(Base64.decodeBase64(tokens[1].getBytes()))).split(":",2);
                        if(credentials.length == 2)
                        {


                            AuthenticationManager authenticationManager =
                                    ApplicationRegistry.getInstance().getAuthenticationManager();
                            AuthenticationResult authResult =
                                    authenticationManager.authenticate(credentials[0], credentials[1]);
                            subject = authResult.getSubject();

                        }
                    }
                }
            }
        }
        _subject = subject;
        org.apache.qpid.server.security.SecurityManager.setThreadSubject(subject);

    }

    protected Subject getSubject()
    {
        return _subject;
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        setAuthorizedSubject(req);
        try
        {
            onPost(req, resp);
        }
        finally
        {
            clearAuthorizedSubject();
        }

    }

    protected void onPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        super.doPost(req, resp);
    }

    @Override
    protected final void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        setAuthorizedSubject(req);
        try
        {
            onPut(req, resp);

        }
        finally
        {
            clearAuthorizedSubject();
        }
    }

    protected void onPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException
    {
        super.doPut(req,resp);
    }

    @Override
    protected final void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        setAuthorizedSubject(req);
        try
        {
            onDelete(req, resp);
        }
        finally
        {
            clearAuthorizedSubject();
        }
    }

    protected void onDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        super.doDelete(req, resp);
    }


    protected Broker getBroker()
    {
        return _broker;
    }


}
