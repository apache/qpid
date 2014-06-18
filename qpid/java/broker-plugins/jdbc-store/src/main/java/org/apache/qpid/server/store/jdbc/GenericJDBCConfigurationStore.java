/*
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
 */
package org.apache.qpid.server.store.jdbc;


import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.JDBCConnectionProviderFactory;
import org.apache.qpid.server.store.*;
import org.apache.qpid.server.util.MapValueConverter;

/**
 * Implementation of a DurableConfigurationStore backed by Generic JDBC Database
 * that also provides a MessageStore.
 */
public class GenericJDBCConfigurationStore extends AbstractJDBCConfigurationStore implements MessageStoreProvider
{
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final Logger LOGGER = Logger.getLogger(GenericJDBCConfigurationStore.class);

    public static final String CONNECTION_URL = "connectionUrl";
    public static final String CONNECTION_POOL_TYPE = "connectionPoolType";
    public static final String JDBC_BIG_INT_TYPE = "bigIntType";
    public static final String JDBC_BYTES_FOR_BLOB = "bytesForBlob";
    public static final String JDBC_VARBINARY_TYPE = "varbinaryType";
    public static final String JDBC_BLOB_TYPE = "blobType";

    private final AtomicBoolean _configurationStoreOpen = new AtomicBoolean();
    private final MessageStore _providedMessageStore = new ProvidedMessageStore();

    protected String _connectionURL;
    private ConnectionProvider _connectionProvider;

    private String _blobType;
    private String _varBinaryType;
    private String _bigIntType;
    private boolean _useBytesMethodsForBlob;

    private ConfiguredObject<?> _parent;

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings)
            throws StoreException
    {
        if (_configurationStoreOpen.compareAndSet(false,  true))
        {
            _parent = parent;
            _connectionURL = String.valueOf(storeSettings.get(CONNECTION_URL));
            Object poolAttribute = storeSettings.get(CONNECTION_POOL_TYPE);

            JDBCDetails details = null;

            String[] components = _connectionURL.split(":", 3);
            if(components.length >= 2)
            {
                String vendor = components[1];
                details = JDBCDetails.getDetails(vendor);
            }

            if(details == null)
            {
                getLogger().info("Do not recognize vendor from connection URL: " + _connectionURL);

                details = JDBCDetails.getDefaultDetails();
            }

            String connectionPoolType = poolAttribute == null ? DefaultConnectionProviderFactory.TYPE : String.valueOf(poolAttribute);

            JDBCConnectionProviderFactory connectionProviderFactory =
                    JDBCConnectionProviderFactory.FACTORIES.get(connectionPoolType);
            if(connectionProviderFactory == null)
            {
                LOGGER.warn("Unknown connection pool type: "
                             + connectionPoolType
                             + ".  no connection pooling will be used");
                connectionProviderFactory = new DefaultConnectionProviderFactory();
            }

            try
            {
                _connectionProvider = connectionProviderFactory.getConnectionProvider(_connectionURL, storeSettings);
            }
            catch (SQLException e)
            {
                throw new StoreException("Failed to create connection provider for " + _connectionURL);
            }
            _blobType = MapValueConverter.getStringAttribute(JDBC_BLOB_TYPE, storeSettings, details.getBlobType());
            _varBinaryType = MapValueConverter.getStringAttribute(JDBC_VARBINARY_TYPE, storeSettings, details.getVarBinaryType());
            _useBytesMethodsForBlob = MapValueConverter.getBooleanAttribute(JDBC_BYTES_FOR_BLOB, storeSettings, details.isUseBytesMethodsForBlob());
            _bigIntType = MapValueConverter.getStringAttribute(JDBC_BIG_INT_TYPE,
                                                               storeSettings,
                                                               details.getBigintType());

            createOrOpenConfigurationStoreDatabase();
        }
    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {
        checkConfigurationStoreOpen();
        upgradeIfNecessary(_parent);
    }

    @Override
    protected Connection getConnection() throws SQLException
    {
        return _connectionProvider.getConnection();
    }

    @Override
    public void closeConfigurationStore() throws StoreException
    {
        if (_configurationStoreOpen.compareAndSet(true, false))
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
    public String getSqlBigIntType()
    {
        return _bigIntType;
    }

    @Override
    protected String getBlobAsString(ResultSet rs, int col) throws SQLException
    {
        byte[] bytes;
        if(_useBytesMethodsForBlob)
        {
            bytes = rs.getBytes(col);
            return new String(bytes,UTF8_CHARSET);
        }
        else
        {
            Blob blob = rs.getBlob(col);
            if(blob == null)
            {
                return null;
            }
            bytes = blob.getBytes(1, (int)blob.length());
        }
        return new String(bytes, UTF8_CHARSET);

    }

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
    protected void checkConfigurationStoreOpen()
    {
        if (!_configurationStoreOpen.get())
        {
            throw new IllegalStateException("Configuration store is not open");
        }
    }

    @Override
    protected Logger getLogger()
    {
        return LOGGER;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _providedMessageStore;
    }

    private class ProvidedMessageStore extends GenericAbstractJDBCMessageStore
    {
        @Override
        protected void doOpen(final ConfiguredObject<?> parent, final Map<String, Object> messageStoreSettings)
        {
            // Nothing to do, store provided by DerbyConfigurationStore
        }

        @Override
        protected Connection getConnection() throws SQLException
        {
            return GenericJDBCConfigurationStore.this.getConnection();
        }

        @Override
        protected void doClose()
        {
            // Nothing to do, store provided by DerbyConfigurationStore
        }

        @Override
        public String getStoreLocation()
        {
            return GenericJDBCConfigurationStore.this._connectionURL;
        }

        @Override
        protected Logger getLogger()
        {
            return GenericJDBCConfigurationStore.this.getLogger();
        }

        @Override
        protected String getSqlBlobType()
        {
            return GenericJDBCConfigurationStore.this.getSqlBlobType();
        }

        @Override
        protected String getSqlVarBinaryType(int size)
        {
            return GenericJDBCConfigurationStore.this.getSqlVarBinaryType(size);
        }

        @Override
        protected String getSqlBigIntType()
        {
            return GenericJDBCConfigurationStore.this.getSqlBigIntType();
        }

        @Override
        protected byte[] getBlobAsBytes(final ResultSet rs, final int col) throws SQLException
        {
            return GenericJDBCConfigurationStore.this.getBlobAsBytes(rs, col);
        }
    }


}
