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
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.HttpManagementActor;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.management.plugin.session.LoginLogoutReporter;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;

public abstract class AbstractServlet extends HttpServlet
{
    private static final Logger LOGGER = Logger.getLogger(AbstractServlet.class);

    /**
     * Servlet context attribute holding a reference to a broker instance
     */
    public static final String ATTR_BROKER = "Qpid.broker";

    /**
     * Servlet context attribute holding a reference to plugin configuration
     */
    public static final String ATTR_MANAGEMENT = "Qpid.management";

    private static final String ATTR_LOGIN_LOGOUT_REPORTER = "AbstractServlet.loginLogoutReporter";
    private static final String ATTR_SUBJECT = "AbstractServlet.subject";
    private static final String ATTR_LOG_ACTOR = "AbstractServlet.logActor";

    private Broker _broker;
    private RootMessageLogger _rootLogger;
    private HttpManagement _httpManagement;

    protected AbstractServlet()
    {
        super();
    }

    @Override
    public void init() throws ServletException
    {
        ServletConfig servletConfig = getServletConfig();
        ServletContext servletContext = servletConfig.getServletContext();
        _broker = (Broker)servletContext.getAttribute(ATTR_BROKER);
        _rootLogger = _broker.getRootMessageLogger();
        _httpManagement = (HttpManagement)servletContext.getAttribute(ATTR_MANAGEMENT);
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
            subject = getAndCacheAuthorizedSubject(request);
        }
        catch (AccessControlException e)
        {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        SecurityManager.setThreadSubject(subject);
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
            try
            {
                SecurityManager.setThreadSubject(null);
            }
            finally
            {
                AMQShortString.clearLocalCache();
            }
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
        Subject subject = getAuthorisedSubjectFromSession(session);

        if(subject != null)
        {
            return subject;
        }

        SubjectCreator subjectCreator = getSubjectCreator(request);
        subject = authenticate(request, subjectCreator);
        if (subject != null)
        {
            authoriseManagement(request, subject);
            setAuthorisedSubjectInSession(subject, request, session);
        }
        else
        {
            subject = subjectCreator.createSubjectWithGroups(AnonymousAuthenticationManager.ANONYMOUS_USERNAME);
        }

        return subject;
    }

    protected void authoriseManagement(HttpServletRequest request, Subject subject)
    {
        // TODO: We should eliminate SecurityManager.setThreadSubject in favour of Subject.doAs
        SecurityManager.setThreadSubject(subject);  // Required for accessManagement check
        LogActor actor = createHttpManagementActor(request);
        CurrentActor.set(actor);
        try
        {
            try
            {
                Subject.doAs(subject, new PrivilegedExceptionAction<Void>() // Required for proper logging of Subject
                {
                    @Override
                    public Void run() throws Exception
                    {
                        boolean allowed = getSecurityManager().accessManagement();
                        if (!allowed)
                        {
                            throw new AccessControlException("User is not authorised for management");
                        }
                        return null;
                    }
                });
            }
            catch (PrivilegedActionException e)
            {
                throw new RuntimeException("Unable to perform access check", e);
            }
        }
        finally
        {
            try
            {
                CurrentActor.remove();
            }
            finally
            {
                SecurityManager.setThreadSubject(null);
            }
        }
    }

    private Subject authenticate(HttpServletRequest request, SubjectCreator subjectCreator)
    {
        Subject subject = null;

        String remoteUser = request.getRemoteUser();
        if(remoteUser != null)
        {
            subject = authenticateUserAndGetSubject(subjectCreator, remoteUser, null);
        }
        else
        {
            String header = request.getHeader("Authorization");

            if (header != null)
            {
                String[] tokens = header.split("\\s");
                if(tokens.length >= 2 && "BASIC".equalsIgnoreCase(tokens[0]))
                {
                    if(!isBasicAuthSupported(request))
                    {
                        //TODO: write a return response indicating failure?
                        throw new IllegalArgumentException("BASIC Authorization is not enabled.");
                    }

                    subject = performBasicAuth(subject, subjectCreator, tokens[1]);
                }
            }
        }

        return subject;
    }

    private Subject performBasicAuth(Subject subject,SubjectCreator subjectCreator, String base64UsernameAndPassword)
    {
        String[] credentials = (new String(Base64.decodeBase64(base64UsernameAndPassword.getBytes()))).split(":",2);
        if(credentials.length == 2)
        {
            subject = authenticateUserAndGetSubject(subjectCreator, credentials[0], credentials[1]);
        }
        else
        {
            //TODO: write a return response indicating failure?
            throw new AccessControlException("Invalid number of credentials supplied: "
                                            + credentials.length);
        }
        return subject;
    }

    private Subject authenticateUserAndGetSubject(SubjectCreator subjectCreator, String username, String password)
    {
        SubjectAuthenticationResult authResult = subjectCreator.authenticate(username, password);
        if( authResult.getStatus() != AuthenticationStatus.SUCCESS)
        {
            //TODO: write a return response indicating failure?
            throw new AccessControlException("Incorrect username or password");
        }
        Subject subject = authResult.getSubject();
        return subject;
    }

    private boolean isBasicAuthSupported(HttpServletRequest req)
    {
        return req.isSecure()  ? _httpManagement.isHttpsBasicAuthenticationEnabled()
                : _httpManagement.isHttpBasicAuthenticationEnabled();
    }

    private HttpManagementActor getLogActorAndCacheInSession(HttpServletRequest req)
    {
        HttpSession session = req.getSession();

        HttpManagementActor actor = (HttpManagementActor) session.getAttribute(ATTR_LOG_ACTOR);
        if(actor == null)
        {
            actor = createHttpManagementActor(req);
            session.setAttribute(ATTR_LOG_ACTOR, actor);
        }

        return actor;
    }

    protected Subject getAuthorisedSubjectFromSession(HttpSession session)
    {
        return (Subject)session.getAttribute(ATTR_SUBJECT);
    }

    protected void setAuthorisedSubjectInSession(Subject subject, HttpServletRequest request, final HttpSession session)
    {
        session.setAttribute(ATTR_SUBJECT, subject);

        LogActor logActor = createHttpManagementActor(request);
        // Cause the user logon to be logged.
        session.setAttribute(ATTR_LOGIN_LOGOUT_REPORTER, new LoginLogoutReporter(logActor, subject));
    }

    protected Broker getBroker()
    {
        return _broker;
    }

    protected SocketAddress getSocketAddress(HttpServletRequest request)
    {
        return InetSocketAddress.createUnresolved(request.getServerName(), request.getServerPort());
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

    private HttpManagementActor createHttpManagementActor(HttpServletRequest request)
    {
        return new HttpManagementActor(_rootLogger, request.getRemoteAddr(), request.getRemotePort());
    }

    protected HttpManagement getManagement()
    {
        return _httpManagement;
    }

    protected SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    protected SubjectCreator getSubjectCreator(HttpServletRequest request)
    {
        return _broker.getSubjectCreator(getSocketAddress(request));
    }
}
