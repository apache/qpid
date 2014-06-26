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

import static org.apache.qpid.server.store.jdbc.bonecp.BoneCPConnectionProviderFactory.MAX_CONNECTIONS_PER_PARTITION;
import static org.apache.qpid.server.store.jdbc.bonecp.BoneCPConnectionProviderFactory.MIN_CONNECTIONS_PER_PARTITION;
import static org.apache.qpid.server.store.jdbc.bonecp.BoneCPConnectionProviderFactory.PARTITION_COUNT;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import org.apache.qpid.server.store.jdbc.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class BoneCPConnectionProvider implements ConnectionProvider
{
    public static final int DEFAULT_MIN_CONNECTIONS_PER_PARTITION = 5;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_PARTITION = 10;
    public static final int DEFAULT_PARTITION_COUNT = 4;

    private final BoneCP _connectionPool;

    public BoneCPConnectionProvider(String connectionUrl, String username, String password, Map<String, String> providerAttributes) throws SQLException
    {
        BoneCPConfig config = new BoneCPConfig();
        config.setJdbcUrl(connectionUrl);
        if (username != null)
        {
            config.setUsername(username);
            config.setPassword(password);
        }

        config.setMinConnectionsPerPartition(convertToIntWithDefault(MIN_CONNECTIONS_PER_PARTITION, providerAttributes, DEFAULT_MIN_CONNECTIONS_PER_PARTITION));
        config.setMaxConnectionsPerPartition(convertToIntWithDefault(MAX_CONNECTIONS_PER_PARTITION, providerAttributes, DEFAULT_MAX_CONNECTIONS_PER_PARTITION));
        config.setPartitionCount(convertToIntWithDefault(PARTITION_COUNT, providerAttributes, DEFAULT_PARTITION_COUNT));

        _connectionPool = new BoneCP(config);
    }

    private int convertToIntWithDefault(String key, Map<String, String> context, int defaultValue)
    {
        if (context.containsKey(key))
        {
            try
            {
                return Integer.parseInt(context.get(key));
            }
            catch (NumberFormatException e)
            {
               return defaultValue;
            }
        }
        else
        {
            return defaultValue;
        }
    }
    @Override
    public Connection getConnection() throws SQLException
    {
        return _connectionPool.getConnection();
    }

    @Override
    public void close() throws SQLException
    {
        _connectionPool.shutdown();
    }
}
