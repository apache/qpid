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


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.ConfiguredObjectHelper;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.EventManager;
import org.apache.qpid.server.store.MessageMetaDataType;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreConstants;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.State;
import org.apache.qpid.server.store.StateManager;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMemoryMessage;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.BindingRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.ExchangeRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.QueueRecoveryHandler;

/**
 * An implementation of a {@link MessageStore} that uses Apache Derby as the persistence
 * mechanism.
 *
 * TODO extract the SQL statements into a generic JDBC store
 */
public class DerbyMessageStore implements MessageStore
{

    private static final Logger _logger = Logger.getLogger(DerbyMessageStore.class);

    private static final String SQL_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";

    private static final String DB_VERSION_TABLE_NAME = "QPID_DB_VERSION";

    private static final String QUEUE_ENTRY_TABLE_NAME = "QPID_QUEUE_ENTRIES";

    private static final String META_DATA_TABLE_NAME = "QPID_MESSAGE_METADATA";
    private static final String MESSAGE_CONTENT_TABLE_NAME = "QPID_MESSAGE_CONTENT";

    private static final String LINKS_TABLE_NAME = "QPID_LINKS";
    private static final String BRIDGES_TABLE_NAME = "QPID_BRIDGES";

    private static final String XID_TABLE_NAME = "QPID_XIDS";
    private static final String XID_ACTIONS_TABLE_NAME = "QPID_XID_ACTIONS";

    private static final String CONFIGURED_OBJECTS_TABLE_NAME = "QPID_CONFIGURED_OBJECTS";

    private static final int DB_VERSION = 6;



    private static Class<Driver> DRIVER_CLASS;
    public static final String MEMORY_STORE_LOCATION = ":memory:";

    private final AtomicLong _messageId = new AtomicLong(0);
    private AtomicBoolean _closed = new AtomicBoolean(false);

    private String _connectionURL;

    private static final String TABLE_EXISTANCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";

    private static final String CREATE_DB_VERSION_TABLE = "CREATE TABLE "+DB_VERSION_TABLE_NAME+" ( version int not null )";
    private static final String INSERT_INTO_DB_VERSION = "INSERT INTO "+DB_VERSION_TABLE_NAME+" ( version ) VALUES ( ? )";

    private static final String CREATE_QUEUE_ENTRY_TABLE = "CREATE TABLE "+QUEUE_ENTRY_TABLE_NAME+" ( queue_id varchar(36) not null, message_id bigint not null, PRIMARY KEY (queue_id, message_id) )";
    private static final String INSERT_INTO_QUEUE_ENTRY = "INSERT INTO " + QUEUE_ENTRY_TABLE_NAME + " (queue_id, message_id) values (?,?)";
    private static final String DELETE_FROM_QUEUE_ENTRY = "DELETE FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_id = ? AND message_id =?";
    private static final String SELECT_FROM_QUEUE_ENTRY = "SELECT queue_id, message_id FROM " + QUEUE_ENTRY_TABLE_NAME + " ORDER BY queue_id, message_id";


    private static final String CREATE_META_DATA_TABLE = "CREATE TABLE " + META_DATA_TABLE_NAME
            + " ( message_id bigint not null, meta_data blob, PRIMARY KEY ( message_id ) )";
    private static final String CREATE_MESSAGE_CONTENT_TABLE = "CREATE TABLE " + MESSAGE_CONTENT_TABLE_NAME
            + " ( message_id bigint not null, content blob , PRIMARY KEY (message_id) )";

    private static final String INSERT_INTO_MESSAGE_CONTENT = "INSERT INTO " + MESSAGE_CONTENT_TABLE_NAME
            + "( message_id, content ) values (?, ?)";
    private static final String SELECT_FROM_MESSAGE_CONTENT = "SELECT content FROM " + MESSAGE_CONTENT_TABLE_NAME
            + " WHERE message_id = ?";
    private static final String DELETE_FROM_MESSAGE_CONTENT = "DELETE FROM " + MESSAGE_CONTENT_TABLE_NAME
            + " WHERE message_id = ?";

    private static final String INSERT_INTO_META_DATA = "INSERT INTO " + META_DATA_TABLE_NAME + "( message_id , meta_data ) values (?, ?)";;
    private static final String SELECT_FROM_META_DATA =
            "SELECT meta_data FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String DELETE_FROM_META_DATA = "DELETE FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String SELECT_ALL_FROM_META_DATA = "SELECT message_id, meta_data FROM " + META_DATA_TABLE_NAME;

    private static final String CREATE_LINKS_TABLE =
            "CREATE TABLE "+LINKS_TABLE_NAME+" ( id_lsb bigint not null,"
                                            + " id_msb bigint not null,"
                                             + " create_time bigint not null,"
                                             + " arguments blob,  PRIMARY KEY ( id_lsb, id_msb ))";
    private static final String SELECT_FROM_LINKS =
            "SELECT create_time, arguments FROM " + LINKS_TABLE_NAME + " WHERE id_lsb = ? and id_msb";
    private static final String DELETE_FROM_LINKS = "DELETE FROM " + LINKS_TABLE_NAME
                                                    + " WHERE id_lsb = ? and id_msb = ?";
    private static final String SELECT_ALL_FROM_LINKS = "SELECT id_lsb, id_msb, create_time, "
                                                        + "arguments FROM " + LINKS_TABLE_NAME;
    private static final String FIND_LINK = "SELECT id_lsb, id_msb FROM " + LINKS_TABLE_NAME + " WHERE id_lsb = ? and"
                                            + " id_msb = ?";
    private static final String INSERT_INTO_LINKS = "INSERT INTO " + LINKS_TABLE_NAME + "( id_lsb, "
                                                  + "id_msb, create_time, arguments ) values (?, ?, ?, ?)";


    private static final String CREATE_BRIDGES_TABLE =
            "CREATE TABLE "+BRIDGES_TABLE_NAME+" ( id_lsb bigint not null,"
            + " id_msb bigint not null,"
            + " create_time bigint not null,"
            + " link_id_lsb bigint not null,"
            + " link_id_msb bigint not null,"
            + " arguments blob,  PRIMARY KEY ( id_lsb, id_msb ))";
    private static final String SELECT_FROM_BRIDGES =
            "SELECT create_time, link_id_lsb, link_id_msb, arguments FROM "
            + BRIDGES_TABLE_NAME + " WHERE id_lsb = ? and id_msb = ?";
    private static final String DELETE_FROM_BRIDGES = "DELETE FROM " + BRIDGES_TABLE_NAME
                                                      + " WHERE id_lsb = ? and id_msb = ?";
    private static final String SELECT_ALL_FROM_BRIDGES = "SELECT id_lsb, id_msb, "
                                                          + " create_time,"
                                                          + " link_id_lsb, link_id_msb, "
                                                        + "arguments FROM " + BRIDGES_TABLE_NAME
                                                        + " WHERE link_id_lsb = ? and link_id_msb = ?";
    private static final String FIND_BRIDGE = "SELECT id_lsb, id_msb FROM " + BRIDGES_TABLE_NAME +
                                              " WHERE id_lsb = ? and id_msb = ?";
    private static final String INSERT_INTO_BRIDGES = "INSERT INTO " + BRIDGES_TABLE_NAME + "( id_lsb, id_msb, "
                                                    + "create_time, "
                                                    + "link_id_lsb, link_id_msb, "
                                                    + "arguments )"
                                                    + " values (?, ?, ?, ?, ?, ?)";

    private static final String CREATE_XIDS_TABLE =
            "CREATE TABLE "+XID_TABLE_NAME+" ( format bigint not null,"
            + " global_id varchar(64) for bit data, branch_id varchar(64) for bit data,  PRIMARY KEY ( format, " +
            "global_id, branch_id ))";
    private static final String INSERT_INTO_XIDS =
            "INSERT INTO "+XID_TABLE_NAME+" ( format, global_id, branch_id ) values (?, ?, ?)";
    private static final String DELETE_FROM_XIDS = "DELETE FROM " + XID_TABLE_NAME
                                                      + " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String SELECT_ALL_FROM_XIDS = "SELECT format, global_id, branch_id FROM " + XID_TABLE_NAME;


    private static final String CREATE_XID_ACTIONS_TABLE =
            "CREATE TABLE "+XID_ACTIONS_TABLE_NAME+" ( format bigint not null,"
            + " global_id varchar(64) for bit data not null, branch_id varchar(64) for bit data not null, " +
            "action_type char not null, queue_id varchar(36) not null, message_id bigint not null" +
            ",  PRIMARY KEY ( " +
            "format, global_id, branch_id, action_type, queue_id, message_id))";
    private static final String INSERT_INTO_XID_ACTIONS =
            "INSERT INTO "+XID_ACTIONS_TABLE_NAME+" ( format, global_id, branch_id, action_type, " +
            "queue_id, message_id ) values (?,?,?,?,?,?) ";
    private static final String DELETE_FROM_XID_ACTIONS = "DELETE FROM " + XID_ACTIONS_TABLE_NAME
                                                   + " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String SELECT_ALL_FROM_XID_ACTIONS =
            "SELECT action_type, queue_id, message_id FROM " + XID_ACTIONS_TABLE_NAME +
            " WHERE format = ? and global_id = ? and branch_id = ?";

    private static final String CREATE_CONFIGURED_OBJECTS_TABLE = "CREATE TABLE " + CONFIGURED_OBJECTS_TABLE_NAME
            + " ( id VARCHAR(36) not null, object_type varchar(255), attributes blob,  PRIMARY KEY (id))";
    private static final String INSERT_INTO_CONFIGURED_OBJECTS = "INSERT INTO " + CONFIGURED_OBJECTS_TABLE_NAME
            + " ( id, object_type, attributes) VALUES (?,?,?)";
    private static final String UPDATE_CONFIGURED_OBJECTS = "UPDATE " + CONFIGURED_OBJECTS_TABLE_NAME
            + " set object_type =?, attributes = ? where id = ?";
    private static final String DELETE_FROM_CONFIGURED_OBJECTS = "DELETE FROM " + CONFIGURED_OBJECTS_TABLE_NAME
            + " where id = ?";
    private static final String FIND_CONFIGURED_OBJECT = "SELECT object_type, attributes FROM " + CONFIGURED_OBJECTS_TABLE_NAME
            + " where id = ?";
    private static final String SELECT_FROM_CONFIGURED_OBJECTS = "SELECT id, object_type, attributes FROM " + CONFIGURED_OBJECTS_TABLE_NAME;

    private final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final String DERBY_SINGLE_DB_SHUTDOWN_CODE = "08006";

    public static final String TYPE = "DERBY";

    private final StateManager _stateManager;

    private final EventManager _eventManager = new EventManager();

    private long _totalStoreSize;
    private boolean _limitBusted;
    private long _persistentSizeLowThreshold;
    private long _persistentSizeHighThreshold;

    private MessageStoreRecoveryHandler _messageRecoveryHandler;

    private TransactionLogRecoveryHandler _tlogRecoveryHandler;

    private ConfigurationRecoveryHandler _configRecoveryHandler;
    private String _storeLocation;

    public DerbyMessageStore()
    {
        _stateManager = new StateManager(_eventManager);
    }

    private ConfiguredObjectHelper _configuredObjectHelper = new ConfiguredObjectHelper();

    @Override
    public void configureConfigStore(String name,
                          ConfigurationRecoveryHandler configRecoveryHandler,
                          Configuration storeConfiguration) throws Exception
    {
        _stateManager.attainState(State.INITIALISING);
        _configRecoveryHandler = configRecoveryHandler;

        commonConfiguration(name, storeConfiguration);

    }

    @Override
    public void configureMessageStore(String name,
                          MessageStoreRecoveryHandler recoveryHandler,
                          TransactionLogRecoveryHandler tlogRecoveryHandler,
                          Configuration storeConfiguration) throws Exception
    {
        _tlogRecoveryHandler = tlogRecoveryHandler;
        _messageRecoveryHandler = recoveryHandler;

        _stateManager.attainState(State.INITIALISED);
    }

    @Override
    public void activate() throws Exception
    {
        _stateManager.attainState(State.ACTIVATING);

        // this recovers durable exchanges, queues, and bindings
        recoverConfiguration(_configRecoveryHandler);
        recoverMessages(_messageRecoveryHandler);
        TransactionLogRecoveryHandler.DtxRecordRecoveryHandler dtxrh = recoverQueueEntries(_tlogRecoveryHandler);
        recoverXids(dtxrh);

        _stateManager.attainState(State.ACTIVE);
    }

    private void commonConfiguration(String name, Configuration storeConfiguration)
            throws ClassNotFoundException, SQLException
    {
        initialiseDriver();

        //Update to pick up QPID_WORK and use that as the default location not just derbyDB

        final String databasePath = storeConfiguration.getString(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY, System.getProperty("QPID_WORK")
                + File.separator + "derbyDB");

        if(!MEMORY_STORE_LOCATION.equals(databasePath))
        {
            File environmentPath = new File(databasePath);
            if (!environmentPath.exists())
            {
                if (!environmentPath.mkdirs())
                {
                    throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                        + "Ensure the path is correct and that the permissions are correct.");
                }
            }
        }

        _storeLocation = databasePath;

        _persistentSizeHighThreshold = storeConfiguration.getLong(MessageStoreConstants.OVERFULL_SIZE_PROPERTY, -1l);
        _persistentSizeLowThreshold = storeConfiguration.getLong(MessageStoreConstants.UNDERFULL_SIZE_PROPERTY, _persistentSizeHighThreshold);
        if(_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
        {
            _persistentSizeLowThreshold = _persistentSizeHighThreshold;
        }

        createOrOpenDatabase(name, databasePath);

        Connection conn = newAutoCommitConnection();;
        try
        {
            _totalStoreSize = getSizeOnDisk(conn);
        }
        finally
        {
            conn.close();
        }
    }

    private static synchronized void initialiseDriver() throws ClassNotFoundException
    {
        if(DRIVER_CLASS == null)
        {
            DRIVER_CLASS = (Class<Driver>) Class.forName(SQL_DRIVER_NAME);
        }
    }

    private void createOrOpenDatabase(String name, final String environmentPath) throws SQLException
    {
        //FIXME this the _vhost name should not be added here, but derby wont use an empty directory as was possibly just created.
        _connectionURL = "jdbc:derby" + (environmentPath.equals(MEMORY_STORE_LOCATION) ? environmentPath : ":" + environmentPath + "/") + name + ";create=true";

        Connection conn = newAutoCommitConnection();

        createVersionTable(conn);
        createConfiguredObjectsTable(conn);
        createQueueEntryTable(conn);
        createMetaDataTable(conn);
        createMessageContentTable(conn);
        createLinkTable(conn);
        createBridgeTable(conn);
        createXidTable(conn);
        createXidActionTable(conn);
        conn.close();
    }



    private void createVersionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(DB_VERSION_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_DB_VERSION_TABLE);
            }
            finally
            {
                stmt.close();
            }

            PreparedStatement pstmt = conn.prepareStatement(INSERT_INTO_DB_VERSION);
            try
            {
                pstmt.setInt(1, DB_VERSION);
                pstmt.execute();
            }
            finally
            {
                pstmt.close();
            }
        }

    }

    private void createConfiguredObjectsTable(final Connection conn) throws SQLException
    {
        if(!tableExists(CONFIGURED_OBJECTS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_CONFIGURED_OBJECTS_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createQueueEntryTable(final Connection conn) throws SQLException
    {
        if(!tableExists(QUEUE_ENTRY_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_QUEUE_ENTRY_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }

    }

    private void createMetaDataTable(final Connection conn) throws SQLException
    {
        if(!tableExists(META_DATA_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_META_DATA_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }

    }


    private void createMessageContentTable(final Connection conn) throws SQLException
    {
        if(!tableExists(MESSAGE_CONTENT_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
            stmt.execute(CREATE_MESSAGE_CONTENT_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }

    }

    private void createLinkTable(final Connection conn) throws SQLException
    {
        if(!tableExists(LINKS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_LINKS_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }


    private void createBridgeTable(final Connection conn) throws SQLException
    {
        if(!tableExists(BRIDGES_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_BRIDGES_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createXidTable(final Connection conn) throws SQLException
    {
        if(!tableExists(XID_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_XIDS_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }


    private void createXidActionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(XID_ACTIONS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_XID_ACTIONS_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(TABLE_EXISTANCE_QUERY);
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

    private void recoverConfiguration(ConfigurationRecoveryHandler recoveryHandler) throws AMQException
    {
        try
        {
            List<ConfiguredObjectRecord> configuredObjects = loadConfiguredObjects();

            ExchangeRecoveryHandler erh = recoveryHandler.begin(this);
            _configuredObjectHelper.recoverExchanges(erh, configuredObjects);

            QueueRecoveryHandler qrh = erh.completeExchangeRecovery();
            _configuredObjectHelper.recoverQueues(qrh, configuredObjects);

            BindingRecoveryHandler brh = qrh.completeQueueRecovery();
            _configuredObjectHelper.recoverBindings(brh, configuredObjects);

            brh.completeBindingRecovery();
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception
    {
        _closed.getAndSet(true);
        _stateManager.attainState(State.CLOSING);

        try
        {
            Connection conn = DriverManager.getConnection(_connectionURL + ";shutdown=true");
            // Shouldn't reach this point - shutdown=true should throw SQLException
            conn.close();
            _logger.error("Unable to shut down the store");
        }
        catch (SQLException e)
        {
            if (e.getSQLState().equalsIgnoreCase(DERBY_SINGLE_DB_SHUTDOWN_CODE))
            {
                //expected and represents a clean shutdown of this database only, do nothing.
            }
            else
            {
                _logger.error("Exception whilst shutting down the store: " + e);
            }
        }

        _stateManager.attainState(State.CLOSED);
    }

    @Override
    public StoredMessage addMessage(StorableMessageMetaData metaData)
    {
        if(metaData.isPersistent())
        {
            return new StoredDerbyMessage(_messageId.incrementAndGet(), metaData);
        }
        else
        {
            return new StoredMemoryMessage(_messageId.incrementAndGet(), metaData);
        }
    }

    public StoredMessage getMessage(long messageNumber)
    {
        return null;
    }

    public void removeMessage(long messageId)
    {
        try
        {
            Connection conn = newConnection();
            try
            {
                PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_META_DATA);
                try
                {
                    stmt.setLong(1,messageId);
                    int results = stmt.executeUpdate();
                    stmt.close();

                    if (results == 0)
                    {
                        _logger.warn("Message metadata not found for message id " + messageId);
                    }

                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Deleted metadata for message " + messageId);
                    }

                    stmt = conn.prepareStatement(DELETE_FROM_MESSAGE_CONTENT);
                    stmt.setLong(1,messageId);
                    results = stmt.executeUpdate();
                }
                finally
                {
                    stmt.close();
                }
                conn.commit();
            }
            catch(SQLException e)
            {
                try
                {
                    conn.rollback();
                }
                catch(SQLException t)
                {
                    // ignore - we are re-throwing underlying exception
                }

                throw e;

            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error removing message with id " + messageId + " from database: " + e.getMessage(), e);
        }

    }

    @Override
    public void createExchange(Exchange exchange) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            ConfiguredObjectRecord configuredObject = _configuredObjectHelper.createExchangeConfiguredObject(exchange);
            insertConfiguredObject(configuredObject);
        }

    }

    @Override
    public void removeExchange(Exchange exchange) throws AMQStoreException
    {
        int results = removeConfiguredObject(exchange.getId());
        if (results == 0)
        {
            throw new AMQStoreException("Exchange " + exchange.getName() + " with id " + exchange.getId() + " not found");
        }
    }

    @Override
    public void bindQueue(Binding binding)
            throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            ConfiguredObjectRecord configuredObject = _configuredObjectHelper.createBindingConfiguredObject(binding);
            insertConfiguredObject(configuredObject);
        }
    }

    @Override
    public void unbindQueue(Binding binding)
            throws AMQStoreException
    {
        int results = removeConfiguredObject(binding.getId());
        if (results == 0)
        {
            throw new AMQStoreException("Binding " + binding + " not found");
        }
    }

    @Override
    public void createQueue(AMQQueue queue) throws AMQStoreException
    {
        createQueue(queue, null);
    }

    @Override
    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQStoreException
    {
        _logger.debug("public void createQueue(AMQQueue queue = " + queue + "): called");

        if (_stateManager.isInState(State.ACTIVE))
        {
            ConfiguredObjectRecord queueConfiguredObject = _configuredObjectHelper.createQueueConfiguredObject(queue, arguments);
            insertConfiguredObject(queueConfiguredObject);
        }
    }

    /**
     * Updates the specified queue in the persistent store, IF it is already present. If the queue
     * is not present in the store, it will not be added.
     *
     * NOTE: Currently only updates the exclusivity.
     *
     * @param queue The queue to update the entry for.
     * @throws AMQStoreException If the operation fails for any reason.
     */
    @Override
    public void updateQueue(final AMQQueue queue) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            ConfiguredObjectRecord queueConfiguredObject = loadConfiguredObject(queue.getId());
            if (queueConfiguredObject != null)
            {
                ConfiguredObjectRecord newQueueRecord = _configuredObjectHelper.updateQueueConfiguredObject(queue, queueConfiguredObject);
                updateConfiguredObject(newQueueRecord);
            }
        }

    }

    /**
     * Convenience method to create a new Connection configured for TRANSACTION_READ_COMMITED
     * isolation and with auto-commit transactions enabled.
     */
    private Connection newAutoCommitConnection() throws SQLException
    {
        final Connection connection = newConnection();
        try
        {
            connection.setAutoCommit(true);
        }
        catch (SQLException sqlEx)
        {

            try
            {
                connection.close();
            }
            finally
            {
                throw sqlEx;
            }
        }

        return connection;
    }

    /**
     * Convenience method to create a new Connection configured for TRANSACTION_READ_COMMITED
     * isolation and with auto-commit transactions disabled.
     */
    private Connection newConnection() throws SQLException
    {
        final Connection connection = DriverManager.getConnection(_connectionURL);
        try
        {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
        catch (SQLException sqlEx)
        {
            try
            {
                connection.close();
            }
            finally
            {
                throw sqlEx;
            }
        }
        return connection;
    }

    @Override
    public void removeQueue(final AMQQueue queue) throws AMQStoreException
    {
        AMQShortString name = queue.getNameShortString();
        _logger.debug("public void removeQueue(AMQShortString name = " + name + "): called");
        int results = removeConfiguredObject(queue.getId());
        if (results == 0)
        {
            throw new AMQStoreException("Queue " + name + " with id " + queue.getId() + " not found");
        }
    }

    private byte[] convertStringMapToBytes(final Map<String, String> arguments) throws AMQStoreException
    {
        byte[] argumentBytes;
        if(arguments == null)
        {
            argumentBytes = new byte[0];
        }
        else
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);


            try
            {
                dos.writeInt(arguments.size());
                for(Map.Entry<String,String> arg : arguments.entrySet())
                {
                    dos.writeUTF(arg.getKey());
                    dos.writeUTF(arg.getValue());
                }
            }
            catch (IOException e)
            {
                // This should never happen
                throw new AMQStoreException(e.getMessage(), e);
            }
            argumentBytes = bos.toByteArray();
        }
        return argumentBytes;
    }



    @Override
    public Transaction newTransaction()
    {
        return new DerbyTransaction();
    }

    public void enqueueMessage(ConnectionWrapper connWrapper, final TransactionLogResource queue, Long messageId) throws AMQStoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Enqueuing message " + messageId + " on queue " + (queue instanceof AMQQueue ? ((AMQQueue)queue).getName() : "" ) + queue.getId()+ "[Connection" + conn + "]");
            }

            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_QUEUE_ENTRY);
            try
            {
                stmt.setString(1, queue.getId().toString());
                stmt.setLong(2,messageId);
                stmt.executeUpdate();
            }
            finally
            {
                stmt.close();
            }
        }
        catch (SQLException e)
        {
            _logger.error("Failed to enqueue: " + e.getMessage(), e);
            throw new AMQStoreException("Error writing enqueued message with id " + messageId + " for queue " + (queue instanceof AMQQueue ? ((AMQQueue)queue).getName() : "" ) + " with id " + queue.getId()
                + " to database", e);
        }

    }

    public void dequeueMessage(ConnectionWrapper connWrapper, final TransactionLogResource  queue, Long messageId) throws AMQStoreException
    {

        Connection conn = connWrapper.getConnection();


        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_QUEUE_ENTRY);
            try
            {
                stmt.setString(1, queue.getId().toString());
                stmt.setLong(2,messageId);
                int results = stmt.executeUpdate();



                if(results != 1)
                {
                    throw new AMQStoreException("Unable to find message with id " + messageId + " on queue " + (queue instanceof AMQQueue ? ((AMQQueue)queue).getName() : "" )
                           + " with id " + queue.getId());
                }

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeuing message " + messageId + " on queue " + (queue instanceof AMQQueue ? ((AMQQueue)queue).getName() : "" )
                            + " with id " + queue.getId());
                }
            }
            finally
            {
                stmt.close();
            }
        }
        catch (SQLException e)
        {
            _logger.error("Failed to dequeue: " + e.getMessage(), e);
            throw new AMQStoreException("Error deleting enqueued message with id " + messageId + " for queue " + (queue instanceof AMQQueue ? ((AMQQueue)queue).getName() : "" )
                    + " with id " + queue.getId() + " from database", e);
        }

    }


    private void removeXid(ConnectionWrapper connWrapper, long format, byte[] globalId, byte[] branchId)
            throws AMQStoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_XIDS);
            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2,globalId);
                stmt.setBytes(3,branchId);
                int results = stmt.executeUpdate();



                if(results != 1)
                {
                    throw new AMQStoreException("Unable to find message with xid");
                }
            }
            finally
            {
                stmt.close();
            }

            stmt = conn.prepareStatement(DELETE_FROM_XID_ACTIONS);
            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2,globalId);
                stmt.setBytes(3,branchId);
                int results = stmt.executeUpdate();

            }
            finally
            {
                stmt.close();
            }

        }
        catch (SQLException e)
        {
            _logger.error("Failed to dequeue: " + e.getMessage(), e);
            throw new AMQStoreException("Error deleting enqueued message with xid", e);
        }

    }


    private void recordXid(ConnectionWrapper connWrapper, long format, byte[] globalId, byte[] branchId,
                           Transaction.Record[] enqueues, Transaction.Record[] dequeues) throws AMQStoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {

            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_XIDS);
            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2, globalId);
                stmt.setBytes(3, branchId);
                stmt.executeUpdate();
            }
            finally
            {
                stmt.close();
            }

            stmt = conn.prepareStatement(INSERT_INTO_XID_ACTIONS);

            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2, globalId);
                stmt.setBytes(3, branchId);

                if(enqueues != null)
                {
                    stmt.setString(4, "E");
                    for(Transaction.Record record : enqueues)
                    {
                        stmt.setString(5, record.getQueue().getId().toString());
                        stmt.setLong(6, record.getMessage().getMessageNumber());
                        stmt.executeUpdate();
                    }
                }

                if(dequeues != null)
                {
                    stmt.setString(4, "D");
                    for(Transaction.Record record : dequeues)
                    {
                        stmt.setString(5, record.getQueue().getId().toString());
                        stmt.setLong(6, record.getMessage().getMessageNumber());
                        stmt.executeUpdate();
                    }
                }

            }
            finally
            {
                stmt.close();
            }

        }
        catch (SQLException e)
        {
            _logger.error("Failed to enqueue: " + e.getMessage(), e);
            throw new AMQStoreException("Error writing xid ", e);
        }

    }

    private static final class ConnectionWrapper
    {
        private final Connection _connection;

        public ConnectionWrapper(Connection conn)
        {
            _connection = conn;
        }

        public Connection getConnection()
        {
            return _connection;
        }
    }


    public void commitTran(ConnectionWrapper connWrapper) throws AMQStoreException
    {

        try
        {
            Connection conn = connWrapper.getConnection();
            conn.commit();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("commit tran completed");
            }

            conn.close();
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error commit tx: " + e.getMessage(), e);
        }
        finally
        {

        }
    }

    public StoreFuture commitTranAsync(ConnectionWrapper connWrapper) throws AMQStoreException
    {
        commitTran(connWrapper);
        return StoreFuture.IMMEDIATE_FUTURE;
    }

    public void abortTran(ConnectionWrapper connWrapper) throws AMQStoreException
    {
        if (connWrapper == null)
        {
            throw new AMQStoreException("Fatal internal error: transactional context is empty at abortTran");
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("abort tran called: " + connWrapper.getConnection());
        }

        try
        {
            Connection conn = connWrapper.getConnection();
            conn.rollback();
            conn.close();
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error aborting transaction: " + e.getMessage(), e);
        }

    }

    public Long getNewMessageId()
    {
        return _messageId.incrementAndGet();
    }


    private void storeMetaData(Connection conn, long messageId, StorableMessageMetaData metaData)
        throws SQLException
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Adding metadata for message " +messageId);
        }

        PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_META_DATA);
        try
        {
            stmt.setLong(1,messageId);

            final int bodySize = 1 + metaData.getStorableSize();
            byte[] underlying = new byte[bodySize];
            underlying[0] = (byte) metaData.getType().ordinal();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(underlying);
            buf.position(1);
            buf = buf.slice();

            metaData.writeToBuffer(0, buf);
            ByteArrayInputStream bis = new ByteArrayInputStream(underlying);
            try
            {
                stmt.setBinaryStream(2,bis,underlying.length);
                int result = stmt.executeUpdate();

                if(result == 0)
                {
                    throw new RuntimeException("Unable to add meta data for message " +messageId);
                }
            }
            finally
            {
                try
                {
                    bis.close();
                }
                catch (IOException e)
                {

                    throw new SQLException(e);
                }
            }

        }
        finally
        {
            stmt.close();
        }

    }




    private void recoverMessages(MessageStoreRecoveryHandler recoveryHandler) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {
            MessageStoreRecoveryHandler.StoredMessageRecoveryHandler messageHandler = recoveryHandler.begin();

            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_ALL_FROM_META_DATA);
                try
                {

                    long maxId = 0;

                    while(rs.next())
                    {

                        long messageId = rs.getLong(1);
                        Blob dataAsBlob = rs.getBlob(2);

                        if(messageId > maxId)
                        {
                            maxId = messageId;
                        }

                        byte[] dataAsBytes = dataAsBlob.getBytes(1,(int) dataAsBlob.length());
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType type = MessageMetaDataType.values()[dataAsBytes[0]];
                        StorableMessageMetaData metaData = type.getFactory().createMetaData(buf);
                        StoredDerbyMessage message = new StoredDerbyMessage(messageId, metaData, true);
                        messageHandler.message(message);
                    }

                    _messageId.set(maxId);

                    messageHandler.completeMessageRecovery();
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
        finally
        {
            conn.close();
        }
    }



    private TransactionLogRecoveryHandler.DtxRecordRecoveryHandler recoverQueueEntries(TransactionLogRecoveryHandler recoveryHandler) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {
            TransactionLogRecoveryHandler.QueueEntryRecoveryHandler queueEntryHandler = recoveryHandler.begin(this);

            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_FROM_QUEUE_ENTRY);
                try
                {
                    while(rs.next())
                    {

                        String id = rs.getString(1);
                        long messageId = rs.getLong(2);
                        queueEntryHandler.queueEntry(UUID.fromString(id), messageId);
                    }
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

            return queueEntryHandler.completeQueueEntryRecovery();
        }
        finally
        {
            conn.close();
        }
    }

    private static final class Xid
    {

        private final long _format;
        private final byte[] _globalId;
        private final byte[] _branchId;

        public Xid(long format, byte[] globalId, byte[] branchId)
        {
            _format = format;
            _globalId = globalId;
            _branchId = branchId;
        }

        public long getFormat()
        {
            return _format;
        }

        public byte[] getGlobalId()
        {
            return _globalId;
        }

        public byte[] getBranchId()
        {
            return _branchId;
        }
    }

    private static class RecordImpl implements Transaction.Record, TransactionLogResource, EnqueableMessage
    {

        private long _messageNumber;
        private UUID _queueId;

        public RecordImpl(UUID queueId, long messageNumber)
        {
            _messageNumber = messageNumber;
            _queueId = queueId;
        }

        @Override
        public TransactionLogResource getQueue()
        {
            return this;
        }

        @Override
        public EnqueableMessage getMessage()
        {
            return this;
        }

        @Override
        public long getMessageNumber()
        {
            return _messageNumber;
        }

        @Override
        public boolean isPersistent()
        {
            return true;
        }

        @Override
        public StoredMessage getStoredMessage()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID getId()
        {
            return _queueId;
        }
    }

    private void recoverXids(TransactionLogRecoveryHandler.DtxRecordRecoveryHandler dtxrh) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {
            List<Xid> xids = new ArrayList<Xid>();

            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_ALL_FROM_XIDS);
                try
                {
                    while(rs.next())
                    {

                        long format = rs.getLong(1);
                        byte[] globalId = rs.getBytes(2);
                        byte[] branchId = rs.getBytes(3);
                        xids.add(new Xid(format, globalId, branchId));
                    }
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



            for(Xid xid : xids)
            {
                List<RecordImpl> enqueues = new ArrayList<RecordImpl>();
                List<RecordImpl> dequeues = new ArrayList<RecordImpl>();

                PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL_FROM_XID_ACTIONS);

                try
                {
                    pstmt.setLong(1, xid.getFormat());
                    pstmt.setBytes(2, xid.getGlobalId());
                    pstmt.setBytes(3, xid.getBranchId());

                    ResultSet rs = pstmt.executeQuery();
                    try
                    {
                        while(rs.next())
                        {

                            String actionType = rs.getString(1);
                            UUID queueId = UUID.fromString(rs.getString(2));
                            long messageId = rs.getLong(3);

                            RecordImpl record = new RecordImpl(queueId, messageId);
                            List<RecordImpl> records = "E".equals(actionType) ? enqueues : dequeues;
                            records.add(record);
                        }
                    }
                    finally
                    {
                        rs.close();
                    }
                }
                finally
                {
                    pstmt.close();
                }

                dtxrh.dtxRecord(xid.getFormat(), xid.getGlobalId(), xid.getBranchId(),
                                enqueues.toArray(new RecordImpl[enqueues.size()]),
                                dequeues.toArray(new RecordImpl[dequeues.size()]));
            }


            dtxrh.completeDtxRecordRecovery();
        }
        finally
        {
            conn.close();
        }

    }

    StorableMessageMetaData getMetaData(long messageId) throws SQLException
    {

        Connection conn = newAutoCommitConnection();
        try
        {
            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_META_DATA);
            try
            {
                stmt.setLong(1,messageId);
                ResultSet rs = stmt.executeQuery();
                try
                {

                    if(rs.next())
                    {
                        Blob dataAsBlob = rs.getBlob(1);

                        byte[] dataAsBytes = dataAsBlob.getBytes(1,(int) dataAsBlob.length());
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType type = MessageMetaDataType.values()[dataAsBytes[0]];
                        StorableMessageMetaData metaData = type.getFactory().createMetaData(buf);

                        return metaData;
                    }
                    else
                    {
                        throw new RuntimeException("Meta data not found for message with id " + messageId);
                    }
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
        finally
        {
            conn.close();
        }
    }


    private void addContent(Connection conn, long messageId, ByteBuffer src)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Adding content for message " +messageId);
        }
        PreparedStatement stmt = null;

        try
        {
            src = src.slice();

            byte[] chunkData = new byte[src.limit()];
            src.duplicate().get(chunkData);

            stmt = conn.prepareStatement(INSERT_INTO_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);

            ByteArrayInputStream bis = new ByteArrayInputStream(chunkData);
            stmt.setBinaryStream(2, bis, chunkData.length);
            stmt.executeUpdate();
        }
        catch (SQLException e)
        {
            closeConnection(conn);
            throw new RuntimeException("Error adding content for message " + messageId + ": " + e.getMessage(), e);
        }
        finally
        {
            closePreparedStatement(stmt);
        }

    }


    public int getContent(long messageId, int offset, ByteBuffer dst)
    {
        Connection conn = null;
        PreparedStatement stmt = null;

        try
        {
            conn = newAutoCommitConnection();

            stmt = conn.prepareStatement(SELECT_FROM_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);
            ResultSet rs = stmt.executeQuery();

            int written = 0;

            if (rs.next())
            {

                Blob dataAsBlob = rs.getBlob(1);

                final int size = (int) dataAsBlob.length();
                byte[] dataAsBytes = dataAsBlob.getBytes(1, size);

                if (offset > size)
                {
                    throw new RuntimeException("Offset " + offset + " is greater than message size " + size
                            + " for message id " + messageId + "!");

                }

                written = size - offset;
                if(written > dst.remaining())
                {
                    written = dst.remaining();
                }

                dst.put(dataAsBytes, offset, written);
            }

            return written;

        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error retrieving content from offset " + offset + " for message " + messageId + ": " + e.getMessage(), e);
        }
        finally
        {
            closePreparedStatement(stmt);
            closeConnection(conn);
        }


    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }


    private class DerbyTransaction implements Transaction
    {
        private final ConnectionWrapper _connWrapper;
        private int _storeSizeIncrease;


        private DerbyTransaction()
        {
            try
            {
                _connWrapper = new ConnectionWrapper(newConnection());
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
        {
            final StoredMessage storedMessage = message.getStoredMessage();
            if(storedMessage instanceof StoredDerbyMessage)
            {
                try
                {
                    ((StoredDerbyMessage) storedMessage).store(_connWrapper.getConnection());
                }
                catch (SQLException e)
                {
                    throw new AMQStoreException("Exception on enqueuing message " + _messageId, e);
                }
            }
            _storeSizeIncrease += storedMessage.getMetaData().getContentSize();
            DerbyMessageStore.this.enqueueMessage(_connWrapper, queue, message.getMessageNumber());
        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
        {
            DerbyMessageStore.this.dequeueMessage(_connWrapper, queue, message.getMessageNumber());

        }

        @Override
        public void commitTran() throws AMQStoreException
        {
            DerbyMessageStore.this.commitTran(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
        }

        @Override
        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            final StoreFuture storeFuture = DerbyMessageStore.this.commitTranAsync(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
            return storeFuture;
        }

        @Override
        public void abortTran() throws AMQStoreException
        {
            DerbyMessageStore.this.abortTran(_connWrapper);
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId) throws AMQStoreException
        {
            DerbyMessageStore.this.removeXid(_connWrapper, format, globalId, branchId);
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
                throws AMQStoreException
        {
            DerbyMessageStore.this.recordXid(_connWrapper, format, globalId, branchId, enqueues, dequeues);
        }
    }



    private class StoredDerbyMessage implements StoredMessage
    {

        private final long _messageId;
        private final boolean _isRecovered;

        private StorableMessageMetaData _metaData;
        private volatile SoftReference<StorableMessageMetaData> _metaDataRef;
        private byte[] _data;
        private volatile SoftReference<byte[]> _dataRef;


        StoredDerbyMessage(long messageId, StorableMessageMetaData metaData)
        {
            this(messageId, metaData, false);
        }


        StoredDerbyMessage(long messageId,
                           StorableMessageMetaData metaData, boolean isRecovered)
        {
            _messageId = messageId;
            _isRecovered = isRecovered;

            if(!_isRecovered)
            {
                _metaData = metaData;
            }
            _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
        }

        @Override
        public StorableMessageMetaData getMetaData()
        {
            StorableMessageMetaData metaData = _metaData == null ? _metaDataRef.get() : _metaData;
            if(metaData == null)
            {
                try
                {
                    metaData = DerbyMessageStore.this.getMetaData(_messageId);
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
                _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
            }

            return metaData;
        }

        @Override
        public long getMessageNumber()
        {
            return _messageId;
        }

        @Override
        public void addContent(int offsetInMessage, java.nio.ByteBuffer src)
        {
            src = src.slice();

            if(_data == null)
            {
                _data = new byte[src.remaining()];
                _dataRef = new SoftReference<byte[]>(_data);
                src.duplicate().get(_data);
            }
            else
            {
                byte[] oldData = _data;
                _data = new byte[oldData.length + src.remaining()];
                _dataRef = new SoftReference<byte[]>(_data);

                System.arraycopy(oldData,0,_data,0,oldData.length);
                src.duplicate().get(_data, oldData.length, src.remaining());
            }

        }

        @Override
        public int getContent(int offsetInMessage, java.nio.ByteBuffer dst)
        {
            byte[] data = _dataRef == null ? null : _dataRef.get();
            if(data != null)
            {
                int length = Math.min(dst.remaining(), data.length - offsetInMessage);
                dst.put(data, offsetInMessage, length);
                return length;
            }
            else
            {
                return DerbyMessageStore.this.getContent(_messageId, offsetInMessage, dst);
            }
        }


        @Override
        public ByteBuffer getContent(int offsetInMessage, int size)
        {
            ByteBuffer buf = ByteBuffer.allocate(size);
            int length = getContent(offsetInMessage, buf);
            buf.position(0);
            buf.limit(length);
            return  buf;
        }

        @Override
        public synchronized StoreFuture flushToStore()
        {
            Connection conn = null;
            try
            {
                if(!stored())
                {
                    conn = newConnection();

                    store(conn);

                    conn.commit();
                    storedSizeChange(getMetaData().getContentSize());
                }
            }
            catch (SQLException e)
            {
                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Error when trying to flush message " + _messageId + " to store: " + e);
                }
                throw new RuntimeException(e);
            }
            finally
            {
                closeConnection(conn);
            }
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        @Override
        public void remove()
        {
            int delta = getMetaData().getContentSize();
            DerbyMessageStore.this.removeMessage(_messageId);
            storedSizeChange(-delta);
        }

        private synchronized void store(final Connection conn) throws SQLException
        {
            if (!stored())
            {
                try
                {
                    storeMetaData(conn, _messageId, _metaData);
                    DerbyMessageStore.this.addContent(conn, _messageId,
                                                      _data == null ? ByteBuffer.allocate(0) : ByteBuffer.wrap(_data));
                }
                finally
                {
                    _metaData = null;
                    _data = null;
                }

                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Storing message " + _messageId + " to store");
                }
            }
        }

        private boolean stored()
        {
            return _metaData == null || _isRecovered;
        }
    }

    private void closeConnection(final Connection conn)
    {
        if(conn != null)
        {
           try
           {
               conn.close();
           }
           catch (SQLException e)
           {
               _logger.error("Problem closing connection", e);
           }
        }
    }

    private void closePreparedStatement(final PreparedStatement stmt)
    {
        if (stmt != null)
        {
            try
            {
                stmt.close();
            }
            catch(SQLException e)
            {
                _logger.error("Problem closing prepared statement", e);
            }
        }
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    @Override
    public String getStoreLocation()
    {
        return _storeLocation;
    }

    private void insertConfiguredObject(ConfiguredObjectRecord configuredObject) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            try
            {
                Connection conn = newAutoCommitConnection();
                try
                {
                    PreparedStatement stmt = conn.prepareStatement(FIND_CONFIGURED_OBJECT);
                    try
                    {
                        stmt.setString(1, configuredObject.getId().toString());
                        ResultSet rs = stmt.executeQuery();
                        try
                        {
                            // If we don't have any data in the result set then we can add this configured object
                            if (!rs.next())
                            {
                                PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_CONFIGURED_OBJECTS);
                                try
                                {
                                    insertStmt.setString(1, configuredObject.getId().toString());
                                    insertStmt.setString(2, configuredObject.getType());
                                    if(configuredObject.getAttributes() == null)
                                    {
                                        insertStmt.setNull(3, Types.BLOB);
                                    }
                                    else
                                    {
                                        byte[] attributesAsBytes = configuredObject.getAttributes().getBytes(UTF8_CHARSET);
                                        ByteArrayInputStream bis = new ByteArrayInputStream(attributesAsBytes);
                                        insertStmt.setBinaryStream(3, bis, attributesAsBytes.length);
                                    }
                                    insertStmt.execute();
                                }
                                finally
                                {
                                    insertStmt.close();
                                }
                            }
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
                finally
                {
                    conn.close();
                }
            }
            catch (SQLException e)
            {
                throw new AMQStoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
            }
        }
    }

    private int removeConfiguredObject(UUID id) throws AMQStoreException
    {
        int results = 0;
        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_CONFIGURED_OBJECTS);
                try
                {
                    stmt.setString(1, id.toString());
                    results = stmt.executeUpdate();
                }
                finally
                {
                    stmt.close();
                }
            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error deleting of configured object with id " + id + " from database: " + e.getMessage(), e);
        }
        return results;
    }

    private void updateConfiguredObject(final ConfiguredObjectRecord configuredObject) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            try
            {
                Connection conn = newAutoCommitConnection();
                try
                {
                    PreparedStatement stmt = conn.prepareStatement(FIND_CONFIGURED_OBJECT);
                    try
                    {
                        stmt.setString(1, configuredObject.getId().toString());
                        ResultSet rs = stmt.executeQuery();
                        try
                        {
                            if (rs.next())
                            {
                                PreparedStatement stmt2 = conn.prepareStatement(UPDATE_CONFIGURED_OBJECTS);
                                try
                                {
                                    stmt2.setString(1, configuredObject.getType());
                                    if (configuredObject.getAttributes() != null)
                                    {
                                        byte[] attributesAsBytes = configuredObject.getAttributes().getBytes(UTF8_CHARSET);
                                        ByteArrayInputStream bis = new ByteArrayInputStream(attributesAsBytes);
                                        stmt2.setBinaryStream(2, bis, attributesAsBytes.length);
                                    }
                                    else
                                    {
                                        stmt2.setNull(2, Types.BLOB);
                                    }
                                    stmt2.setString(3, configuredObject.getId().toString());
                                    stmt2.execute();
                                }
                                finally
                                {
                                    stmt2.close();
                                }
                            }
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
                finally
                {
                    conn.close();
                }
            }
            catch (SQLException e)
            {
                throw new AMQStoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
            }
        }
    }

    private ConfiguredObjectRecord loadConfiguredObject(final UUID id) throws AMQStoreException
    {
        ConfiguredObjectRecord result = null;
        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                PreparedStatement stmt = conn.prepareStatement(FIND_CONFIGURED_OBJECT);
                try
                {
                    stmt.setString(1, id.toString());
                    ResultSet rs = stmt.executeQuery();
                    try
                    {
                        if (rs.next())
                        {
                            String type = rs.getString(1);
                            Blob blob = rs.getBlob(2);
                            String attributes = null;
                            if (blob != null)
                            {
                                attributes = blobToString(blob);
                            }
                            result = new ConfiguredObjectRecord(id, type, attributes);
                        }
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
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error loading of configured object with id " + id + " from database: "
                    + e.getMessage(), e);
        }
        return result;
    }

    private String blobToString(Blob blob) throws SQLException
    {
        byte[] bytes = blob.getBytes(1, (int)blob.length());
        return new String(bytes, UTF8_CHARSET);
    }

    private List<ConfiguredObjectRecord> loadConfiguredObjects() throws SQLException
    {
        ArrayList<ConfiguredObjectRecord> results = new ArrayList<ConfiguredObjectRecord>();
        Connection conn = newAutoCommitConnection();
        try
        {
            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_CONFIGURED_OBJECTS);
            try
            {
                ResultSet rs = stmt.executeQuery();
                try
                {
                    while (rs.next())
                    {
                        String id = rs.getString(1);
                        String objectType = rs.getString(2);
                        String attributes = blobToString(rs.getBlob(3));
                        results.add(new ConfiguredObjectRecord(UUID.fromString(id), objectType, attributes));
                    }
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
        finally
        {
            conn.close();
        }
        return results;
    }

    private synchronized void storedSizeChange(final int delta)
    {
        if(getPersistentSizeHighThreshold() > 0)
        {
            synchronized(this)
            {
                // the delta supplied is an approximation of a store size change. we don;t want to check the statistic every
                // time, so we do so only when there's been enough change that it is worth looking again. We do this by
                // assuming the total size will change by less than twice the amount of the message data change.
                long newSize = _totalStoreSize += 3*delta;

                Connection conn = null;
                try
                {

                    if(!_limitBusted &&  newSize > getPersistentSizeHighThreshold())
                    {
                        conn = newAutoCommitConnection();
                        _totalStoreSize = getSizeOnDisk(conn);
                        if(_totalStoreSize > getPersistentSizeHighThreshold())
                        {
                            _limitBusted = true;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
                        }
                    }
                    else if(_limitBusted && newSize < getPersistentSizeLowThreshold())
                    {
                        long oldSize = _totalStoreSize;
                        conn = newAutoCommitConnection();
                        _totalStoreSize = getSizeOnDisk(conn);
                        if(oldSize <= _totalStoreSize)
                        {

                            reduceSizeOnDisk(conn);

                            _totalStoreSize = getSizeOnDisk(conn);
                        }

                        if(_totalStoreSize < getPersistentSizeLowThreshold())
                        {
                            _limitBusted = false;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
                        }


                    }
                }
                catch (SQLException e)
                {
                    closeConnection(conn);
                    throw new RuntimeException("Exception will processing store size change", e);
                }
            }
        }
    }

    private void reduceSizeOnDisk(Connection conn)
    {
        CallableStatement cs = null;
        PreparedStatement stmt = null;
        try
        {
            String tableQuery =
                    "SELECT S.SCHEMANAME, T.TABLENAME FROM SYS.SYSSCHEMAS S, SYS.SYSTABLES T WHERE S.SCHEMAID = T.SCHEMAID AND T.TABLETYPE='T'";
            stmt = conn.prepareStatement(tableQuery);
            ResultSet rs = null;

            List<String> schemas = new ArrayList<String>();
            List<String> tables = new ArrayList<String>();

            try
            {
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    schemas.add(rs.getString(1));
                    tables.add(rs.getString(2));
                }
            }
            finally
            {
                if(rs != null)
                {
                    rs.close();
                }
            }


            cs = conn.prepareCall
                    ("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?)");

            for(int i = 0; i < schemas.size(); i++)
            {
                cs.setString(1, schemas.get(i));
                cs.setString(2, tables.get(i));
                cs.setShort(3, (short) 0);
                cs.execute();
            }
        }
        catch (SQLException e)
        {
            closeConnection(conn);
            throw new RuntimeException("Error reducing on disk size", e);
        }
        finally
        {
            closePreparedStatement(stmt);
            closePreparedStatement(cs);
        }

    }

    private long getSizeOnDisk(Connection conn)
    {
        PreparedStatement stmt = null;
        try
        {
            String sizeQuery = "SELECT SUM(T2.NUMALLOCATEDPAGES * T2.PAGESIZE) TOTALSIZE" +
                    "    FROM " +
                    "        SYS.SYSTABLES systabs," +
                    "        TABLE (SYSCS_DIAG.SPACE_TABLE(systabs.tablename)) AS T2" +
                    "    WHERE systabs.tabletype = 'T'";

            stmt = conn.prepareStatement(sizeQuery);

            ResultSet rs = null;
            long size = 0l;

            try
            {
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    size = rs.getLong(1);
                }
            }
            finally
            {
                if(rs != null)
                {
                    rs.close();
                }
            }

            return size;

        }
        catch (SQLException e)
        {
            closeConnection(conn);
            throw new RuntimeException("Error establishing on disk size", e);
        }
        finally
        {
            closePreparedStatement(stmt);
        }

    }


    private long getPersistentSizeLowThreshold()
    {
        return _persistentSizeLowThreshold;
    }

    private long getPersistentSizeHighThreshold()
    {
        return _persistentSizeHighThreshold;
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }

}