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
package org.apache.qpid.server.filter;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FilterManager
{

    private final Map<String, MessageFilter> _filters = new ConcurrentHashMap<>();

    public FilterManager()
    {
    }

    public void add(String name, MessageFilter filter)
    {
        _filters.put(name, filter);
    }

    public boolean allAllow(Filterable msg)
    {
        for (MessageFilter filter : _filters.values())
        {
            if (!filter.matches(msg))
            {
                return false;
            }
        }
        return true;
    }

    public boolean startAtTail()
    {
        for(MessageFilter filter : _filters.values())
        {
            if(filter.startAtTail())
            {
                return true;
            }
        }
        return false;
    }

    public Iterator<MessageFilter> filters()
    {
        return _filters.values().iterator();
    }

    public boolean hasFilters()
    {
        return !_filters.isEmpty();
    }

    public boolean hasFilter(final String name)
    {
        return _filters.containsKey(name);
    }

    public boolean hasFilter(final MessageFilter filter)
    {
        return _filters.containsValue(filter);
    }

    @Override
    public String toString()
    {
        return _filters.toString();
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final FilterManager that = (FilterManager) o;

        if (_filters != null ? !_filters.equals(that._filters) : that._filters != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return _filters != null ? _filters.hashCode() : 0;
    }
}
