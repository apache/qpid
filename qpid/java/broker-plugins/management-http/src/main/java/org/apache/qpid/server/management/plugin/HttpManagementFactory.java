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
package org.apache.qpid.server.management.plugin;

import static org.apache.qpid.server.util.MapValueConverter.getBooleanAttribute;
import static org.apache.qpid.server.util.MapValueConverter.getIntegerAttribute;
import static org.apache.qpid.server.util.MapValueConverter.getStringAttribute;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.plugin.PluginFactory;

public class HttpManagementFactory implements PluginFactory
{
    // 10 minutes by default
    private static final int DEFAULT_TIMEOUT_IN_SECONDS = 60 * 10;

    public static final String TIME_OUT = "sessionTimeout";
    public static final String KEY_STORE_PATH = "keyStorePath";
    public static final String KEY_STORE_PASSWORD = "keyStorePassword";
    public static final String HTTP_BASIC_AUTHENTICATION_ENABLED = "httpBasicAuthenticationEnabled";
    public static final String HTTPS_BASIC_AUTHENTICATION_ENABLED = "httpsBasicAuthenticationEnabled";
    public static final String HTTP_SASL_AUTHENTICATION_ENABLED = "httpSaslAuthenticationEnabled";
    public static final String HTTPS_SASL_AUTHENTICATION_ENABLED = "httpsSaslAuthenticationEnabled";

    public static final String PLUGIN_NAME = "MANAGEMENT-HTTP";

    @Override
    public Plugin createInstance(UUID id, Map<String, Object> attributes, Broker broker)
    {
        if (!PLUGIN_NAME.equals(attributes.get(PLUGIN_TYPE)))
        {
            return null;
        }

        HttpConfiguration configuration = new HttpConfiguration(
                getIntegerAttribute(TIME_OUT, attributes, DEFAULT_TIMEOUT_IN_SECONDS),
                getBooleanAttribute(HTTP_BASIC_AUTHENTICATION_ENABLED, attributes, false),
                getBooleanAttribute(HTTPS_BASIC_AUTHENTICATION_ENABLED, attributes, true),
                getBooleanAttribute(HTTP_SASL_AUTHENTICATION_ENABLED, attributes, true),
                getBooleanAttribute(HTTPS_SASL_AUTHENTICATION_ENABLED, attributes, true),
                getStringAttribute(KEY_STORE_PATH, attributes, null),
                getStringAttribute(KEY_STORE_PASSWORD, attributes, null)
                );
        //TODO: create defaults
        Map<String, Object> defaults = null;
        return new HttpManagement( id, broker, configuration, defaults);
    }
}
