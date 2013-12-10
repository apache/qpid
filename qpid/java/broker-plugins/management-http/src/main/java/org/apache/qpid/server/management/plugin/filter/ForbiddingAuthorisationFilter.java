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
import java.security.AccessControlException;

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

public class ForbiddingAuthorisationFilter implements Filter
{
    public static String INIT_PARAM_ALLOWED = "allowed";
    private String _allowed = null;

    private Broker _broker;
    private HttpManagementConfiguration _managementConfiguration;

    @Override
    public void destroy()
    {
    }

    @Override
    public void init(FilterConfig config) throws ServletException
    {
        String allowed = config.getInitParameter(INIT_PARAM_ALLOWED);
        if (allowed != null)
        {
            _allowed = allowed;
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
        String servletPath = httpRequest.getServletPath();
        if (_allowed == null || "".equals(_allowed) || servletPath.indexOf(_allowed) == -1)
        {
            try
            {
                HttpManagementUtil.checkRequestAuthenticatedAndAccessAuthorized(httpRequest, _broker, _managementConfiguration);
            }
            catch(AccessControlException e)
            {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            catch(SecurityException e)
            {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

}
