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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreTestCase;
import org.apache.qpid.server.virtualhost.jdbc.JDBCVirtualHost;

public class JDBCMessageStoreTest extends MessageStoreTestCase
{
    private String _connectionURL;

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            shutdownDerby();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testOnDelete() throws Exception
    {
        Set<String> expectedTables = GenericJDBCMessageStore.MESSAGE_STORE_TABLE_NAMES;
        assertTablesExist(expectedTables, true);
        getStore().closeMessageStore();
        assertTablesExist(expectedTables, true);
        getStore().onDelete();
        assertTablesExist(expectedTables, false);
    }

    @Override
    protected VirtualHost createVirtualHost()
    {
        _connectionURL = "jdbc:derby:memory:/" + getTestName() + ";create=true";

        final JDBCVirtualHost jdbcVirtualHost = mock(JDBCVirtualHost.class);
        when(jdbcVirtualHost.getConnectionUrl()).thenReturn(_connectionURL);
        when(jdbcVirtualHost.getUsername()).thenReturn("test");
        when(jdbcVirtualHost.getPassword()).thenReturn("pass");
        return jdbcVirtualHost;
    }


    @Override
    protected MessageStore createMessageStore()
    {
        return new GenericJDBCMessageStore();
    }

    private void assertTablesExist(Set<String> expectedTables, boolean exists) throws SQLException
    {
        Set<String> existingTables = getTableNames();
        for (String tableName : expectedTables)
        {
            assertEquals("Table " + tableName + (exists ? " is not found" : " actually exist"), exists,
                    existingTables.contains(tableName));
        }
    }

    private Set<String> getTableNames() throws SQLException
    {
        Set<String> tableNames = new HashSet<String>();
        Connection conn = null;
        try
        {
            conn = openConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, null, new String[] { "TABLE" });
            try
            {
                while (tables.next())
                {
                    tableNames.add(tables.getString("TABLE_NAME"));
                }
            }
            finally
            {
                tables.close();
            }
        }
        finally
        {
            if (conn != null)
            {
                conn.close();
            }
        }
        return tableNames;
    }

    private Connection openConnection() throws SQLException
    {
        return DriverManager.getConnection(_connectionURL);
    }


    private void shutdownDerby() throws SQLException
    {
        Connection connection = null;
        try
        {
            connection = DriverManager.getConnection("jdbc:derby:memory:/" + getTestName() + ";shutdown=true");
        }
        catch(SQLException e)
        {
            if (e.getSQLState().equalsIgnoreCase("08006"))
            {
                //expected and represents a clean shutdown of this database only, do nothing.
            }
            else
            {
                throw e;
            }
        }
        finally
        {
            if (connection != null)
            {
                connection.close();
            }
        }
    }
}
