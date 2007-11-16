/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.access;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.configuration.PropertyUtils;
import org.apache.log4j.Logger;

import java.util.List;
import java.lang.reflect.Method;
import java.security.Principal;

public class AccessManagerImpl implements AccessManager
{
    private static final Logger _logger = Logger.getLogger(AccessManagerImpl.class);

    AccessManager _accessManager;

    public AccessManagerImpl(String name, Configuration hostConfig) throws ConfigurationException
    {
        if (hostConfig == null)
        {
            _logger.warn("No Configuration specified. Using default access controls for VirtualHost:'" + name + "'");
            return;
        }

        String accessClass = hostConfig.getString("security.access.class");
        if (accessClass == null)
        {
            _logger.warn("No access control specified. Using default access controls for VirtualHost:'" + name + "'");
            return;
        }

        Object o;
        try
        {
            o = Class.forName(accessClass).newInstance();
        }
        catch (Exception e)
        {
            throw new ConfigurationException("Error initialising access control: " + e, e);
        }

        if (!(o instanceof AccessManager))
        {
            throw new ConfigurationException("Access control must implement the VirtualHostAccess interface");
        }

        initialiseAccessControl((AccessManager) o, hostConfig);

        _accessManager = (AccessManager) o;

        _logger.info("Initialised access control for virtualhost '" + name + "' successfully");

    }


    private void initialiseAccessControl(AccessManager accessManager, Configuration config)
            throws ConfigurationException
    {
        String baseName = "security.access.attributes.attribute.";
        List<String> argumentNames = config.getList(baseName + "name");
        List<String> argumentValues = config.getList(baseName + "value");
        for (int i = 0; i < argumentNames.size(); i++)
        {
            String argName = argumentNames.get(i);
            if (argName == null || argName.length() == 0)
            {
                throw new ConfigurationException("Access Control argument names must have length >= 1 character");
            }
            if (Character.isLowerCase(argName.charAt(0)))
            {
                argName = Character.toUpperCase(argName.charAt(0)) + argName.substring(1);
            }
            String methodName = "set" + argName;
            Method method = null;
            try
            {
                method = accessManager.getClass().getMethod(methodName, String.class);
            }
            catch (NoSuchMethodException e)
            {
                //do nothing as method will be null
            }

            if (method == null)
            {
                throw new ConfigurationException("No method " + methodName + " found in class " + accessManager.getClass() +
                                                 " hence unable to configure access control. The method must be public and " +
                                                 "have a single String argument with a void return type");
            }
            try
            {
                method.invoke(accessManager, PropertyUtils.replaceProperties(argumentValues.get(i)));
            }
            catch (Exception e)
            {
                ConfigurationException ce = new ConfigurationException(e.getMessage(), e.getCause());
                ce.initCause(e);
                throw ce;
            }
        }
    }

    public AccessResult isAuthorized(Accessable accessObject, String username)
    {
        return isAuthorized(accessObject, new UsernamePrincipal(username), AccessRights.Rights.READ);
    }

    public AccessResult isAuthorized(Accessable accessObject, Principal user, AccessRights.Rights rights)
    {
        if (_accessManager == null)
        {
            if (ApplicationRegistry.getInstance().getAccessManager() == this)
            {
                _logger.warn("No Default access manager specified DENYING ALL ACCESS");
                return new AccessResult(this, AccessResult.AccessStatus.REFUSED);
            }
            else
            {
                return ApplicationRegistry.getInstance().getAccessManager().isAuthorized(accessObject, user, rights);
            }
        }
        else
        {
            return _accessManager.isAuthorized(accessObject, user, rights);
        }
    }

    public String getName()
    {
        return "AccessManagerImpl";
    }
}
