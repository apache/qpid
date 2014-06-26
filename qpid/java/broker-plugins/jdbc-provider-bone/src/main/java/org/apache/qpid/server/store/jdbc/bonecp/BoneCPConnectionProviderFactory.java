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
package org.apache.qpid.server.store.jdbc.bonecp;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

import org.apache.qpid.server.plugin.JDBCConnectionProviderFactory;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.jdbc.ConnectionProvider;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@PluggableService
public class BoneCPConnectionProviderFactory implements JDBCConnectionProviderFactory
{
    public static final String PARTITION_COUNT = "qpid.jdbcstore.bonecp.partitionCount";
    public static final String MAX_CONNECTIONS_PER_PARTITION = "qpid.jdbcstore.bonecp.maxConnectionsPerPartition";
    public static final String MIN_CONNECTIONS_PER_PARTITION = "qpid.jdbcstore.bonecp.minConnectionsPerPartition";

    private final Set<String> _supportedAttributes = unmodifiableSet(new HashSet<String>(asList(PARTITION_COUNT, MAX_CONNECTIONS_PER_PARTITION, MIN_CONNECTIONS_PER_PARTITION)));

    @Override
    public String getType()
    {
        return "BONECP";
    }

    public ConnectionProvider getConnectionProvider(String connectionUrl, String username, String password, Map<String, String> providerAttributes)
            throws SQLException
    {
        return new BoneCPConnectionProvider(connectionUrl, username, password, providerAttributes);
    }

    @Override
    public Set<String> getProviderAttributeNames()
    {
        return _supportedAttributes;
    }
}
