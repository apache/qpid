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

import java.io.File;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.plugin.AccessControlFactory;
import org.apache.qpid.server.security.AccessControl;

public class DefaultAccessControlFactory implements AccessControlFactory
{
    public static final String ATTRIBUTE_ACL_FILE = "aclFile";

    public AccessControl createInstance(Map<String, Object> aclConfiguration)
    {
        if (aclConfiguration != null)
        {
            Object aclFile = aclConfiguration.get(ATTRIBUTE_ACL_FILE);
            if (aclFile != null)
            {
                if (aclFile instanceof String)
                {
                    String aclPath = (String) aclFile;
                    if (!new File(aclPath).exists())
                    {
                        throw new IllegalConfigurationException("ACL file '" + aclPath + "' is not found");
                    }
                    return new DefaultAccessControl(aclPath);
                }
                else
                {
                    throw new IllegalConfigurationException("Expected '" + ATTRIBUTE_ACL_FILE + "' attribute value of type String but was " + aclFile.getClass()
                            + ": " + aclFile);
                }
            }
        }
        return null;
    }
}
