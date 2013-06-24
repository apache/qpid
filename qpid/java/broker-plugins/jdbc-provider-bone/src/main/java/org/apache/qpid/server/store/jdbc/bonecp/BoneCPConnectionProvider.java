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

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.store.jdbc.ConnectionProvider;

public class BoneCPConnectionProvider implements ConnectionProvider
{
    public static final int DEFAULT_MIN_CONNECTIONS_PER_PARTITION = 5;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_PARTITION = 10;
    public static final int DEFAULT_PARTITION_COUNT = 4;
    private final BoneCP _connectionPool;

    public BoneCPConnectionProvider(String connectionUrl, Configuration storeConfiguration) throws SQLException
    {
        BoneCPConfig config = new BoneCPConfig();
        config.setJdbcUrl(connectionUrl);

        config.setMinConnectionsPerPartition(storeConfiguration.getInteger("minConnectionsPerPartition", DEFAULT_MIN_CONNECTIONS_PER_PARTITION));
        config.setMaxConnectionsPerPartition(storeConfiguration.getInteger("maxConnectionsPerPartition", DEFAULT_MAX_CONNECTIONS_PER_PARTITION));
        config.setPartitionCount(storeConfiguration.getInteger("partitionCount",DEFAULT_PARTITION_COUNT));
        _connectionPool = new BoneCP(config);
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
