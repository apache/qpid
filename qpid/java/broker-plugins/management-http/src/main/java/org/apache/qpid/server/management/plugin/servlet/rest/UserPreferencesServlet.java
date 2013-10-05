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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.access.Operation;

public class UserPreferencesServlet extends AbstractServlet
{
    private static final Logger LOGGER = Logger.getLogger(UserPreferencesServlet.class);
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException
    {
        String[] pathElements = getPathInfoElements(request);
        if (pathElements != null && pathElements.length > 1)
        {
            getUserPreferences(pathElements[0], pathElements[1], response);
        }
        else
        {
            getUserList(pathElements, response);
        }
    }

    private void getUserPreferences(String authenticationProviderName, String userId, HttpServletResponse response)
            throws IOException
    {
        if (!userPreferencesOperationAuthorized(userId))
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Vieweing of preferences is not allowed");
            return;
        }
        Map<String, Object> preferences = null;
        PreferencesProvider preferencesProvider = getPreferencesProvider(authenticationProviderName);
        if (preferencesProvider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Preferences provider is not configured");
            return;
        }
        preferences =  preferencesProvider.getPreferences(userId);

        sendJsonResponse(preferences, response);
    }

    private void getUserList(String[] pathElements, HttpServletResponse response) throws IOException
    {
        List<Map<String, Object>> users = null;
        try
        {
            users = getUsers(pathElements);
        }
        catch (Exception e)
        {
            LOGGER.debug("Bad preferences request", e);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
        sendJsonResponse(users, response);
    }

    private PreferencesProvider getPreferencesProvider(String authenticationProviderName)
    {
        AuthenticationProvider authenticationProvider = getAuthenticationProvider(authenticationProviderName);
        if (authenticationProvider == null)
        {
            throw new IllegalArgumentException(String.format("Authentication provider '%s' is not found",
                    authenticationProviderName));
        }
        PreferencesProvider preferencesProvider = authenticationProvider.getPreferencesProvider();
        return preferencesProvider;
    }

    private AuthenticationProvider getAuthenticationProvider(String authenticationProviderName)
    {
        Broker broker = getBroker();
        Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
        for (AuthenticationProvider authenticationProvider : authenticationProviders)
        {
            if (authenticationProviderName.equals(authenticationProvider.getName()))
            {
                return authenticationProvider;
            }
        }
        return null;
    }

    private List<Map<String, Object>> getUsers(String[] pathElements)
    {
        List<Map<String, Object>> users = new ArrayList<Map<String, Object>>();
        String authenticationProviderName = pathElements != null && pathElements.length > 0 ? pathElements[0] : null;

        Broker broker = getBroker();
        Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
        for (AuthenticationProvider authenticationProvider : authenticationProviders)
        {
            if (authenticationProviderName != null && !authenticationProvider.getName().equals(authenticationProviderName))
            {
                continue;
            }
            PreferencesProvider preferencesProvider = authenticationProvider.getPreferencesProvider();
            if (preferencesProvider != null)
            {
                Set<String> usernames = preferencesProvider.listUserIDs();
                for (String name : usernames)
                {
                    Map<String, Object> userMap = new HashMap<String, Object>();
                    userMap.put(User.NAME, name);
                    userMap.put("authenticationProvider", authenticationProvider.getName());
                    users.add(userMap);
                }
            }
        }
        return users;
    }

    /*
     * removes preferences
     */
    @Override
    protected void doDeleteWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        final List<String[]> userData = new ArrayList<String[]>();
        for (String name : request.getParameterValues("user"))
        {
            String[] elements = name.split("/");
            if (elements.length != 2)
            {
                throw new IllegalArgumentException("Illegal parameter");
            }
            userData.add(elements);
        }

        if (!userData.isEmpty())
        {
            Broker broker = getBroker();
            Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
            for (Iterator<String[]> it = userData.iterator(); it.hasNext();)
            {
                String[] data = (String[]) it.next();
                String authenticationProviderName = data[0];
                String userId = data[1];

                for (AuthenticationProvider authenticationProvider : authenticationProviders)
                {
                    if (authenticationProviderName.equals(authenticationProvider.getName()))
                    {
                        PreferencesProvider preferencesProvider = authenticationProvider.getPreferencesProvider();
                        if (preferencesProvider != null)
                        {
                            Set<String> usernames = preferencesProvider.listUserIDs();
                            if (usernames.contains(userId))
                            {
                                if (!userPreferencesOperationAuthorized(userId))
                                {
                                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Deletion of preferences is not allowed");
                                    return;
                                }
                                preferencesProvider.deletePreferences(userId);
                            }
                        }
                        break;
                    }
                }
            }
        }

    }

    private boolean userPreferencesOperationAuthorized(String userId)
    {
        return getBroker().getSecurityManager().authoriseUserOperation(Operation.UPDATE, userId);
    }
}
