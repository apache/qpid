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
package org.apache.qpid.server.model.configuration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHost;

public class ConfigurationEntryTest extends TestCase
{
    public void testGetChildren()
    {
        ConfigurationEntryStore store = mock(ConfigurationEntryStore.class);

        ConfigurationEntry virtualHostEntry1 = new ConfigurationEntry(UUID.randomUUID(), VirtualHost.class.getSimpleName(),
                Collections.<String, Object> emptyMap(), Collections.<UUID> emptySet(), store);
        ConfigurationEntry virtualHostEntry2 = new ConfigurationEntry(UUID.randomUUID(), VirtualHost.class.getSimpleName(),
                Collections.<String, Object> emptyMap(), Collections.<UUID> emptySet(), store);
        ConfigurationEntry portEntry = new ConfigurationEntry(UUID.randomUUID(), Port.class.getSimpleName(),
                Collections.<String, Object> emptyMap(), Collections.<UUID> emptySet(), store);

        when(store.getEntry(virtualHostEntry1.getId())).thenReturn(virtualHostEntry1);
        when(store.getEntry(virtualHostEntry2.getId())).thenReturn(virtualHostEntry2);
        when(store.getEntry(portEntry.getId())).thenReturn(portEntry);

        Set<UUID> childrenIds = new HashSet<UUID>();
        childrenIds.add(virtualHostEntry1.getId());
        childrenIds.add(virtualHostEntry2.getId());
        childrenIds.add(portEntry.getId());
        ConfigurationEntry parentEntry = new ConfigurationEntry(UUID.randomUUID(), Broker.class.getSimpleName(),
                Collections.<String, Object> emptyMap(), childrenIds, store);

        Map<String, Collection<ConfigurationEntry>> children = parentEntry.getChildren();
        assertNotNull("Null is returned for children", children);
        assertEquals("Unexpected size", 2, children.size());
        Collection<ConfigurationEntry> virtualHosts = children.get(VirtualHost.class.getSimpleName());
        Collection<ConfigurationEntry> ports = children.get(Port.class.getSimpleName());
        assertEquals("Unexpected virtual hosts",
                new HashSet<ConfigurationEntry>(Arrays.asList(virtualHostEntry1, virtualHostEntry2)),
                new HashSet<ConfigurationEntry>(virtualHosts));
        assertEquals("Unexpected ports", new HashSet<ConfigurationEntry>(Arrays.asList(portEntry)),
                new HashSet<ConfigurationEntry>(ports));
    }

    public void testHashCode()
    {
        ConfigurationEntryStore store = mock(ConfigurationEntryStore.class);

        UUID id = UUID.randomUUID();
        ConfigurationEntry entry1 = new ConfigurationEntry(id, VirtualHost.class.getSimpleName(),
                Collections.<String, Object> emptyMap(), Collections.singleton(UUID.randomUUID()), store);
        ConfigurationEntry entry2 = new ConfigurationEntry(id, VirtualHost.class.getSimpleName(),
                Collections.<String, Object> emptyMap(), Collections.singleton(UUID.randomUUID()), store);
        ConfigurationEntry entryWithDifferentId = new ConfigurationEntry(UUID.randomUUID(),
                VirtualHost.class.getSimpleName(), Collections.<String, Object> emptyMap(), Collections.singleton(UUID.randomUUID()), store);

        assertTrue(entry1.hashCode() == entry2.hashCode());
        assertFalse(entry1.hashCode() == entryWithDifferentId.hashCode());
    }

    public void testEqualsObject()
    {
        ConfigurationEntryStore store = mock(ConfigurationEntryStore.class);

        UUID id = UUID.randomUUID();
        Map<String, Object> attributes1 = new HashMap<String, Object>();
        attributes1.put(VirtualHost.NAME, "name1");
        Set<UUID> childrenIds = Collections.singleton(UUID.randomUUID());
        ConfigurationEntry entry1 = new ConfigurationEntry(id, VirtualHost.class.getSimpleName(), attributes1,
                childrenIds, store);

        Map<String, Object> attributes2 = new HashMap<String, Object>();
        attributes2.put(VirtualHost.NAME, "name2");

        ConfigurationEntry entry2 = new ConfigurationEntry(id, VirtualHost.class.getSimpleName(), attributes1,
                childrenIds, store);
        ConfigurationEntry entryWithDifferentId = new ConfigurationEntry(UUID.randomUUID(),
                VirtualHost.class.getSimpleName(), attributes1, childrenIds, store);

        assertTrue(entry1.equals(entry2));
        assertFalse("Entries should be diferrent because of diferrent IDs", entry1.equals(entryWithDifferentId));

        ConfigurationEntry entryWithDifferentChildId = new ConfigurationEntry(id,
                VirtualHost.class.getSimpleName(), attributes1, Collections.singleton(UUID.randomUUID()), store);
        assertFalse("Entries should be diferrent because of diferrent children", entry1.equals(entryWithDifferentChildId));

        ConfigurationEntry entryWithDifferentName = new ConfigurationEntry(id,
                VirtualHost.class.getSimpleName(), attributes2, childrenIds, store);
        assertFalse("Entries should be diferrent because of diferrent attributes", entry1.equals(entryWithDifferentName));

        ConfigurationEntry entryWithDifferentType = new ConfigurationEntry(id,
                Broker.class.getSimpleName(), attributes1, childrenIds, store);
        assertFalse("Entries should be diferrent because of diferrent types", entry1.equals(entryWithDifferentType));
    }
}
