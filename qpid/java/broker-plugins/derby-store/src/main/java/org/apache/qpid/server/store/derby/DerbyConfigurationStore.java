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


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.AbstractJDBCConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreProvider;
import org.apache.qpid.server.store.StoreException;

/**
 * Implementation of a DurableConfigurationStore backed by Apache Derby
 * that also provides a MessageStore.
 */
public class DerbyConfigurationStore extends AbstractJDBCConfigurationStore
        implements MessageStoreProvider, DurableConfigurationStore
{
    private static final Logger LOGGER = Logger.getLogger(DerbyConfigurationStore.class);

    private final AtomicBoolean _configurationStoreOpen = new AtomicBoolean();
    private final ProvidedMessageStore _providedMessageStore = new ProvidedMessageStore();

    private String _connectionURL;
    private String _storeLocation;

    private ConfiguredObject<?> _parent;

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings)
            throws StoreException
    {
        if (_configurationStoreOpen.compareAndSet(false,  true))
        {
            _parent = parent;
            DerbyUtils.loadDerbyDriver();

            String databasePath =  (String) storeSettings.get(MessageStore.STORE_PATH);

            _storeLocation = databasePath;
            _connectionURL = DerbyUtils.createConnectionUrl(parent.getName(), databasePath);

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
        checkConfigurationStoreOpen();
        return DriverManager.getConnection(_connectionURL);
    }

    @Override
    public void closeConfigurationStore() throws StoreException
    {
        if (_providedMessageStore.isMessageStoreOpen())
        {
            throw new IllegalStateException("Cannot close the store as the provided message store is still open");
        }

        if (_configurationStoreOpen.compareAndSet(true,  false))
        {
            try
            {
                DerbyUtils.shutdownDatabase(_connectionURL);
            }
            catch (SQLException e)
            {
                throw new StoreException("Error closing configuration store", e);
            }
        }
    }

    @Override
    protected String getSqlBlobType()
    {
        return "blob";
    }

    @Override
    protected String getSqlVarBinaryType(int size)
    {
        return "varchar("+size+") for bit data";
    }

    @Override
    protected String getSqlBigIntType()
    {
        return "bigint";
    }

    @Override
    protected String getBlobAsString(ResultSet rs, int col) throws SQLException
    {
        return DerbyUtils.getBlobAsString(rs, col);
    }

    @Override
    public void onDelete()
    {
        if (_providedMessageStore.isMessageStoreOpen())
        {
            throw new IllegalStateException("Cannot delete the store as the provided message store is still open");
        }

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Deleting store " + _storeLocation);
        }

        try
        {
            DerbyUtils.deleteDatabaseLocation(_storeLocation);
        }
        catch (StoreException se)
        {
            LOGGER.debug("Failed to delete the store at location " + _storeLocation);
        }
        finally
        {
            _storeLocation = null;
        }
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _providedMessageStore;
    }

    @Override
    protected boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        return DerbyUtils.tableExists(tableName, conn);
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

    private class ProvidedMessageStore extends AbstractDerbyMessageStore
    {
        @Override
        protected void doOpen(final ConfiguredObject<?> parent, final Map<String, Object> messageStoreSettings)
        {
            // Nothing to do, store provided by DerbyConfigurationStore
        }

        @Override
        protected Connection getConnection() throws SQLException
        {
            checkMessageStoreOpen();
            return DerbyConfigurationStore.this.getConnection();
        }

        @Override
        protected void doClose()
        {
            // Nothing to do, store provided by DerbyConfigurationStore
        }

        @Override
        public String getStoreLocation()
        {
            return DerbyConfigurationStore.this._storeLocation;
        }

        @Override
        protected Logger getLogger()
        {
            return DerbyConfigurationStore.this.getLogger();
        }
    }
}
