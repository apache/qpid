/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.util;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Utility class used by AMQChannel to retrieve unacknowledged
 * messages. Made generic to avoid exposing the inner class in
 * AMQChannel. Put in this package to keep ot out the way.
 */
public class OrderedMapHelper<K, V>
{
    private final Map<K, V> _map;
    private final Object _lock;
    private final K _wildcard;

    public OrderedMapHelper(Map<K, V> map, Object lock, K wildcard)
    {
        _map = map;
        _lock = lock;
        _wildcard = wildcard;
    }

    /**
     * Assumes the map passed in is ordered.  Returns a copy of the
     * map containing an individual key-value pair or a list of
     * key-values upto and including the one matching the given
     * key. If multiple == true and the key == the wildcard specified
     * on construction, then all the values in the map will be
     * returned.
     */
    public Map<K, V> getValues(K key, boolean multiple) throws NoSuchElementException
    {
        if (multiple)
        {
            if(key == _wildcard)
            {
                synchronized(_lock)
                {
                    return new LinkedHashMap<K, V>(_map);
                }
            }
            else
            {
                return getValues(key);
            }
        }
        else
        {
            Map<K, V> values = new LinkedHashMap<K, V>();
            values.put(key, getValue(key));
            return values;
        }
    }

    private V getValue(K key) throws NoSuchElementException
    {
        V value;
        synchronized(_lock)
        {
            value = _map.get(key);
        }

        if(value == null)
        {
            throw new NoSuchElementException();
        }
        else
        {
            return value;
        }
    }

    private Map<K, V> getValues(K key) throws NoSuchElementException
    {
        Map<K, V> values = new LinkedHashMap<K, V>();
        synchronized(_lock)
        {
            if (!_map.containsKey(key))
            {
                throw new NoSuchElementException();
            }
            
            for(Map.Entry<K, V> entry : _map.entrySet())
            {
                values.put(entry.getKey(), entry.getValue());
                if (entry.getKey() == key)
                {
                    break;
                }                        
            }
        }
        return values;
    }

}