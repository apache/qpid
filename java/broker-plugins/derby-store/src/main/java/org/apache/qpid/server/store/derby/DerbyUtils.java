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
package org.apache.qpid.server.store.derby;


import java.io.File;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.util.FileUtils;

public class DerbyUtils
{
    public static final String MEMORY_STORE_LOCATION = ":memory:";
    public static final String DERBY_SINGLE_DB_SHUTDOWN_CODE = "08006";
    private static final String SQL_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String TABLE_EXISTENCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");


    public static void loadDerbyDriver()
    {
        try
        {
            Class<Driver> driverClass = (Class<Driver>) Class.forName(SQL_DRIVER_NAME);
        }
        catch (ClassNotFoundException e)
        {
            throw new StoreException("Failed to load driver " + SQL_DRIVER_NAME, e);
        }
    }

    public static String createConnectionUrl(final String name, final String databasePath)
    {
        // Derby wont use an existing directory, so we append parent name
        if (MEMORY_STORE_LOCATION.equals(databasePath))
        {
            return "jdbc:derby:" + MEMORY_STORE_LOCATION + "/" + name + ";create=true";
        }
        else
        {
            File environmentPath = new File(databasePath);
            if (!environmentPath.exists())
            {
                if (!environmentPath.mkdirs())
                {
                    throw new IllegalArgumentException("Environment path "
                                                       + environmentPath
                                                       + " could not be read or created. "
                                                       + "Ensure the path is correct and that the permissions are correct.");
                }
            }
            return "jdbc:derby:" +  databasePath + "/" + name + ";create=true";
        }

    }

    public static void shutdownDatabase(String connectionURL) throws SQLException
    {
        try
        {
            Connection conn = DriverManager.getConnection(connectionURL + ";shutdown=true");
            // Shouldn't reach this point - shutdown=true should throw SQLException
            conn.close();
        }
        catch (SQLException e)
        {
            if (e.getSQLState().equalsIgnoreCase(DerbyUtils.DERBY_SINGLE_DB_SHUTDOWN_CODE))
            {
                //expected and represents a clean shutdown of this database only, do nothing.
            }
            else
            {
                throw e;
            }
        }
    }

    public static void deleteDatabaseLocation(String storeLocation)
    {
        if (MEMORY_STORE_LOCATION.equals(storeLocation))
        {
            return;
        }

        if (storeLocation != null)
        {
            File location = new File(storeLocation);
            if (location.exists())
            {
                if (!FileUtils.delete(location, true))
                {
                    throw new StoreException("Failed to delete the store at location : " + storeLocation);
                }
            }
        }
    }

    public static String getBlobAsString(ResultSet rs, int col) throws SQLException
    {
        Blob blob = rs.getBlob(col);
        if(blob == null)
        {
            return null;
        }
        byte[] bytes = blob.getBytes(1, (int) blob.length());
        return new String(bytes, UTF8_CHARSET);
    }

    protected static byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException
    {
        Blob dataAsBlob = rs.getBlob(col);
        return dataAsBlob.getBytes(1,(int) dataAsBlob.length());
    }

    public static boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(TABLE_EXISTENCE_QUERY);
        try
        {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            try
            {
                return rs.next();
            }
            finally
            {
                rs.close();
            }
        }
        finally
        {
            stmt.close();
        }
    }


}

