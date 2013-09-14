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

public class UserPreferencesServlet extends AbstractServlet
{
    private static final Logger LOGGER = Logger.getLogger(UserPreferencesServlet.class);
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException
    {
        String[] pathElements = null;
        if (request.getPathInfo() != null && request.getPathInfo().length() > 0)
        {
            pathElements = request.getPathInfo().substring(1).split("/");
        }
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
        Map<String, Object> preferences = null;
        try
        {
            preferences = getUserPreferences(authenticationProviderName, userId);
        }
        catch (Exception e)
        {
            LOGGER.debug("Bad preferences request", e);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
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

    private Map<String, Object> getUserPreferences(String authenticationProviderName, String userId)
    {
        AuthenticationProvider authenticationProvider = getAuthenticationProvider(authenticationProviderName);
        if (authenticationProvider == null)
        {
            throw new IllegalArgumentException(String.format("Authentication provider '%s' is not found",
                    authenticationProviderName));
        }
        PreferencesProvider preferencesProvider = authenticationProvider.getPreferencesProvider();
        if (preferencesProvider == null)
        {
            throw new IllegalStateException(String.format(
                    "Preferences provider is not set for authentication provider '%s'", authenticationProviderName));
        }
        return preferencesProvider.getPreferences(userId);
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
    protected void doDeleteWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response)
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
                                preferencesProvider.deletePreferences(userId);
                            }
                        }
                        break;
                    }
                }
            }
        }

    }
}
