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
package org.apache.qpid.server.security.auth.manager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.model.AbstractConfiguredObjectTypeFactory;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.PluggableService;

@PluggableService
public class ScramSHA1AuthenticationManagerFactory
        extends AbstractConfiguredObjectTypeFactory<ScramSHA1AuthenticationManager>
        implements AuthenticationManagerFactory<ScramSHA1AuthenticationManager>
{

    public static final String PROVIDER_TYPE = "SCRAM-SHA-1";

    public static final String ATTRIBUTE_NAME = "name";

    public static final Collection<String> ATTRIBUTES = Collections.<String> unmodifiableList(Arrays.asList(
            AuthenticationProvider.TYPE
            ));

    public ScramSHA1AuthenticationManagerFactory()
    {
        super(ScramSHA1AuthenticationManager.class);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ATTRIBUTES;
    }

    @Override
    public Map<String, String> getAttributeDescriptions()
    {
        return Collections.emptyMap();
    }

    @Override
    public ScramSHA1AuthenticationManager createInstance(final Map<String, Object> attributes,
                                                         final ConfiguredObject<?>... parents)
    {
        return new ScramSHA1AuthenticationManager(attributes, getParent(Broker.class, parents));
    }

}
