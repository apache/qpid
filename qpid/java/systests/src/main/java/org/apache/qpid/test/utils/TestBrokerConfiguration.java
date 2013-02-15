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

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.PluginFactory;

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

    private JsonConfigurationEntryStore _store;
    private boolean _saved;

    public TestBrokerConfiguration(String storeType, String intialStoreLocation)
    {
        // TODO: add support for DERBY store
        _store = new JsonConfigurationEntryStore();

        try
        {
            // load the initial store data into our store
            _store.load(new File(intialStoreLocation).toURI().toURL());
        }
        catch (MalformedURLException e)
        {
            // ignore
        }
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
        _store.saveTo(configFile);
        return true;
    }

    public UUID[] removeObjectConfiguration(String name)
    {
        ConfigurationEntry entry = findObjectByName(name);
        if (entry != null)
        {
            return _store.remove(entry.getId());
        }
        return null;
    }

    public UUID addObjectConfiguration(String name, String type, Map<String, Object> attributes)
    {
        UUID id = UUIDGenerator.generateBrokerChildUUID(type, name);
        addObjectConfiguration(id, type, attributes);
        return id;
    }

    public UUID addJmxManagementConfiguration()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(PluginFactory.PLUGIN_TYPE, MANAGEMENT_JMX_PLUGIN_TYPE);
        attributes.put(Plugin.NAME, ENTRY_NAME_JMX_MANAGEMENT);
        return addObjectConfiguration(ENTRY_NAME_JMX_MANAGEMENT, Plugin.class.getSimpleName(), attributes);
    }

    public UUID addHttpManagementConfiguration()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(PluginFactory.PLUGIN_TYPE, MANAGEMENT_HTTP_PLUGIN_TYPE);
        attributes.put(Plugin.NAME, ENTRY_NAME_HTTP_MANAGEMENT);
        return addObjectConfiguration(ENTRY_NAME_HTTP_MANAGEMENT, Plugin.class.getSimpleName(), attributes);
    }

    public UUID addPortConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(Port.NAME);
        return addObjectConfiguration(name, Port.class.getSimpleName(), attributes);
    }

    public UUID addHostConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(VirtualHost.NAME);
        return addObjectConfiguration(name, VirtualHost.class.getSimpleName(), attributes);
    }

    public UUID addAuthenticationProviderConfiguration(Map<String, Object> attributes)
    {
        String name = (String) attributes.get(AuthenticationProvider.NAME);
        return addObjectConfiguration(name, AuthenticationProvider.class.getSimpleName(), attributes);
    }

    private boolean setObjectAttributes(ConfigurationEntry entry, Map<String, Object> attributes)
    {
        Map<String, Object> newAttributes = new HashMap<String, Object>(entry.getAttributes());
        newAttributes.putAll(attributes);
        ConfigurationEntry newEntry = new ConfigurationEntry(entry.getId(), entry.getType(), newAttributes,
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
        ConfigurationEntry entry = new ConfigurationEntry(id, type, attributes, Collections.<UUID> emptySet(), _store);
        ConfigurationEntry root = _store.getRootEntry();
        Set<UUID> childrenIds = new HashSet<UUID>(root.getChildrenIds());
        childrenIds.add(id);
        ConfigurationEntry newRoot = new ConfigurationEntry(root.getId(), root.getType(), root.getAttributes(), childrenIds,
                _store);
        _store.save(newRoot, entry);
    }

    private boolean setObjectAttribute(ConfigurationEntry entry, String attributeName, Object value)
    {
        Map<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
        attributes.put(attributeName, value);
        ConfigurationEntry newEntry = new ConfigurationEntry(entry.getId(), entry.getType(), attributes, entry.getChildrenIds(),
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

}
