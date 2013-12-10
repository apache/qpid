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
package org.apache.qpid.disttest.json;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.disttest.client.property.GeneratedPropertyValue;
import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.client.property.PropertyValueFactory;
import org.apache.qpid.disttest.client.property.SimplePropertyValue;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class PropertyValueAdapter implements JsonDeserializer<PropertyValue>, JsonSerializer<PropertyValue>
{
    private static final String DEF_FIELD = "@def";
    private PropertyValueFactory _factory = new PropertyValueFactory();

    @Override
    public PropertyValue deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException
    {
        if (json.isJsonNull())
        {
            return null;
        }
        else if (json.isJsonPrimitive())
        {
            Object result = null;
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isString())
            {
                result = primitive.getAsString();
            }
            else if (primitive.isNumber())
            {
                String asString = primitive.getAsString();
                if (asString.indexOf('.') != -1 || asString.indexOf('e') != -1)
                {
                    result = primitive.getAsDouble();
                }
                else
                {
                    result = primitive.getAsLong();
                }
            }
            else if (primitive.isBoolean())
            {
                result = primitive.getAsBoolean();
            }
            else
            {
                throw new JsonParseException("Unsupported primitive value " + primitive);
            }
            return new SimplePropertyValue(result);
        }
        else if (json.isJsonArray())
        {
            JsonArray array = json.getAsJsonArray();
            List<Object> result = new ArrayList<Object>(array.size());
            for (JsonElement element : array)
            {
                result.add(context.deserialize(element, Object.class));
            }
            return new SimplePropertyValue(result);
        }
        else if (json.isJsonObject())
        {
            JsonObject object = json.getAsJsonObject();
            JsonElement defElement = object.getAsJsonPrimitive(DEF_FIELD);
            Class<?> classInstance = null;
            if (defElement != null)
            {
                try
                {
                    classInstance = _factory.getPropertyValueClass(defElement.getAsString());
                }
                catch (ClassNotFoundException e)
                {
                    // ignore
                }
            }
            if (classInstance == null)
            {
                Map<String, Object> result = new HashMap<String, Object>();
                for (Map.Entry<String, JsonElement> entry : object.entrySet())
                {
                    Object value = context.deserialize(entry.getValue(), Object.class);
                    result.put(entry.getKey(), value);
                }
                return new SimplePropertyValue(result);
            }
            else
            {
                return context.deserialize(json, classInstance);
            }
        }
        else
        {
            throw new JsonParseException("Unsupported JSON type " + json);
        }
    }

    @Override
    public JsonElement serialize(PropertyValue src, Type typeOfSrc, JsonSerializationContext context)
    {
        if (src instanceof GeneratedPropertyValue)
        {
            JsonObject object = (JsonObject) context.serialize(src, Object.class);
            object.addProperty(DEF_FIELD, ((GeneratedPropertyValue) src).getDefinition());
            return object;
        }
        else
        {
            return context.serialize(src.getValue(), Object.class);
        }
    }

}
