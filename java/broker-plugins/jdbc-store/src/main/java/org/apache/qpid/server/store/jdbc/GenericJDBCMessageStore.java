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


import java.io.File;
import java.security.PrivilegedAction;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.JDBCConnectionProviderFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.StoreException;

/**
 * Implementation of a MessageStore backed by a Generic JDBC Database.
 */
public class GenericJDBCMessageStore extends GenericAbstractJDBCMessageStore
{

    private static final Logger _logger = Logger.getLogger(GenericJDBCMessageStore.class);

    protected String _connectionURL;
    private ConnectionProvider _connectionProvider;

    private String _blobType;
    private String _varBinaryType;
    private String _bigIntType;
    private boolean _useBytesMethodsForBlob;

    @Override
    protected void doOpen(final ConfiguredObject<?> parent) throws StoreException
    {
        JDBCSettings settings = (JDBCSettings)parent;
        _connectionURL = settings.getConnectionUrl();

        JDBCDetails details = JDBCDetails.getDetailsForJdbcUrl(_connectionURL, parent);

        if (!details.isKnownVendor() && getLogger().isInfoEnabled())
        {
            getLogger().info("Do not recognize vendor from connection URL: " + _connectionURL
                             + " Using fallback settings " + details);
        }
        if (details.isOverridden() && getLogger().isInfoEnabled())
        {
            getLogger().info("One or more JDBC details were overridden from context. "
                             +  " Using settings : " + details);
        }

        _blobType = details.getBlobType();
        _varBinaryType = details.getVarBinaryType();
        _useBytesMethodsForBlob = details.isUseBytesMethodsForBlob();
        _bigIntType = details.getBigintType();

        String connectionPoolType = settings.getConnectionPoolType() == null ? DefaultConnectionProviderFactory.TYPE : settings.getConnectionPoolType();

        JDBCConnectionProviderFactory connectionProviderFactory =
                JDBCConnectionProviderFactory.FACTORIES.get(connectionPoolType);
        if(connectionProviderFactory == null)
        {
            _logger.warn("Unknown connection pool type: " + connectionPoolType + ".  No connection pooling will be used");
            connectionProviderFactory = new DefaultConnectionProviderFactory();
        }

        try
        {
            Map<String, String> providerAttributes = new HashMap<>();
            Set<String> providerAttributeNames = new HashSet<String>(connectionProviderFactory.getProviderAttributeNames());
            providerAttributeNames.retainAll(parent.getContextKeys(false));
            for(String attr : providerAttributeNames)
            {
                providerAttributes.put(attr, parent.getContextValue(String.class, attr));
            }

            _connectionProvider = connectionProviderFactory.getConnectionProvider(_connectionURL,
                                                                                  settings.getUsername(),
                                                                                  getPlainTextPassword(settings),
                                                                                  providerAttributes);
        }
        catch (SQLException e)
        {
            throw new StoreException("Failed to create connection provider for connectionUrl: " + _connectionURL +
                                    " and username: " + settings.getUsername());
        }

    }

    @Override
    protected Connection getConnection() throws SQLException
    {
        return _connectionProvider.getConnection();
    }

    protected void doClose()
    {
        try
        {
            _connectionProvider.close();
        }
        catch (SQLException e)
        {
            throw new StoreException("Unable to close connection provider ", e);
        }
    }


    @Override
    protected Logger getLogger()
    {
        return _logger;
    }

    @Override
    protected String getSqlBlobType()
    {
        return _blobType;
    }

    @Override
    protected String getSqlVarBinaryType(int size)
    {
        return String.format(_varBinaryType, size);
    }

    @Override
    protected byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException
    {
        if(_useBytesMethodsForBlob)
        {
            return rs.getBytes(col);
        }
        else
        {
            Blob dataAsBlob = rs.getBlob(col);
            return dataAsBlob.getBytes(1,(int) dataAsBlob.length());

        }
    }

    @Override
    public String getSqlBigIntType()
    {
        return _bigIntType;
    }

    @Override
    public String getStoreLocation()
    {
        return _connectionURL;
    }

    @Override
    public File getStoreLocationAsFile()
    {
        return null;
    }

    protected String getPlainTextPassword(final JDBCSettings settings)
    {
        return Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<String>()
        {
            @Override
            public String run()
            {
                return settings.getPassword();
            }
        });
    }
}
