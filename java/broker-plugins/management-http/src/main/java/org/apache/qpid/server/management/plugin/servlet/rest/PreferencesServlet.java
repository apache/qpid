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

import org.apache.qpid.server.management.plugin.HttpManagementUtil;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.codehaus.jackson.map.ObjectMapper;

public class PreferencesServlet extends AbstractServlet
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
        sendJsonResponse(preferences, response);
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
        sendJsonResponse(preferences, response);
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
            throw new IllegalStateException("Preferences provider is not configured");
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
        sendJsonResponse(preferences, response);
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
