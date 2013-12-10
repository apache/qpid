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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

class MapComparator implements Comparator<Map>
{
    private Comparator<Map>[] _sortKeys;

    public MapComparator(final String[] sortKeys)
    {
        _sortKeys = parseKeys(sortKeys);
    }

    private static Comparator<Map>[] parseKeys(final String[] sortKeys)
    {
        Comparator<Map>[] comparators = new Comparator[sortKeys.length];
        for(int i = 0; i < sortKeys.length; i++)
        {
            String key = sortKeys[i];

            if(key.startsWith("+") || key.startsWith(" "))
            {
                comparators[i] = new KeyComparator(key.substring(1));
            }
            else if(key.startsWith("-"))
            {
                comparators[i] = Collections.reverseOrder(new KeyComparator(key.substring(1)));
            }
            else
            {
                comparators[i] = new KeyComparator(key);
            }
        }
        return comparators;
    }


    public int compare(final Map o1, final Map o2)
    {
        int result = 0;
        for(int i = 0; i < _sortKeys.length; i++)
        {
            result = _sortKeys[i].compare(o1, o2);
            if(result != 0)
            {
                return result;
            }
        }
        return 0;
    }

}
