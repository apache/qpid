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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.AccessControlException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.HttpManagementActor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;

public abstract class AbstractServlet extends HttpServlet
{
    private static final Logger LOGGER = Logger.getLogger(AbstractServlet.class);

    protected static final String ATTR_SUBJECT = "subject";
    private static final String ATTR_LOG_ACTOR = "AbstractServlet.logActor";

    private final Broker _broker;

    private RootMessageLogger _rootLogger;

    protected AbstractServlet()
    {
        super();
        _broker = ApplicationRegistry.getInstance().getBroker();
        _rootLogger = ApplicationRegistry.getInstance().getRootMessageLogger();
    }

    protected AbstractServlet(Broker broker)
    {
        _broker = broker;
        _rootLogger = ApplicationRegistry.getInstance().getRootMessageLogger();
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
        Subject subject = getAndCacheAuthorizedSubject(request);
        org.apache.qpid.server.security.SecurityManager.setThreadSubject(subject);

        try
        {
            HttpManagementActor logActor = getLogActorAndCacheInSession(request);
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
            org.apache.qpid.server.security.SecurityManager.setThreadSubject(null);
        }
    }

    /**
     * Gets the logged-in {@link Subject} by trying the following:
     *
     * <ul>
     * <li>Get it from the session</li>
     * <li>Get it from the request</li>
     * <li>Log in using the username and password in the Authorization HTTP header</li>
     * <li>Create a Subject representing the anonymous user.</li>
     * </ul>
     *
     * If an authenticated subject is found it is cached in the http session.
     */
    private Subject getAndCacheAuthorizedSubject(HttpServletRequest request)
    {
        HttpSession session = request.getSession();
        Subject subject = getSubjectFromSession(session);

        if(subject != null)
        {
            return subject;
        }

        SubjectCreator subjectCreator = ApplicationRegistry.getInstance().getSubjectCreator(getSocketAddress(request));

        String remoteUser = request.getRemoteUser();
        if(remoteUser != null)
        {
            subject = subjectCreator.createSubjectWithGroups(remoteUser);
        }
        else
        {
            String header = request.getHeader("Authorization");

            /*
             * TODO - Should configure whether basic authentication is allowed... and in particular whether it
             * should be allowed over non-ssl connections
             * */

            if (header != null)
            {
                String[] tokens = header.split("\\s");
                if(tokens.length >= 2
                        && "BASIC".equalsIgnoreCase(tokens[0]))
                {
                    String[] credentials = (new String(Base64.decodeBase64(tokens[1].getBytes()))).split(":",2);
                    if(credentials.length == 2)
                    {
                        SubjectAuthenticationResult authResult = subjectCreator.authenticate(credentials[0], credentials[1]);
                        if( authResult.getStatus() != AuthenticationStatus.SUCCESS)
                        {
                            //TODO: write a return response indicating failure?
                            throw new AccessControlException("Incorrect username or password");
                        }
                        subject = authResult.getSubject();
                    }
                    else
                    {
                        //TODO: write a return response indicating failure?
                        throw new AccessControlException("Invalid number of credentials supplied: "
                                                        + credentials.length);
                    }
                }
            }
        }

        if (subject != null)
        {
            setSubjectInSession(subject, session);
        }
        else
        {
            subject = subjectCreator.createSubjectWithGroups(AnonymousAuthenticationManager.ANONYMOUS_USERNAME);
        }

        return subject;
    }

    private HttpManagementActor getLogActorAndCacheInSession(HttpServletRequest req)
    {
        HttpSession session = req.getSession();

        HttpManagementActor actor = (HttpManagementActor) session.getAttribute(ATTR_LOG_ACTOR);
        if(actor == null)
        {
            actor = new HttpManagementActor(_rootLogger, req.getRemoteAddr(), req.getRemotePort());
            session.setAttribute(ATTR_LOG_ACTOR, actor);
        }

        return actor;
    }

    protected Subject getSubjectFromSession(HttpSession session)
    {
        return (Subject)session.getAttribute(ATTR_SUBJECT);
    }

    protected void setSubjectInSession(Subject subject, final HttpSession session)
    {
        session.setAttribute(ATTR_SUBJECT, subject);
    }

    protected Broker getBroker()
    {
        return _broker;
    }

    protected SocketAddress getSocketAddress(HttpServletRequest request)
    {
        return InetSocketAddress.createUnresolved(request.getServerName(), request.getServerPort());
    }
}
