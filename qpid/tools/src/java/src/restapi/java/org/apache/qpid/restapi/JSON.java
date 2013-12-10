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
package org.apache.qpid.restapi;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// QMF2 imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfDescribed;
import org.apache.qpid.qmf2.common.SchemaClassId;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;

/**
 * This class provides a number of convenience methods to serialise and deserialise JSON strings to/from Java
 * Collections or QmfData objects.
 *
 * The JSONMapParser class used here is largely a direct copy of org.apache.qpid.messaging.util.AddressParser
 * as it provides a handy mechanism to parse a JSON String into a Map which is the only JSON requirement that
 * we really need for QMF. Originally this code simply did "import org.apache.qpid.messaging.util.AddressParser;"
 * but there's a restriction/bug on the core AddressParser whereby it serialises integers into Java Integer
 * which means that long integer values aren't correctly stored. It's this restriction that gives Java Address
 * Strings a defacto 2GB queue size. I should really provide a patch for the *real* AddressParser but it's better
 * to add features covering "shorthand" forms for large values (e.g. k/K, m/M, g/G for kilo, mega, giga etc.)
 * to both the Java and C++ AddressParser to ensure maximum consistency.
 *
 * Because the JSON requirements for the REST API are relatively modest a fairly simple serialisation/deserialisation
 * mechanism is included here and in the modified AddressParser classes rather than incorporating a full-blown
 * Java JSON parser. This helps avoid a bot of bloat and keeps dependencies limited to the core qpid classes.
 *
 * @author Fraser Adams
 */
public final class JSON
{
    /**
     * Serialise an Object to JSON. Note this isn't a full JSON serialisation of java.lang.Object, rather it only
     * includes types that are relevant to QmfData Objects and the types that may be contained therein.
     * @param item the Object that we wish to serialise to JSON.
     * @return the JSON String encoding.
     */
    public final static String fromObject(final Object item)
    {
        if (item == null)
        {
            return "";
        }
        else
        {
            if (item instanceof Map)
            { // Check if the value part is an ObjectId and serialise appropriately
                Map map = (Map)item;
                if (map.containsKey("_object_name"))
                { // Serialise "ref" properties as String versions of ObjectId to match encoding used in fromQmfData()
                    return "\"" + new ObjectId(map) + "\"";
                }
                else
                {
                    return fromMap(map);
                }
            }
            else if (item instanceof List)
            {
                return fromList((List)item);
            }
            else if (item instanceof QmfData)
            {
                return fromQmfData((QmfData)item);
            }
            else if (item instanceof WorkItem)
            {
                return fromWorkItem((WorkItem)item);
            }
            else if (item instanceof String)
            {
                return "\"" + item + "\"";
            }
            else if (item instanceof byte[])
            {
                return "\"" + new String((byte[])item) + "\"";
            }
            else if (item instanceof UUID)
            {
                return "\"" + item.toString() + "\"";
            }
            else
            {
                return item.toString();
            }
        }
    }

    /**
     * Encode the Map contents so we can use the same code for fromMap and fromQmfData as the latter also needs
     * to encode _object_id and _schema_id. This returns a String that needs to be topped and tailed with braces.
     * @param m the Map that we wish to serialise to JSON.
     * @return the String encoding of the contents.
     */
    @SuppressWarnings("unchecked")
    private final static String encodeMapContents(final Map m)
    {
        Map<String, Object> map = (Map<String, Object>)m;
        StringBuilder buffer = new StringBuilder(512);
        int size = map.size();
        int count = 1;
        for (Map.Entry<String, Object> entry : map.entrySet())
        {
            String key = (String)entry.getKey();
            buffer.append("\"" + key + "\":");

            Object value = entry.getValue();
            buffer.append(fromObject(value));
            if (count++ < size)
            {
                buffer.append(",");
            }
        }
        return buffer.toString();
    }

    /**
     * Serialise a Map to JSON.
     * @param m the Map that we wish to serialise to JSON.
     * @return the JSON String encoding.
     */
    @SuppressWarnings("unchecked")
    public final static String fromMap(final Map m)
    {
        return "{" + encodeMapContents(m) + "}";
    }

    /**
     * Serialise a List to JSON.
     * @param list the List that we wish to serialise to JSON.
     * @return the JSON String encoding.
     */
    public final static String fromList(final List list)
    {
        StringBuilder buffer = new StringBuilder(512);
        buffer.append("[");
        int size = list.size();
        int count = 1;
        for (Object item : list)
        {
            buffer.append(fromObject(item));
            if (count++ < size)
            {
                buffer.append(",");
            }
        }
        buffer.append("]");
        return buffer.toString();
    }

    /**
     * Serialise a QmfData Object to JSON. If the Object is a QmfConsoleData we serialise the ObjectId as a String
     * which is the same encoding used for the various "ref" properies in fromObject().
     * @param data the QmfData that we wish to serialise to JSON.
     * @return the JSON String encoding.
     */
    public final static String fromQmfData(final QmfData data)
    {
        String consoleDataInfo = "";

        if (data instanceof QmfConsoleData)
        {
            QmfConsoleData consoleData = (QmfConsoleData)data;
            SchemaClassId sid = consoleData.getSchemaClassId();
            long[] ts = consoleData.getTimestamps();

            String objectId = "\"_object_id\":\"" + consoleData.getObjectId().toString() + "\",";
            String schemaId = "\"_schema_id\":{" + 
                "\"_package_name\":\"" + sid.getPackageName() +
                "\",\"_class_name\":\"" + sid.getClassName() +
                "\",\"_type\":\"" + sid.getType() +
                "\",\"_hash\":\"" + sid.getHashString() +
            "\"},";

            String timestamps = "\"_update_ts\":" + ts[0] + "," +
                                "\"_create_ts\":" + ts[1] + "," +
                                "\"_delete_ts\":" + ts[2] + ",";

            consoleDataInfo = objectId + schemaId + timestamps;
        }

        return "{" + consoleDataInfo + encodeMapContents(data.mapEncode()) + "}";
    }

    /**
     * Serialise a WorkItem Object to JSON.
     * @param data the WorkItem that we wish to serialise to JSON.
     * @return the JSON String encoding.
     */
    public final static String fromWorkItem(final WorkItem data)
    {
        // TODO There are a couple of WorkItem types that won't serialise correctly - SubscriptionIndicationWorkItem
        // and MethodCallWorkItem. Their params require a custom serialiser - though they probably won't be used
        // from a REST API so they've been parked for now.
        String type = "\"_type\":\"" + data.getType() + "\",";
        Handle handle = data.getHandle();
        String handleString = handle == null ? "" : "\"_handle\":\"" + handle.getCorrelationId() + "\",";
        String params = "\"_params\":" + fromObject(data.getParams());

        return "{" + type + handleString + params + "}";
    }

    /**
     * Create a Map from a JSON String.
     * The JSONMapParser class used here is largely a direct copy of org.apache.qpid.messaging.util.AddressParser
     * as it provides a handy mechanism to parse a JSON String into a Map which is the only JSON requirement that
     * we really need for QMF. Originally this code simply did "import org.apache.qpid.messaging.util.AddressParser;"
     * but there's a restriction/bug on the core AddressParser whereby it serialises integers into Java Integer
     * which means that long integer values aren't correctly stored. It's this restriction that gives Java Address
     * Strings a defacto 2GB queue size. I should really provide a patch for the *real* AddressParser but it's better
     * to add features covering "shorthand" forms for large values (e.g. k/K, m/M, g/G for kilo, mega, giga etc.)
     * to both the Java and C++ AddressParser to ensure maximum consistency.
     * @param json the JSON String that we wish to decode into a Map.
     * @return the Map encoding of the JSON String.
     */
    public final static Map toMap(final String json)
    {
        if (json == null || json.equals(""))
        {
            return Collections.EMPTY_MAP;
        }
        else
        {
            return new JSONMapParser(json).map();
        }
    }

    /**
     * Create a QmfData from a JSON String.
     * @param json the JSON String that we wish to decode into a QmfData.
     * @return the QmfData encoding of the JSON String.
     */
    public final static QmfData toQmfData(final String json)
    {
        return new QmfData(toMap(json));
    }
}


