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
import java.net.SocketAddress;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.management.plugin.HttpManagementUtil;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;

public class LoggedOnUserPreferencesServlet extends AbstractServlet
{
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException
    {
        PreferencesProvider preferencesProvider = getPreferencesProvider(request);
        if (preferencesProvider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Preferences provider is not configured");
            return;
        }
        String userName = getAuthenticatedUserName(request);
        Map<String, Object> preferences = preferencesProvider.getPreferences(userName);
        if (preferences == null)
        {
            preferences = Collections.<String, Object>emptyMap();
        }
        sendJsonResponse(preferences, request, response);
    }

    /*
     * replace preferences
     */
    @Override
    protected void doPutWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        PreferencesProvider preferencesProvider = getPreferencesProvider(request);
        if (preferencesProvider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Preferences provider is not configured");
            return;
        }
        String userName = getAuthenticatedUserName(request);

        ObjectMapper mapper = new ObjectMapper();

        @SuppressWarnings("unchecked")
        Map<String, Object> newPreferences = mapper.readValue(request.getInputStream(), LinkedHashMap.class);

        preferencesProvider.deletePreferences(userName);
        Map<String, Object> preferences = preferencesProvider.setPreferences(userName, newPreferences);
        if (preferences == null)
        {
            preferences = Collections.<String, Object>emptyMap();
        }
        sendJsonResponse(preferences, request, response);
    }

    /*
     * update preferences
     */
    @Override
    protected void doPostWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        PreferencesProvider preferencesProvider = getPreferencesProvider(request);
        if (preferencesProvider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Preferences provider is not configured");
            return;
        }
        String userName = getAuthenticatedUserName(request);

        ObjectMapper mapper = new ObjectMapper();

        @SuppressWarnings("unchecked")
        Map<String, Object> newPreferences = mapper.readValue(request.getInputStream(), LinkedHashMap.class);
        Map<String, Object> preferences = preferencesProvider.setPreferences(userName, newPreferences);
        if (preferences == null)
        {
            preferences = Collections.<String, Object>emptyMap();
        }
        sendJsonResponse(preferences, request, response);
    }

    private String getAuthenticatedUserName(HttpServletRequest request)
    {
        Subject subject = getAuthorisedSubject(request);
        Principal principal = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
        return principal.getName();
    }

    private PreferencesProvider getPreferencesProvider(HttpServletRequest request)
    {
        SocketAddress localAddress = HttpManagementUtil.getSocketAddress(request);
        AuthenticationProvider authenticationProvider = getManagementConfiguration().getAuthenticationProvider(localAddress);
        if (authenticationProvider == null)
        {
            throw new IllegalStateException("Authentication provider is not found");
        }
        return authenticationProvider.getPreferencesProvider();
    }
}
