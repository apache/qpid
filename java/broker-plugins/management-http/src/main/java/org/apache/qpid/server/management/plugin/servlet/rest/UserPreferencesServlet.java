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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.access.Operation;

public class UserPreferencesServlet extends AbstractServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferencesServlet.class);
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException
    {
        String[] pathElements = getPathInfoElements(request);
        if (pathElements != null && pathElements.length > 1)
        {
            getUserPreferences(pathElements[0], pathElements[1], request, response);
        }
        else
        {
            getUserList(pathElements, request, response);
        }
    }

    private void getUserPreferences(String authenticationProviderName, String userId, HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        Map<String, Object> preferences = null;
        PreferencesProvider preferencesProvider = getPreferencesProvider(authenticationProviderName);
        if (preferencesProvider == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Preferences provider is not configured");
            return;
        }
        try
        {
            preferences =  preferencesProvider.getPreferences(userId);

            sendJsonResponse(preferences, request, response);
        }
        catch (SecurityException e)
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Viewing of preferences is not allowed");
            return;
        }
    }

    private void getUserList(String[] pathElements, HttpServletRequest request, HttpServletResponse response) throws IOException
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
        sendJsonResponse(users, request, response);
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
        Broker broker = getBroker();
        Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
        Map<String, Set<String>> providerUsers = new HashMap<String, Set<String>>();
        Map<String, AuthenticationProvider> requestProviders = new HashMap<String, AuthenticationProvider>();
        for (String path : request.getParameterValues("user"))
        {
            String[] elements = path.split("/");
            if (elements.length != 2)
            {
                throw new IllegalArgumentException("Illegal user parameter " + path);
            }

            String userId = elements[1];

            String providerName =  elements[0];
            Set<String> users = providerUsers.get(providerName);

            if (users == null)
            {
                AuthenticationProvider provider = findAuthenticationProviderByName(providerName, authenticationProviders);
                if (provider == null)
                {
                    throw new IllegalArgumentException("Cannot find provider with name '" + providerName + "'");
                }
                users = new HashSet<String>();
                providerUsers.put(providerName, users);
                requestProviders.put(providerName, provider);
            }
            users.add(userId);
        }

        if (!providerUsers.isEmpty())
        {
            for (Map.Entry<String, Set<String>> entry : providerUsers.entrySet())
            {
                String providerName = entry.getKey();
                AuthenticationProvider provider = requestProviders.get(providerName);
                Set<String> usersToDelete = entry.getValue();
                PreferencesProvider preferencesProvider = provider.getPreferencesProvider();

                if (preferencesProvider != null && !usersToDelete.isEmpty())
                {
                    String[] users = usersToDelete.toArray(new String[usersToDelete.size()]);
                    try
                    {
                        preferencesProvider.deletePreferences(users);
                    }
                    catch (SecurityException e)
                    {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Deletion of preferences is not allowed");
                        return;
                    }
                }
            }
        }

    }

    protected AuthenticationProvider findAuthenticationProviderByName(String providerName, Collection<AuthenticationProvider> authenticationProviders)
    {
        AuthenticationProvider provider = null;
        for (AuthenticationProvider authenticationProvider : authenticationProviders)
        {
            if(authenticationProvider.getName().equals(providerName))
            {
                provider = authenticationProvider;
                break;
            }
        }
        return provider;
    }

}
