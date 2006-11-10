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
package org.apache.qpid.server.cluster.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps a key to a collection of values
 *
 */
public class MultiValuedMap<K, V>
{
    private Map<K, Collection<V>> _map = new HashMap<K, Collection<V>>();

    public boolean add(K key, V value)
    {
        Collection<V> values = get(key);
        if (values == null)
        {
            values = createList();
            _map.put(key, values);
        }
        return values.add(value);
    }

    public Collection<V> get(K key)
    {
        return _map.get(key);
    }

    public Collection<V> remove(K key)
    {
        return _map.remove(key);
    }

    protected Collection<V> createList()
    {
        return new ArrayList<V>();
    }
}
