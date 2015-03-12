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


import java.io.File;
import java.nio.charset.Charset;
import java.security.PrivilegedAction;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.JDBCConnectionProviderFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.AbstractJDBCConfigurationStore;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreProvider;
import org.apache.qpid.server.store.StoreException;

/**
 * Implementation of a DurableConfigurationStore backed by Generic JDBC Database
 * that also provides a MessageStore.
 */
public class GenericJDBCConfigurationStore extends AbstractJDBCConfigurationStore implements MessageStoreProvider
{
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericJDBCConfigurationStore.class);

    private final AtomicBoolean _configurationStoreOpen = new AtomicBoolean();
    private final MessageStore _providedMessageStore = new ProvidedMessageStore();

    private String _connectionURL;
    private ConnectionProvider _connectionProvider;

    private String _blobType;
    private String _varBinaryType;
    private String _bigIntType;
    private boolean _useBytesMethodsForBlob;

    private ConfiguredObject<?> _parent;
    private final Class<? extends ConfiguredObject> _rootClass;

    public GenericJDBCConfigurationStore(final Class<? extends ConfiguredObject> rootClass)
    {
        _rootClass = rootClass;
    }

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent,
                                       final boolean overwrite,
                                       final ConfiguredObjectRecord... initialRecords)
            throws StoreException
    {
        if (_configurationStoreOpen.compareAndSet(false,  true))
        {
            _parent = parent;

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

            String connectionPoolType = settings.getConnectionPoolType() == null ? DefaultConnectionProviderFactory.TYPE : settings.getConnectionPoolType();

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
                                            " and username: " + settings.getUsername(), e);
            }
            _blobType = details.getBlobType();
            _varBinaryType = details.getVarBinaryType();
            _useBytesMethodsForBlob = details.isUseBytesMethodsForBlob();
            _bigIntType = details.getBigintType();

            createOrOpenConfigurationStoreDatabase(overwrite);
            if(hasNoConfigurationEntries())
            {
                update(true, initialRecords);
            }
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

    private class ProvidedMessageStore extends GenericAbstractJDBCMessageStore
    {
        @Override
        protected void doOpen(final ConfiguredObject<?> parent)
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
        public File getStoreLocationAsFile()
        {
            return null;
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
