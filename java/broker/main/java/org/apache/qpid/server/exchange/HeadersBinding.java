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
package org.apache.qpid.server.exchange;

import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines binding and matching based on a set of headers.
 */
class HeadersBinding
{
    private static final Logger _logger = Logger.getLogger(HeadersBinding.class);

    private final Map _mappings = new HashMap();
    private final Set<Object> required = new HashSet<Object>();
    private final Set<Map.Entry> matches = new HashSet<Map.Entry>();
    private boolean matchAny;

    /**
     * Creates a binding for a set of mappings. Those mappings whose value is
     * null or the empty string are assumed only to be required headers, with
     * no constraint on the value. Those with a non-null value are assumed to
     * define a required match of value. 
     * @param mappings the defined mappings this binding should use
     */
    HeadersBinding(Map mappings)
    {
        //noinspection unchecked
        this(mappings == null ? new HashSet<Map.Entry>() : mappings.entrySet());
        _mappings.putAll(mappings);
    }

    private HeadersBinding(Set<Map.Entry> entries)
    {
        for (Map.Entry e : entries)
        {
            if (isSpecial(e.getKey()))
            {
                processSpecial((String) e.getKey(), e.getValue());
            }
            else if (e.getValue() == null || e.getValue().equals(""))
            {
                required.add(e.getKey());
            }
            else
            {
                matches.add(e);
            }
        }
    }

    protected Map getMappings()
    {
        return _mappings;
    }

    /**
     * Checks whether the supplied headers match the requirements of this binding
     * @param headers the headers to check
     * @return true if the headers define any required keys and match any required
     * values
     */
    public boolean matches(Map headers)
    {
        if(headers == null)
        {
            return required.isEmpty() && matches.isEmpty();
        }
        else
        {
            return matchAny ? or(headers) : and(headers);
        }
    }

    private boolean and(Map headers)
    {
        //need to match all the defined mapping rules:
        return headers.keySet().containsAll(required)
                && headers.entrySet().containsAll(matches);
    }

    private boolean or(Map headers)
    {
        //only need to match one mapping rule:
        return !Collections.disjoint(headers.keySet(), required)
                || !Collections.disjoint(headers.entrySet(), matches);
    }

    private void processSpecial(String key, Object value)
    {
        if("X-match".equalsIgnoreCase(key))
        {
            matchAny = isAny(value);
        }
        else
        {
            _logger.warn("Ignoring special header: " + key);
        }
    }

    private boolean isAny(Object value)
    {
        if(value instanceof String)
        {
            if("any".equalsIgnoreCase((String) value)) return true;
            if("all".equalsIgnoreCase((String) value)) return false;
        }
        _logger.warn("Ignoring unrecognised match type: " + value);
        return false;//default to all
    }

    static boolean isSpecial(Object key)
    {
        return key instanceof String && isSpecial((String) key);
    }

    static boolean isSpecial(String key)
    {
        return key.startsWith("X-") || key.startsWith("x-");
    }
}
