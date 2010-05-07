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
package org.apache.qpid.server.util;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.store.TestableMemoryMessageStore;

public class TestApplicationRegistry extends NullApplicationRegistry
{
    public TestApplicationRegistry() throws ConfigurationException
    {
        this(new ServerConfiguration(new PropertiesConfiguration()));
    }

    public TestApplicationRegistry(ServerConfiguration config) throws ConfigurationException
    {
        super(config);
        _configuration.getConfig().setProperty("virtualhosts.virtualhost.name",
                                               "test");
        _configuration.getConfig().setProperty("virtualhosts.virtualhost.test.store.class",
                                               TestableMemoryMessageStore.class.getName());
    }
}


