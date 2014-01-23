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
package org.apache.qpid.server.management.plugin.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.server.management.plugin.HttpManagementConfiguration;
import org.apache.qpid.server.management.plugin.HttpManagementUtil;
import org.apache.qpid.server.model.Broker;

public class RedirectingAuthorisationFilter implements Filter
{
    public static String DEFAULT_LOGIN_URL = "login.html";
    public static String INIT_PARAM_LOGIN_URL = "login-url";

    private String _loginUrl = DEFAULT_LOGIN_URL;
    private Broker _broker;
    private HttpManagementConfiguration _managementConfiguration;

    @Override
    public void destroy()
    {
    }

    @Override
    public void init(FilterConfig config) throws ServletException
    {
        String loginUrl = config.getInitParameter(INIT_PARAM_LOGIN_URL);
        if (loginUrl != null)
        {
            _loginUrl = loginUrl;
        }
        ServletContext servletContext = config.getServletContext();
        _broker = HttpManagementUtil.getBroker(servletContext);
        _managementConfiguration = HttpManagementUtil.getManagementConfiguration(servletContext);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException
    {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        try
        {
            HttpManagementUtil.checkRequestAuthenticatedAndAccessAuthorized(httpRequest, _broker, _managementConfiguration);
            chain.doFilter(request, response);
        }
        catch(SecurityException e)
        {
            httpResponse.sendRedirect(_loginUrl);
        }
    }

}
