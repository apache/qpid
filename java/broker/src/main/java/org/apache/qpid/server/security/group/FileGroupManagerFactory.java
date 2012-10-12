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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.plugin.GroupManagerFactory;

public class FileGroupManagerFactory implements GroupManagerFactory
{
    private static final String GROUP_FILE_MARKER = "groupFile";
    private static final String FILE_ATTRIBUTE_VALUE = "file-group-manager.attributes.attribute.value";
    private static final String FILE_ATTRIBUTE_NAME = "file-group-manager.attributes.attribute.name";

    @Override
    public GroupManager createInstance(Configuration configuration)
    {
        if(configuration.subset("file-group-manager").isEmpty())
        {
            return null;
        }

        String groupFileArgumentName = configuration.getString(FILE_ATTRIBUTE_NAME);
        String groupFile = configuration.getString(FILE_ATTRIBUTE_VALUE);

        if (!GROUP_FILE_MARKER.equals(groupFileArgumentName))
        {
            throw new RuntimeException("Config for file-group-manager found but " + FILE_ATTRIBUTE_NAME
                    + " has no value or " + groupFileArgumentName
                    + " does not equal " + GROUP_FILE_MARKER);
        }

        if (groupFile == null)
        {
            throw new RuntimeException("Config for file-group-manager found but " +  FILE_ATTRIBUTE_VALUE + " has no value."
                    + " Filename expected.");
        }

        try
        {
            return new FileGroupManager(groupFile);
        }
        catch (ConfigurationException e)
        {
            throw new RuntimeException(e);
        }
    }

}
