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
 *
 */
package org.apache.qpid.disttest.json;

import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.message.Command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonHandler
{
    private final Gson _gson = new GsonBuilder()
            .registerTypeAdapter(PropertyValue.class, new PropertyValueAdapter())
            .create();

    public <T extends Command> T unmarshall(final String jsonParams, final Class<T> clazz)
    {
        return _gson.fromJson(jsonParams, clazz);
    }

    public <T extends Command> String marshall(final T command)
    {
        return _gson.toJson(command);
    }
}
