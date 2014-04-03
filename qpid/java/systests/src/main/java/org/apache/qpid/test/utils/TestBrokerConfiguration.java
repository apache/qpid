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
package org.apache.qpid.test.utils;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryImpl;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.store.MemoryConfigurationEntryStore;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.server.security.group.FileGroupManagerFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TestBrokerConfiguration
{
    public static final String ENTRY_NAME_HTTP_PORT = "http";
    public static final String ENTRY_NAME_AMQP_PORT = "amqp";
    public static final String ENTRY_NAME_RMI_PORT = "rmi";
    public static final String ENTRY_NAME_JMX_PORT = "jmx";
    public static final String ENTRY_NAME_VIRTUAL_HOST = "test";
    public static final String ENTRY_NAME_AUTHENTICATION_PROVIDER = "plain";
    public static final String ENTRY_NAME_EXTERNAL_PROVIDER = "external";
    public static final String ENTRY_NAME_SSL_PORT = "sslPort";
    public static final String ENTRY_NAME_HTTP_MANAGEMENT = "MANAGEMENT-HTTP";
    public static final String MANAGEMENT_HTTP_PLUGIN_TYPE = "MANAGEMENT-HTTP";
    public static final String ENTRY_NAME_JMX_MANAGEMENT = "MANAGEMENT-JMX";
    public static final String MANAGEMENT_JMX_PLUGIN_TYPE = "MANAGEMENT-JMX";
    public static final String ENTRY_NAME_ANONYMOUS_PROVIDER = "anonymous";
    public static final String ENTRY_NAME_SSL_KEYSTORE = "systestsKeyStore";
    public static final String ENTRY_NAME_SSL_TRUSTSTORE = "systestsTrustStore";
    public static final String ENTRY_NAME_GROUP_FILE = "groupFile";
    public static final String ENTRY_NAME_ACL_FILE = "aclFile";

    private MemoryConfigurationEntryStore _store;
    private boolean _saved;

    public TestBrokerConfiguration(String storeType, String intialStoreLocation)
    {
        _store = new MemoryConfigurationEntryStore(intialStoreLocation, null, Collections.<String,String>emptyMap());
    }

    public boolean setBrokerAttribute(String name, Object value)
    {
        return setObjectAttribute(_store.getRootEntry(), name, value);
    }

    public boolean setObjectAttribute(String objectName, String attributeName, Object value)
    {
        ConfigurationEntry entry = findObjectByName(objectName);
        if (entry == null)
        {
            return false;
        }
        return setObjectAttribute(entry, attributeName, value);
    }

    public boolean setObjectAttributes(String objectName, Map<String, Object> attributes)
    {
        ConfigurationEntry entry = findObjectByName(objectName);
        if (entry == null)
        {
            return false;
        }
        return setObjectAttributes(entry, attributes);
    }

    public boolean save(File configFile)
    {
        _store.copyTo(configFile.getAbsolutePath());
        return true;
    }

    public UUID[] removeObjectConfiguration(String name)
    {
        final ConfigurationEntry entry = findObjectByName(name);
        if (entry != null)
        {
            return _store.remove(new ConfiguredObjectRecord()
            {
                @Override
                public UUID getId()
                {
                    return entry.getId();
                }

                @Override
                public String getType()
                {
                    return entry.getType();
                }

                @Override
                public Map<String, Object> getAttributes()
                {
                    return entry.getAttributes();
                }

                @Override
                public Map<String, ConfiguredObjectRecord> getParents()
                {
                    // TODO RG : this should be rectified
                    return null;
                }
            });
        }
        return null;
    }

    public UUID addObjectConfiguration(String name, String type, Map<String, Object> attributes)
    {
        UUID id = UUIDGenerator.generateRandomUUID();
        addObjectConfiguration(id, type, attributes);
        return id;
    }

    public UUID addJmxManagementConfiguration()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ConfiguredObject.TYPE, MANAGEMENT_JMX_PLUGIN_TYPE);
        attributes.put(Plugin.NAME, ENTRY_NAME_JMX_MANAGEMENT);
        return addObjectConfiguration(ENTRY_NAME_JMX_MANAGEMENT, Plugin.class.getSimpleName(), attributes);
    }

    public UUID addHttpManagementConfiguration()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ConfiguredObject.TYPE, MANAGEMENT_HTTP_PLUGIN_TYPE);
        attributes.put(Plugin.NAME, ENTRY_NAME_HTTP_MANAGEMENT);
        return addObjectConfiguration(ENTRY_NAME_HTTP_MANAGEMENT, Plugin.class.getSimpleName(), attributes);
    }

    public UUID addGroupFileConfiguration(String groupFilePath)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, ENTRY_NAME_GROUP_FILE);
        attributes.put(GroupProvider.TYPE, FileGroupManagerFactory.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileGroupManagerFactory.PATH, groupFilePath);

        return addGroupProviderConfiguration(attributes);
    }

    public UUID addAclFileConfiguration(String aclFilePath)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AccessControlProvider.NAME, ENTRY_NAME_ACL_FILE);
        attributes.put(AccessControlProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
        attributes.put(FileAccessControlProviderConstants.PATH, aclFilePath);

        return addAccessControlConfiguration(attributes);
    }

    public UUID addPortConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(Port.NAME);
        return addObjectConfiguration(name, Port.class.getSimpleName(), attributes);
    }

    public UUID addVirtualHostConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(VirtualHost.NAME);
        return addObjectConfiguration(name, VirtualHost.class.getSimpleName(), attributes);
    }

    public UUID addAuthenticationProviderConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(AuthenticationProvider.NAME);
        return addObjectConfiguration(name, AuthenticationProvider.class.getSimpleName(), attributes);
    }

    public UUID addGroupProviderConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(GroupProvider.NAME);
        return addObjectConfiguration(name, GroupProvider.class.getSimpleName(), attributes);
    }

    public UUID addAccessControlConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(AccessControlProvider.NAME);
        return addObjectConfiguration(name, AccessControlProvider.class.getSimpleName(), attributes);
    }

    public UUID addTrustStoreConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(TrustStore.NAME);
        return addObjectConfiguration(name, TrustStore.class.getSimpleName(), attributes);
    }

    public UUID addKeyStoreConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(KeyStore.NAME);
        return addObjectConfiguration(name, KeyStore.class.getSimpleName(), attributes);
    }

    private boolean setObjectAttributes(ConfigurationEntry entry, Map<String, Object> attributes)
    {
        Map<String, Object> newAttributes = new HashMap<String, Object>(entry.getAttributes());
        newAttributes.putAll(attributes);
        ConfigurationEntry newEntry = new ConfigurationEntryImpl(entry.getId(), entry.getType(), newAttributes,
                entry.getChildrenIds(), _store);
        _store.save(newEntry);
        return true;
    }

    private ConfigurationEntry findObjectByName(String objectName)
    {
        ConfigurationEntry root = _store.getRootEntry();
        return findObjectByName(root, objectName);
    }

    private ConfigurationEntry findObjectByName(ConfigurationEntry entry, String objectName)
    {
        Map<String, Object> attributes = entry.getAttributes();
        if (attributes != null)
        {
            String name = (String) attributes.get("name");
            if (objectName.equals(name))
            {
                return entry;
            }
        }
        Set<UUID> childrenIds = entry.getChildrenIds();
        for (UUID uuid : childrenIds)
        {
            ConfigurationEntry child = _store.getEntry(uuid);
            ConfigurationEntry result = findObjectByName(child, objectName);
            if (result != null)
            {
                return result;
            }
        }
        return null;
    }

    private void addObjectConfiguration(UUID id, String type, Map<String, Object> attributes)
    {
        ConfigurationEntry entry = new ConfigurationEntryImpl(id, type, attributes, Collections.<UUID> emptySet(), _store);
        ConfigurationEntry root = _store.getRootEntry();

        Map<String, Collection<ConfigurationEntry>> children = root.getChildren();

        verifyChildWithNameDoesNotExist(id, type, attributes, children);

        Set<UUID> childrenIds = new HashSet<UUID>(root.getChildrenIds());
        childrenIds.add(id);
        ConfigurationEntry
                newRoot = new ConfigurationEntryImpl(root.getId(), root.getType(), root.getAttributes(), childrenIds,
                _store);
        _store.save(newRoot, entry);
    }

    private void verifyChildWithNameDoesNotExist(UUID id, String type,
            Map<String, Object> attributes,
            Map<String, Collection<ConfigurationEntry>> children)
    {
        Collection<ConfigurationEntry> childrenOfType = children.get(type);

        if(childrenOfType != null)
        {
            String name = (String) attributes.get("name");
            for(ConfigurationEntry ce : childrenOfType)
            {
                Object ceName = ce.getAttributes().get("name");
                if(name.equals(ceName) && !id.equals(ce.getId()))
                {
                    throw new IllegalConfigurationException("A " + type + " with name " + name + " already exists with a different ID");
                }
            }
        }
    }

    private boolean setObjectAttribute(ConfigurationEntry entry, String attributeName, Object value)
    {
        Map<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
        attributes.put(attributeName, value);
        ConfigurationEntry
                newEntry = new ConfigurationEntryImpl(entry.getId(), entry.getType(), attributes, entry.getChildrenIds(),
                _store);
        _store.save(newEntry);
        return true;
    }

    public boolean isSaved()
    {
        return _saved;
    }

    public void setSaved(boolean saved)
    {
        _saved = saved;
    }

    public void addPreferencesProviderConfiguration(String authenticationProvider, Map<String, Object> attributes)
    {
        ConfigurationEntry pp = new ConfigurationEntryImpl(UUIDGenerator.generateRandomUUID(),
                PreferencesProvider.class.getSimpleName(), attributes, Collections.<UUID> emptySet(), _store);
        ConfigurationEntry ap = findObjectByName(authenticationProvider);
        Set<UUID> children = new HashSet<UUID>();
        children.addAll(ap.getChildrenIds());
        children.add(pp.getId());
        ConfigurationEntry
                newAp = new ConfigurationEntryImpl(ap.getId(), ap.getType(), ap.getAttributes(), children, _store);
        _store.save(newAp, pp);
    }

    public Map<String, Object> getObjectAttributes(String name)
    {
        return findObjectByName(name).getAttributes();
    }
}
