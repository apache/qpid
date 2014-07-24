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
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;

import org.apache.qpid.server.management.plugin.servlet.ServletConnectionPrincipal;
import org.apache.qpid.server.management.plugin.session.LoginLogoutReporter;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.ExternalAuthenticationManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

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

    public static Broker<?> getBroker(ServletContext servletContext)
    {
        return (Broker<?>) servletContext.getAttribute(ATTR_BROKER);
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

            Subject original = subject;
            subject = new Subject(false,
                                  original.getPrincipals(),
                                  original.getPublicCredentials(),
                                  original.getPrivateCredentials());
            subject.getPrincipals().add(new ServletConnectionPrincipal(request));
            subject.setReadOnly();

            assertManagementAccess(broker.getSecurityManager(), subject);

            saveAuthorisedSubject(session, subject);


        }
    }

    public static void assertManagementAccess(final SecurityManager securityManager, Subject subject)
    {
        Subject.doAs(subject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                securityManager.accessManagement();
                return null;
            }
        });
    }

    public static void saveAuthorisedSubject(HttpSession session, Subject subject)
    {
        session.setAttribute(ATTR_SUBJECT, subject);

        // Cause the user logon to be logged.
        session.setAttribute(ATTR_LOGIN_LOGOUT_REPORTER,
                             new LoginLogoutReporter(subject, getBroker(session.getServletContext())));
    }

    public static Subject tryToAuthenticate(HttpServletRequest request, HttpManagementConfiguration managementConfig)
    {
        Subject subject = null;
        SocketAddress localAddress = getSocketAddress(request);
        final AuthenticationProvider authenticationProvider = managementConfig.getAuthenticationProvider(localAddress);
        SubjectCreator subjectCreator = authenticationProvider.getSubjectCreator(request.isSecure());
        String remoteUser = request.getRemoteUser();

        if (remoteUser != null || authenticationProvider instanceof AnonymousAuthenticationManager)
        {
            subject = authenticateUser(subjectCreator, remoteUser, null);
        }
        else if(authenticationProvider instanceof ExternalAuthenticationManager
                && Collections.list(request.getAttributeNames()).contains("javax.servlet.request.X509Certificate"))
        {
            Principal principal = null;
            X509Certificate[] certificates =
                    (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
            if(certificates != null && certificates.length != 0)
            {
                principal = certificates[0].getSubjectX500Principal();

                if(!Boolean.valueOf(String.valueOf(authenticationProvider.getAttribute(ExternalAuthenticationManager.ATTRIBUTE_USE_FULL_DN))))
                {
                    String username;
                    String dn = ((X500Principal) principal).getName(X500Principal.RFC2253);


                    username = SSLUtil.getIdFromSubjectDN(dn);
                    principal = new  UsernamePrincipal(username);
                }

                subject = subjectCreator.createSubjectWithGroups(new AuthenticatedPrincipal(principal));
            }
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


}
