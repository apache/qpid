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
package org.apache.qpid.messaging.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * PyPrint
 *
 */

public class PyPrint
{

    public static String pprint(Object obj)
    {
        if (obj instanceof Map)
        {
            return pprint_map((Map) obj);
        }
        else if (obj instanceof List)
        {
            return pprint_list((List) obj);
        }
        else if (obj instanceof String)
        {
            return pprint_string((String) obj);
        }
        else if (obj instanceof Boolean)
        {
            return ((Boolean) obj).booleanValue() ? "True" : "False";
        }
        else if (obj == null)
        {
            return "None";
        }
        else
        {
            return obj.toString();
        }
    }

    private static String indent(String st)
    {
        return "  " + st.replace("\n", "\n  ");
    }

    private static String pprint_map(Map<Object,Object> map)
    {
        List<String> items = new ArrayList<String>();
        for (Map.Entry me : map.entrySet())
        {
            items.add(String.format("%s: %s", pprint(me.getKey()),
                                    pprint(me.getValue())));
        }
        Collections.sort(items);
        return pprint_items("{", items, "}");
    }

    private static String pprint_list(List list)
    {
        List<String> items = new ArrayList<String>();
        for (Object o : list)
        {
            items.add(pprint(o));
        }
        return pprint_items("[", items, "]");
    }

    private static String pprint_items(String start, List<String> items,
                                       String end)
    {
        StringBuilder result = new StringBuilder();
        for (String item : items)
        {
            if (result.length() > 0)
            {
                result.append(",\n");
            }
            result.append(indent(item));
        }

        if (result.length() > 0)
        {
            return String.format("%s\n%s\n%s", start, result, end);
        }
        else
        {
            return String.format("%s%s", start, end);
        }
    }

    private static String pprint_string(String st)
    {
        StringBuilder result = new StringBuilder();
        result.append('\'');
        for (int i = 0; i < st.length(); i++)
        {
            char c = st.charAt(i);
            switch (c)
            {
            case '\'':
                result.append("\\'");
                break;
            case '\n':
                result.append("\\n");
                break;
            default:
                if (c >= 0x80)
                {
                    result.append(String.format("\\u%04x", (int)c));
                }
                else
                {
                    result.append(c);
                }
                break;
            }
        }
        result.append('\'');
        return result.toString();
    }

}
