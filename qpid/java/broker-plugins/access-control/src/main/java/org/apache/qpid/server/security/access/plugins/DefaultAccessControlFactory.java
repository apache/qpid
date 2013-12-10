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
package org.apache.qpid.server.security.access.plugins;

import static org.apache.qpid.server.security.access.FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE;
import static org.apache.qpid.server.security.access.FileAccessControlProviderConstants.PATH;
import static org.apache.qpid.server.util.MapValueConverter.getStringAttribute;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.plugin.AccessControlFactory;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.server.util.ResourceBundleLoader;

public class DefaultAccessControlFactory implements AccessControlFactory
{
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.access.plugins.FileAccessControlProviderAttributeDescriptions";

    public static final Collection<String> ATTRIBUTES = Collections.<String> unmodifiableList(Arrays.asList(
            ATTRIBUTE_TYPE,
            PATH
            ));

    public AccessControl createInstance(Map<String, Object> attributes)
    {
        if(attributes == null || !ACL_FILE_PROVIDER_TYPE.equals(attributes.get(ATTRIBUTE_TYPE)))
        {
            return null;
        }

        String path =  getStringAttribute(PATH, attributes, null);
        if (path == null || "".equals(path.trim()))
        {
            throw new IllegalConfigurationException("Path to ACL was not specified!");
        }

        return new DefaultAccessControl(path);
    }

    @Override
    public String getType()
    {
        return ACL_FILE_PROVIDER_TYPE;
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ATTRIBUTES;
    }

    @Override
    public Map<String, String> getAttributeDescriptions()
    {
        return ResourceBundleLoader.getResources(RESOURCE_BUNDLE);
    }
}
