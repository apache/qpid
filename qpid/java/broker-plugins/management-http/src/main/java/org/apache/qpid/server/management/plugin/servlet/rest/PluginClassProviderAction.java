package org.apache.qpid.server.management.plugin.servlet.rest;/*
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.plugin.Pluggable;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class PluginClassProviderAction implements Action
{
    @Override
    public String getName()
    {
        return "pluginList";
    }

    @Override
    public Object perform(Map<String, Object> request, Broker broker)
    {
        try
        {
            String className = (String) request.get("plugin");
            QpidServiceLoader serviceLoader = new QpidServiceLoader();
            final Class<Pluggable> clazz = (Class<Pluggable>) Class.forName("org.apache.qpid.server.plugin."+className);
            List<String> values = new ArrayList<String>();
            for(Pluggable instance : serviceLoader.instancesOf(clazz))
            {
                values.add(instance.getType());
            }
            return values;
        }
        catch (ClassNotFoundException e)
        {
            return Collections.emptyList();
        }

    }
}
