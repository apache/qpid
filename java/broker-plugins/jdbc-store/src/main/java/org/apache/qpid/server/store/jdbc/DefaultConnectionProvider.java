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
package org.apache.qpid.server.store.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class DefaultConnectionProvider implements ConnectionProvider
{
    private final String _connectionUrl;
    private final String _username;
    private final String _password;

    public DefaultConnectionProvider(String connectionUrl, String username, String password)
    {
        _connectionUrl = connectionUrl;
        _username = username;
        _password = password;
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        if (_username == null)
        {
            return DriverManager.getConnection(_connectionUrl);
        }
        else
        {
            return DriverManager.getConnection(_connectionUrl, _username, _password);
        }
    }

    @Override
    public void close() throws SQLException
    {
    }
}
