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

public class HttpConfiguration
{
    private final int _sessionTimeout;
    private final boolean _httpBasicAuthenticationEnabled;
    private final boolean _httpsBasicAuthenticationEnabled;
    private final boolean _httpSaslAuthenticationEnabled;
    private final boolean _httpsSaslAuthenticationEnabled;

    private final String _keyStorePath;
    private final String _keyStorePassword;

    public HttpConfiguration(int sessionTimeout, boolean httpBasicAuthenticationEnabled, boolean httpsBasicAuthenticationEnabled,
            boolean httpSaslAuthenticationEnabled, boolean httpsSaslAuthenticationEnabled, String keyStorePath, String keyStorePassword)
    {
        super();
        _sessionTimeout = sessionTimeout;
        _httpBasicAuthenticationEnabled = httpBasicAuthenticationEnabled;
        _httpsBasicAuthenticationEnabled = httpsBasicAuthenticationEnabled;
        _httpSaslAuthenticationEnabled = httpSaslAuthenticationEnabled;
        _httpsSaslAuthenticationEnabled = httpsSaslAuthenticationEnabled;
        _keyStorePath = keyStorePath;
        _keyStorePassword = keyStorePassword;
    }

    public int getSessionTimeout()
    {
        return _sessionTimeout;
    }

    public boolean isHttpSaslAuthenticationEnabled()
    {
        return _httpSaslAuthenticationEnabled;
    }

    public boolean isHttpBasicAuthenticationEnabled()
    {
        return _httpBasicAuthenticationEnabled;
    }

    public boolean isHttpsSaslAuthenticationEnabled()
    {
        return _httpsSaslAuthenticationEnabled;
    }

    public boolean isHttpsBasicAuthenticationEnabled()
    {
        return _httpsBasicAuthenticationEnabled;
    }

    public String getKeyStorePath()
    {
        return _keyStorePath;
    }

    public String getKeyStorePassword()
    {
        return _keyStorePassword;
    }

}
