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
package org.apache.qpid.server.jmx;

public class JMXConfiguration
{
    private final boolean _platformMBeanServer;
    private final String _managementKeyStorePath;
    private final String  _managementKeyStorePassword;
    private final boolean _managementRightsInferAllAccess;

    public JMXConfiguration( boolean platformMBeanServer, String managementKeyStorePath,
            String managementKeyStorePassword, boolean managementRightsInferAllAccess)
    {
        _platformMBeanServer = platformMBeanServer;
        _managementKeyStorePath = managementKeyStorePath;
        _managementKeyStorePassword = managementKeyStorePassword;
        _managementRightsInferAllAccess = managementRightsInferAllAccess;
    }

    public boolean isPlatformMBeanServer()
    {
        return _platformMBeanServer;
    }

    public String getManagementKeyStorePath()
    {
        return _managementKeyStorePath;
    }

    public String getManagementKeyStorePassword()
    {
        return _managementKeyStorePassword;
    }

    public boolean isManagementRightsInferAllAccess()
    {
        return _managementRightsInferAllAccess;
    }

}
