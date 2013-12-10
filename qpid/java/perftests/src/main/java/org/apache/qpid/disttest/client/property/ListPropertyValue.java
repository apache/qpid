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
 */
package org.apache.qpid.disttest.client.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides property values from the underlining list of items.
 */
public class ListPropertyValue extends GeneratedPropertySupport
{
    public static final String DEF_VALUE = "list";
    private List<PropertyValue> _items;
    private boolean _cyclic;
    private int _currentIndex;

    public ListPropertyValue()
    {
        super();
        _cyclic = true;
        _currentIndex = 0;
        _items = new ArrayList<PropertyValue>();
    }

    public synchronized void setItems(List<PropertyValue> items)
    {
        _items = new ArrayList<PropertyValue>(items);
    }

    public synchronized List<PropertyValue> getItems()
    {
        return Collections.unmodifiableList(_items);
    }

    public synchronized void setCyclic(boolean cyclic)
    {
        _cyclic = cyclic;
    }

    public synchronized boolean isCyclic()
    {
        return _cyclic;
    }

    @Override
    public synchronized Object nextValue()
    {
        if (_currentIndex >= _items.size())
        {
            if (_cyclic)
            {
                _currentIndex = 0;
            }
            else
            {
                _currentIndex = _items.size() -1;
            }
        }
        Object nextValue = _items.get(_currentIndex);
        _currentIndex++;
        return nextValue;
    }

    @Override
    public String getDefinition()
    {
        return DEF_VALUE;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + _currentIndex;
        result = prime * result + (_cyclic ? 1231 : 1237);
        result = prime * result + ((_items == null) ? 0 : _items.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || !(obj instanceof ListPropertyValue))
        {
            return false;
        }
        ListPropertyValue other = (ListPropertyValue) obj;
        if (_cyclic != other._cyclic)
        {
            return false;
        }
        if (_items == null && other._items != null)
        {
             return false;
        }
        return _items.equals(other._items);
    }

}
