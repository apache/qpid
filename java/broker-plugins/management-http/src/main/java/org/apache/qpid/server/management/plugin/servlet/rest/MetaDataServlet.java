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

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredAutomatedAttribute;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;
import org.apache.qpid.server.model.Model;

public class MetaDataServlet extends AbstractServlet
{

    private Model _instance;

    @Override
    public void init() throws ServletException
    {
        super.init();

        _instance = BrokerModel.getInstance();
    }

    @Override
    protected void doGetWithSubjectAndActor(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        Map<String,Map> classToDataMap = new TreeMap<>();

        for(Class<? extends ConfiguredObject> clazz : _instance.getSupportedCategories())
        {
            classToDataMap.put(clazz.getSimpleName(), processCategory(clazz));
        }

        final Writer writer = getOutputWriter(request, response);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, classToDataMap);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

    }

    private Map<String,Map> processCategory(final Class<? extends ConfiguredObject> clazz)
    {
        Map<String, Map> typeToDataMap = new TreeMap<>();
        ConfiguredObjectTypeRegistry typeRegistry = _instance.getTypeRegistry();
        for(Class<? extends ConfiguredObject> type : typeRegistry.getTypeSpecialisations(clazz))
        {
            typeToDataMap.put(ConfiguredObjectTypeRegistry.getType(type), processType(type));
        }
        return typeToDataMap;
    }

    private Map<String,Object> processType(final Class<? extends ConfiguredObject> type)
    {
        Map<String,Object> typeDetails = new LinkedHashMap<>();
        typeDetails.put("attributes", processAttributes(type));
        return typeDetails;
    }

    private Map<String,Map> processAttributes(final Class<? extends ConfiguredObject> type)
    {
        Collection<ConfiguredObjectAttribute<?, ?>> attributes =
            _instance.getTypeRegistry().getAttributeTypes(type).values();

        Map<String,Map> attributeDetails = new LinkedHashMap<>();
        for(ConfiguredObjectAttribute<?, ?> attribute : attributes)
        {
            Map<String,Object> attrDetails = new LinkedHashMap<>();
            attrDetails.put("type",attribute.getType().getSimpleName());
            if(!"".equals(attribute.getDescription()))
            {
                attrDetails.put("description",attribute.getDescription());
            }
            if(attribute.isDerived())
            {
                attrDetails.put("derived",attribute.isDerived());
            }
            if(attribute.isAutomated())
            {
                if(!"".equals(((ConfiguredAutomatedAttribute)attribute).defaultValue()))
                {
                    attrDetails.put("defaultValue",((ConfiguredAutomatedAttribute)attribute).defaultValue());
                }
                if(((ConfiguredAutomatedAttribute)attribute).isMandatory())
                {
                    attrDetails.put("mandatory",((ConfiguredAutomatedAttribute)attribute).isMandatory());
                }
            }
            if(attribute.isSecure())
            {
                attrDetails.put("secure",attribute.isSecure());
            }

            attributeDetails.put(attribute.getName(), attrDetails);
        }
        return attributeDetails;
    }
}
