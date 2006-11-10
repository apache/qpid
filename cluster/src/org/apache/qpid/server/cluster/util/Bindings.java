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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Maps two separate keys to a list of values.
 *
 */
public class Bindings<K1, K2, V>
{
    private final MultiValuedMap<K1, Binding<K2>> _a = new MultiValuedMap<K1, Binding<K2>>();
    private final MultiValuedMap<K2, Binding<K1>> _b = new MultiValuedMap<K2, Binding<K1>>();
    private final Collection<V> _values = new HashSet<V>();

    public void bind(K1 key1, K2 key2, V value)
    {
        _a.add(key1, new Binding<K2>(key2, value));
        _b.add(key2, new Binding<K1>(key1, value));
        _values.add(value);
    }

    public void unbind1(K1 key1)
    {
        Collection<Binding<K2>> values = _a.remove(key1);
        for (Binding<K2> v : values)
        {
            _b.remove(v.key);
            _values.remove(v.value);
        }
    }

    public void unbind2(K2 key2)
    {
        Collection<Binding<K1>> values = _b.remove(key2);
        for (Binding<K1> v : values)
        {
            _a.remove(v.key);
            _values.remove(v.value);
        }
    }

    public Collection<V> values()
    {
        return Collections.unmodifiableCollection(_values);
    }

    /**
     * Value needs to hold key to the other map
     */
    private class Binding<T>
    {
        private final T key;
        private final V value;

        Binding(T key, V value)
        {
            this.key = key;
            this.value = value;
        }
    }
}
