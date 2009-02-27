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
package org.apache.qpid.server.configuration.management;

import javax.management.NotCompliantMBeanException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.registry.ApplicationRegistry;

public class ConfigurationManagementMBean extends AMQManagedObject implements ConfigurationManagement
{

    public ConfigurationManagementMBean() throws NotCompliantMBeanException
    {
        super(ConfigurationManagement.class, ConfigurationManagement.TYPE, ConfigurationManagement.VERSION);
    }

    @Override
    public String getObjectInstanceName()
    {
        return ConfigurationManagement.TYPE;
    }

    @Override
    public void reloadSecurityConfiguration() throws ConfigurationException
    {
        ApplicationRegistry.getInstance().getConfiguration().reparseConfigFile();
    }

}
