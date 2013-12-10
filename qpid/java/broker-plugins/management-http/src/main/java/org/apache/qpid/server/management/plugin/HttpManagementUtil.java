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
package org.apache.qpid.server.management.plugin;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.AccessControlException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.HttpManagementActor;
import org.apache.qpid.server.management.plugin.session.LoginLogoutReporter;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;

public class HttpManagementUtil
{

    /**
     * Servlet context attribute holding a reference to a broker instance
     */
    public static final String ATTR_BROKER = "Qpid.broker";

    /**
     * Servlet context attribute holding a reference to plugin configuration
     */
    public static final String ATTR_MANAGEMENT_CONFIGURATION = "Qpid.managementConfiguration";

    /**
     * Default management entry URL
     */
    public static final String ENTRY_POINT_PATH = "/management";

    private static final String ATTR_LOGIN_LOGOUT_REPORTER = "Qpid.loginLogoutReporter";
    private static final String ATTR_SUBJECT = "Qpid.subject";
    private static final String ATTR_LOG_ACTOR = "Qpid.logActor";

    public static Broker getBroker(ServletContext servletContext)
    {
        return (Broker) servletContext.getAttribute(ATTR_BROKER);
    }

    public static HttpManagementConfiguration getManagementConfiguration(ServletContext servletContext)
    {
        return (HttpManagementConfiguration) servletContext.getAttribute(ATTR_MANAGEMENT_CONFIGURATION);
    }

    public static SocketAddress getSocketAddress(HttpServletRequest request)
    {
        return InetSocketAddress.createUnresolved(request.getServerName(), request.getServerPort());
    }

    public static Subject getAuthorisedSubject(HttpSession session)
    {
        return (Subject) session.getAttribute(ATTR_SUBJECT);
    }

    public static void checkRequestAuthenticatedAndAccessAuthorized(HttpServletRequest request, Broker broker,
            HttpManagementConfiguration managementConfig)
    {
        HttpSession session = request.getSession();
        Subject subject = getAuthorisedSubject(session);
        if (subject == null)
        {
            subject = tryToAuthenticate(request, managementConfig);
            if (subject == null)
            {
                throw new SecurityException("Only authenticated users can access the management interface");
            }
            LogActor actor = createHttpManagementActor(broker, request);
            if (hasAccessToManagement(broker.getSecurityManager(), subject, actor))
            {
                saveAuthorisedSubject(session, subject, actor);
            }
            else
            {
                throw new AccessControlException("Access to the management interface denied");
            }
        }
    }

    public static boolean hasAccessToManagement(final SecurityManager securityManager, Subject subject, LogActor actor)
    {
        // TODO: We should eliminate SecurityManager.setThreadSubject in favour of Subject.doAs
        SecurityManager.setThreadSubject(subject); // Required for accessManagement check
        CurrentActor.set(actor);
        try
        {
            try
            {
                return Subject.doAs(subject, new PrivilegedExceptionAction<Boolean>()
                {
                    @Override
                    public Boolean run() throws Exception
                    {
                        return securityManager.accessManagement();
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

    public static HttpManagementActor getOrCreateAndCacheLogActor(HttpServletRequest request, Broker broker)
    {
        HttpSession session = request.getSession();
        HttpManagementActor actor = (HttpManagementActor) session.getAttribute(ATTR_LOG_ACTOR);
        if (actor == null)
        {
            actor = createHttpManagementActor(broker, request);
            session.setAttribute(ATTR_LOG_ACTOR, actor);
        }
        return actor;
    }

    public static void saveAuthorisedSubject(HttpSession session, Subject subject, LogActor logActor)
    {
        session.setAttribute(ATTR_SUBJECT, subject);

        // Cause the user logon to be logged.
        session.setAttribute(ATTR_LOGIN_LOGOUT_REPORTER, new LoginLogoutReporter(logActor, subject));
    }

    private static Subject tryToAuthenticate(HttpServletRequest request, HttpManagementConfiguration managementConfig)
    {
        Subject subject = null;
        SocketAddress localAddress = getSocketAddress(request);
        SubjectCreator subjectCreator = managementConfig.getAuthenticationProvider(localAddress).getSubjectCreator();
        String remoteUser = request.getRemoteUser();

        if (remoteUser != null || subjectCreator.isAnonymousAuthenticationAllowed())
        {
            subject = authenticateUser(subjectCreator, remoteUser, null);
        }
        else
        {
            String header = request.getHeader("Authorization");
            if (header != null)
            {
                String[] tokens = header.split("\\s");
                if (tokens.length >= 2 && "BASIC".equalsIgnoreCase(tokens[0]))
                {
                    boolean isBasicAuthSupported = false;
                    if (request.isSecure())
                    {
                        isBasicAuthSupported = managementConfig.isHttpsBasicAuthenticationEnabled();
                    }
                    else
                    {
                        isBasicAuthSupported = managementConfig.isHttpBasicAuthenticationEnabled();
                    }
                    if (isBasicAuthSupported)
                    {
                        String base64UsernameAndPassword = tokens[1];
                        String[] credentials = (new String(Base64.decodeBase64(base64UsernameAndPassword.getBytes()))).split(":", 2);
                        if (credentials.length == 2)
                        {
                            subject = authenticateUser(subjectCreator, credentials[0], credentials[1]);
                        }
                    }
                }
            }
        }
        return subject;
    }

    private static Subject authenticateUser(SubjectCreator subjectCreator, String username, String password)
    {
        SubjectAuthenticationResult authResult = subjectCreator.authenticate(username, password);
        if (authResult.getStatus() == AuthenticationStatus.SUCCESS)
        {
            return authResult.getSubject();
        }
        return null;
    }

    private static HttpManagementActor createHttpManagementActor(Broker broker, HttpServletRequest request)
    {
        return new HttpManagementActor(broker.getRootMessageLogger(), request.getRemoteAddr(), request.getRemotePort());
    }

}
