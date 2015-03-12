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
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.store.StoreException;

public class DerbyUtils
{
    public static final String MEMORY_STORE_LOCATION = ":memory:";
    public static final String DERBY_SINGLE_DB_SHUTDOWN_CODE = "08006";
    private static final String SQL_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String TABLE_EXISTENCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final Logger DERBY_LOG = LoggerFactory.getLogger("DERBY");
    public static final DerbyLogWriter DERBY_LOG_WRITER = new DerbyLogWriter();
    public static final String DERBY_STREAM_ERROR_METHOD = "derby.stream.error.method";

    public static void loadDerbyDriver()
    {
        try
        {
            // set the error log output
            System.setProperty(DERBY_STREAM_ERROR_METHOD,
                               "org.apache.qpid.server.store.derby.DerbyUtils.getDerbyLogWriter");

            Class<Driver> driverClass = (Class<Driver>) Class.forName(SQL_DRIVER_NAME);
        }
        catch (ClassNotFoundException e)
        {
            throw new StoreException("Failed to load driver " + SQL_DRIVER_NAME, e);
        }
    }

    public static Writer getDerbyLogWriter()
    {
        return DERBY_LOG_WRITER;
    }

    public static String createConnectionUrl(final String name, final String databasePath)
    {
        // Derby wont use an existing directory, so we append parent name
        if (isInMemoryDatabase(databasePath))
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

    public static boolean isInMemoryDatabase(final String databasePath)
    {
        return MEMORY_STORE_LOCATION.equals(databasePath);
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


    private static class DerbyLogWriter extends Writer
    {

        public static final String DERBY_STARTUP_MESSAGE = "Booting Derby version ";
        public static final String DERBY_SHUTDOWN_MESSAGE = "Shutting down instance ";
        public static final String DERBY_CLASS_LOADER_STARTED_MESSAGE = "Database Class Loader started";
        public static final String DERBY_SYSTEM_HOME = "derby.system.home";
        public static final String DASHED_LINE = "\\s*-*\\s*";

        private final ThreadLocal<StringBuilder> _threadLocalBuffer = new ThreadLocal<StringBuilder>()
        {
            @Override
            protected StringBuilder initialValue()
            {
                return new StringBuilder();
            }
        };

        @Override
        public void write(final char[] cbuf, final int off, final int len) throws IOException
        {
            _threadLocalBuffer.get().append(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException
        {
            String logMessage = _threadLocalBuffer.get().toString();
            if(!logMessage.matches(DASHED_LINE))
            {
                if(logMessage.contains(DERBY_STARTUP_MESSAGE))
                {
                    // the first line of the message containing the startup message is the current date/time, which
                    // we can remove
                    logMessage = logMessage.substring(logMessage.indexOf('\n') + 1);
                }

                // This is pretty hideous, but since the Derby logging doesn't have any way of informing us of priority
                // we simply have to assume everything is a warning except known startup / shutdown messages
                // which we match using known prefixes.

                if(logMessage.startsWith(DERBY_STARTUP_MESSAGE)
                   || logMessage.startsWith(DERBY_SHUTDOWN_MESSAGE))
                {
                    DERBY_LOG.info(logMessage);
                }
                else if(logMessage.startsWith(DERBY_SYSTEM_HOME)
                   || logMessage.startsWith(DERBY_STREAM_ERROR_METHOD)
                   || logMessage.startsWith("java.vendor")
                   || logMessage.startsWith(DERBY_CLASS_LOADER_STARTED_MESSAGE))
                {
                    DERBY_LOG.debug(logMessage);
                }
                else
                {
                    DERBY_LOG.warn(logMessage);
                }

            }
            _threadLocalBuffer.set(new StringBuilder());
        }

        @Override
        public void close() throws IOException
        {

        }
    }
}

