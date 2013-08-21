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
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.HttpManagementActor;
import org.apache.qpid.server.management.plugin.HttpManagementConfiguration;
import org.apache.qpid.server.management.plugin.HttpManagementUtil;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SecurityManager;

public abstract class AbstractServlet extends HttpServlet
{
    private static final Logger LOGGER = Logger.getLogger(AbstractServlet.class);

    private Broker _broker;
    private HttpManagementConfiguration _managementConfiguration;

    protected AbstractServlet()
    {
        super();
    }

    @Override
    public void init() throws ServletException
    {
        ServletConfig servletConfig = getServletConfig();
        ServletContext servletContext = servletConfig.getServletContext();
        _broker = HttpManagementUtil.getBroker(servletContext);
        _managementConfiguration = HttpManagementUtil.getManagementConfiguration(servletContext);
        super.init();
    }

    @Override
    protected final void doGet(final HttpServletRequest request, final HttpServletResponse resp)
    {
        doWithSubjectAndActor(
            new PrivilegedExceptionAction<Void>()
            {
                @Override
                public Void run() throws Exception
                {
                    doGetWithSubjectAndActor(request, resp);
                    return null;
                }
            },
            request,
            resp
        );
    }

    /**
     * Performs the GET action as the logged-in {@link Subject}.
     * The {@link LogActor} is set before this method is called.
     * Subclasses commonly override this method
     */
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException
    {
        throw new UnsupportedOperationException("GET not supported by this servlet");
    }


    @Override
    protected final void doPost(final HttpServletRequest request, final HttpServletResponse resp)
    {
        doWithSubjectAndActor(
            new PrivilegedExceptionAction<Void>()
            {
                @Override
                public Void run()  throws Exception
                {
                    doPostWithSubjectAndActor(request, resp);
                    return null;
                }
            },
            request,
            resp
        );
    }

    /**
     * Performs the POST action as the logged-in {@link Subject}.
     * The {@link LogActor} is set before this method is called.
     * Subclasses commonly override this method
     */
    protected void doPostWithSubjectAndActor(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        throw new UnsupportedOperationException("POST not supported by this servlet");
    }

    @Override
    protected final void doPut(final HttpServletRequest request, final HttpServletResponse resp)
    {
        doWithSubjectAndActor(
            new PrivilegedExceptionAction<Void>()
            {
                @Override
                public Void run() throws Exception
                {
                    doPutWithSubjectAndActor(request, resp);
                    return null;
                }
            },
            request,
            resp
        );
    }

    /**
     * Performs the PUT action as the logged-in {@link Subject}.
     * The {@link LogActor} is set before this method is called.
     * Subclasses commonly override this method
     */
    protected void doPutWithSubjectAndActor(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        throw new UnsupportedOperationException("PUT not supported by this servlet");
    }

    @Override
    protected final void doDelete(final HttpServletRequest request, final HttpServletResponse resp)
            throws ServletException, IOException
    {
        doWithSubjectAndActor(
            new PrivilegedExceptionAction<Void>()
            {
                @Override
                public Void run() throws Exception
                {
                    doDeleteWithSubjectAndActor(request, resp);
                    return null;
                }
            },
            request,
            resp
        );
    }

    /**
     * Performs the PUT action as the logged-in {@link Subject}.
     * The {@link LogActor} is set before this method is called.
     * Subclasses commonly override this method
     */
    protected void doDeleteWithSubjectAndActor(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        throw new UnsupportedOperationException("DELETE not supported by this servlet");
    }

    private void doWithSubjectAndActor(
                    PrivilegedExceptionAction<Void> privilegedExceptionAction,
                    final HttpServletRequest request,
                    final HttpServletResponse resp)
    {
        Subject subject;
        try
        {
            subject = getAuthorisedSubject(request);
        }
        catch (SecurityException e)
        {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        SecurityManager.setThreadSubject(subject);
        try
        {
            HttpManagementActor logActor = HttpManagementUtil.getOrCreateAndCacheLogActor(request, _broker);
            CurrentActor.set(logActor);
            try
            {
                Subject.doAs(subject, privilegedExceptionAction);
            }
            catch(RuntimeException e)
            {
                LOGGER.error("Unable to perform action", e);
                throw e;
            }
            catch (PrivilegedActionException e)
            {
                LOGGER.error("Unable to perform action", e);
                throw new RuntimeException(e.getCause());
            }
            finally
            {
                CurrentActor.remove();
            }
        }
        finally
        {
            SecurityManager.setThreadSubject(null);
        }
    }

    protected Subject getAuthorisedSubject(HttpServletRequest request)
    {
        Subject subject = HttpManagementUtil.getAuthorisedSubject(request.getSession());
        if (subject == null)
        {
            throw new SecurityException("Access to management rest interfaces is denied for un-authorised user");
        }
        return subject;
    }

    protected Broker getBroker()
    {
        return _broker;
    }

    protected HttpManagementConfiguration getManagementConfiguration()
    {
        return _managementConfiguration;
    }

    protected void sendError(final HttpServletResponse resp, int errorCode)
    {
        try
        {
            resp.sendError(errorCode);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to send error response code " + errorCode, e);
        }
    }
}
