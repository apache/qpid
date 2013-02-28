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
package org.apache.qpid.server.security.group;

import static org.apache.qpid.server.util.MapValueConverter.getStringAttribute;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.plugin.GroupManagerFactory;

public class FileGroupManagerFactory implements GroupManagerFactory
{
    static final String FILE_GROUP_MANAGER_TYPE = "file-group-manager";
    static final String FILE = "file";

    @Override
    public GroupManager createInstance(Map<String, Object> attributes)
    {
        if(!FILE_GROUP_MANAGER_TYPE.equals(getStringAttribute(GroupProvider.TYPE, attributes, null)))
        {
            return null;
        }

        String groupFile =  getStringAttribute(FILE, attributes, null);
        if (StringUtils.isBlank(groupFile))
        {
            throw new IllegalConfigurationException("Path to file containing groups is not specified!");
        }
        return new FileGroupManager(groupFile);
    }

}
