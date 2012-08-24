/*
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
package org.apache.qpid.server.security.group;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.auth.UsernamePrincipal;

/**
 * Implementation of a group manager whose implementation is backed by a flat group file.
 * <p>
 * This plugin is configured in the following manner:
 * </p>
 * <pre>
 * &lt;file-group-manager&gt;
 *    &lt;attributes&gt;
 *       &lt;attribute&gt;
 *            &lt;name>groupFile&lt;/name&gt;
 *            &lt;value>${conf}/groups&lt;/value&gt;
 *        &lt;/attribute&gt;
 *    &lt;/attributes&gt;
 * &lt;/file-group-manager&gt;
 * </pre>
 */
public class FileGroupManager implements GroupManager
{
    private static final Logger LOGGER = Logger.getLogger(FileGroupManager.class);

    public static final GroupManagerPluginFactory<FileGroupManager> FACTORY = new GroupManagerPluginFactory<FileGroupManager>()
    {
        public FileGroupManager newInstance(final ConfigurationPlugin config) throws ConfigurationException
        {
            final FileGroupManagerConfiguration configuration =
                    config == null
                            ? null
                            : (FileGroupManagerConfiguration) config.getConfiguration(FileGroupManagerConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                LOGGER.info("No file-group-manager configuration found for FileGroupManager");
                return null;
            }

            final FileGroupManager fgm = new FileGroupManager();
            fgm.configure(configuration);
            return fgm;
        }

        public Class<FileGroupManager> getPluginClass()
        {
            return FileGroupManager.class;
        }

        public String getPluginName()
        {
            return FileGroupManager.class.getName();
        }
    };

    private FileGroupDatabase _groupDatabase;

    public static class FileGroupManagerConfiguration extends ConfigurationPlugin {

        public static final ConfigurationPluginFactory FACTORY = new ConfigurationPluginFactory()
        {
            public List<String> getParentPaths()
            {
                return Arrays.asList("security.file-group-manager");
            }

            public ConfigurationPlugin newInstance(final String path, final Configuration config) throws ConfigurationException
            {
                final ConfigurationPlugin instance = new FileGroupManagerConfiguration();

                instance.setConfiguration(path, config);
                return instance;
            }
        };

        public String[] getElementsProcessed()
        {
            return new String[] {"attributes.attribute.name",
                                 "attributes.attribute.value"};
        }

        public void validateConfiguration() throws ConfigurationException
        {
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Map<String,String> getAttributeMap() throws ConfigurationException
        {
            final List<String> argumentNames = (List) getConfig().getList("attributes.attribute.name");
            final List<String> argumentValues = (List) getConfig().getList("attributes.attribute.value");
            final Map<String,String> attributes = new HashMap<String,String>(argumentNames.size());

            for (int i = 0; i < argumentNames.size(); i++)
            {
                final String argName = argumentNames.get(i);
                final String argValue = argumentValues.get(i);

                attributes.put(argName, argValue);
            }

            return Collections.unmodifiableMap(attributes);
        }
    }

    @Override
    public void configure(ConfigurationPlugin config)
            throws ConfigurationException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("configuring file group plugin");
        }

        FileGroupManagerConfiguration fileGroupMangerConfig = (FileGroupManagerConfiguration) config;
        Map<String,String> attribMap = fileGroupMangerConfig.getAttributeMap();
        String groupFile = attribMap.get("groupFile");

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Group file : " + groupFile);
        }

        _groupDatabase = new FileGroupDatabase();
        try
        {
            _groupDatabase.setGroupFile(groupFile);
        }
        catch (IOException e)
        {
            throw new ConfigurationException("Unable to set group file " + groupFile, e);
        }
    }

    @Override
    public Set<Principal> getGroupPrincipalsForUser(String userId)
    {
        Set<String> groups = _groupDatabase.getGroupsForUser(userId);
        if (groups.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String groupName : groups)
            {
                principals.add(new GroupPrincipal(groupName));
            }
            return principals;
        }
    }

    @Override
    public Set<Principal> getUserPrincipalsForGroup(String group)
    {
        Set<String> users = _groupDatabase.getUsersInGroup(group);
        if (users.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String user : users)
            {
                principals.add(new UsernamePrincipal(user));
            }
            return principals;
        }
    }

    @Override
    public Set<Principal> getGroupPrincipals()
    {
        Set<String> groups = _groupDatabase.getAllGroups();
        if (groups.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String groupName : groups)
            {
                principals.add(new GroupPrincipal(groupName));
            }
            return principals;
        }
    }

    @Override
    public void createGroup(String group)
    {
        _groupDatabase.createGroup(group);
    }

    @Override
    public void removeGroup(String group)
    {
        _groupDatabase.removeGroup(group);
    }

    @Override
    public void addUserToGroup(String user, String group)
    {
        _groupDatabase.addUserToGroup(user, group);
    }

    @Override
    public void removeUserFromGroup(String user, String group)
    {
        _groupDatabase.removeUserFromGroup(user, group);

    }

}
