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
package org.apache.qpid.server.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.queue.AMQQueue;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

abstract public class AbstractJDBCMessageStore implements MessageStore, DurableConfigurationStore
{
    private static final String DB_VERSION_TABLE_NAME = "QPID_DB_VERSION";
    private static final String CONFIGURATION_VERSION_TABLE_NAME = "QPID_CONFIG_VERSION";

    private static final String QUEUE_ENTRY_TABLE_NAME = "QPID_QUEUE_ENTRIES";

    private static final String META_DATA_TABLE_NAME = "QPID_MESSAGE_METADATA";
    private static final String MESSAGE_CONTENT_TABLE_NAME = "QPID_MESSAGE_CONTENT";

    private static final String LINKS_TABLE_NAME = "QPID_LINKS";
    private static final String BRIDGES_TABLE_NAME = "QPID_BRIDGES";

    private static final String XID_TABLE_NAME = "QPID_XIDS";
    private static final String XID_ACTIONS_TABLE_NAME = "QPID_XID_ACTIONS";

    private static final String CONFIGURED_OBJECTS_TABLE_NAME = "QPID_CONFIGURED_OBJECTS";
    private static final int DEFAULT_CONFIG_VERSION = 0;

    public static String[] ALL_TABLES = new String[] { DB_VERSION_TABLE_NAME, LINKS_TABLE_NAME, BRIDGES_TABLE_NAME, XID_ACTIONS_TABLE_NAME,
        XID_TABLE_NAME, QUEUE_ENTRY_TABLE_NAME, MESSAGE_CONTENT_TABLE_NAME, META_DATA_TABLE_NAME, CONFIGURED_OBJECTS_TABLE_NAME, CONFIGURATION_VERSION_TABLE_NAME };

    private static final int DB_VERSION = 7;

    private final AtomicLong _messageId = new AtomicLong(0);
    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private static final String CREATE_DB_VERSION_TABLE = "CREATE TABLE "+ DB_VERSION_TABLE_NAME + " ( version int not null )";
    private static final String INSERT_INTO_DB_VERSION = "INSERT INTO "+ DB_VERSION_TABLE_NAME + " ( version ) VALUES ( ? )";
    private static final String SELECT_FROM_DB_VERSION = "SELECT version FROM " + DB_VERSION_TABLE_NAME;
    private static final String UPDATE_DB_VERSION = "UPDATE " + DB_VERSION_TABLE_NAME + " SET version = ?";


    private static final String CREATE_CONFIG_VERSION_TABLE = "CREATE TABLE "+ CONFIGURATION_VERSION_TABLE_NAME + " ( version int not null )";
    private static final String INSERT_INTO_CONFIG_VERSION = "INSERT INTO "+ CONFIGURATION_VERSION_TABLE_NAME + " ( version ) VALUES ( ? )";
    private static final String SELECT_FROM_CONFIG_VERSION = "SELECT version FROM " + CONFIGURATION_VERSION_TABLE_NAME;
    private static final String UPDATE_CONFIG_VERSION = "UPDATE " + CONFIGURATION_VERSION_TABLE_NAME + " SET version = ?";


    private static final String INSERT_INTO_QUEUE_ENTRY = "INSERT INTO " + QUEUE_ENTRY_TABLE_NAME + " (queue_id, message_id) values (?,?)";
    private static final String DELETE_FROM_QUEUE_ENTRY = "DELETE FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_id = ? AND message_id =?";
    private static final String SELECT_FROM_QUEUE_ENTRY = "SELECT queue_id, message_id FROM " + QUEUE_ENTRY_TABLE_NAME + " ORDER BY queue_id, message_id";
    private static final String INSERT_INTO_MESSAGE_CONTENT = "INSERT INTO " + MESSAGE_CONTENT_TABLE_NAME
            + "( message_id, content ) values (?, ?)";
    private static final String SELECT_FROM_MESSAGE_CONTENT = "SELECT content FROM " + MESSAGE_CONTENT_TABLE_NAME
            + " WHERE message_id = ?";
    private static final String DELETE_FROM_MESSAGE_CONTENT = "DELETE FROM " + MESSAGE_CONTENT_TABLE_NAME
            + " WHERE message_id = ?";

    private static final String INSERT_INTO_META_DATA = "INSERT INTO " + META_DATA_TABLE_NAME + "( message_id , meta_data ) values (?, ?)";
    private static final String SELECT_FROM_META_DATA =
            "SELECT meta_data FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String DELETE_FROM_META_DATA = "DELETE FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String SELECT_ALL_FROM_META_DATA = "SELECT message_id, meta_data FROM " + META_DATA_TABLE_NAME;

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

    private static final String INSERT_INTO_XIDS =
            "INSERT INTO "+ XID_TABLE_NAME +" ( format, global_id, branch_id ) values (?, ?, ?)";
    private static final String DELETE_FROM_XIDS = "DELETE FROM " + XID_TABLE_NAME
                                                      + " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String SELECT_ALL_FROM_XIDS = "SELECT format, global_id, branch_id FROM " + XID_TABLE_NAME;
    private static final String INSERT_INTO_XID_ACTIONS =
            "INSERT INTO "+ XID_ACTIONS_TABLE_NAME +" ( format, global_id, branch_id, action_type, " +
            "queue_id, message_id ) values (?,?,?,?,?,?) ";
    private static final String DELETE_FROM_XID_ACTIONS = "DELETE FROM " + XID_ACTIONS_TABLE_NAME
                                                   + " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String SELECT_ALL_FROM_XID_ACTIONS =
            "SELECT action_type, queue_id, message_id FROM " + XID_ACTIONS_TABLE_NAME +
            " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String INSERT_INTO_CONFIGURED_OBJECTS = "INSERT INTO " + CONFIGURED_OBJECTS_TABLE_NAME
            + " ( id, object_type, attributes) VALUES (?,?,?)";
    private static final String UPDATE_CONFIGURED_OBJECTS = "UPDATE " + CONFIGURED_OBJECTS_TABLE_NAME
            + " set object_type =?, attributes = ? where id = ?";
    private static final String DELETE_FROM_CONFIGURED_OBJECTS = "DELETE FROM " + CONFIGURED_OBJECTS_TABLE_NAME
            + " where id = ?";
    private static final String FIND_CONFIGURED_OBJECT = "SELECT object_type, attributes FROM " + CONFIGURED_OBJECTS_TABLE_NAME
            + " where id = ?";
    private static final String SELECT_FROM_CONFIGURED_OBJECTS = "SELECT id, object_type, attributes FROM " + CONFIGURED_OBJECTS_TABLE_NAME;

    protected static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    protected final EventManager _eventManager = new EventManager();

    protected final StateManager _stateManager;

    private MessageStoreRecoveryHandler _messageRecoveryHandler;
    private TransactionLogRecoveryHandler _tlogRecoveryHandler;
    private ConfigurationRecoveryHandler _configRecoveryHandler;
    private VirtualHost _virtualHost;

    public AbstractJDBCMessageStore()
    {
        _stateManager = new StateManager(_eventManager);
    }

    @Override
    public void configureConfigStore(VirtualHost virtualHost, ConfigurationRecoveryHandler configRecoveryHandler) throws Exception
    {
        _stateManager.attainState(State.INITIALISING);
        _configRecoveryHandler = configRecoveryHandler;
        _virtualHost = virtualHost;

    }

    @Override
    public void configureMessageStore(VirtualHost virtualHost, MessageStoreRecoveryHandler recoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler) throws Exception
    {
        if(_stateManager.isInState(State.INITIAL))
        {
            _stateManager.attainState(State.INITIALISING);
        }

        _virtualHost = virtualHost;
        _tlogRecoveryHandler = tlogRecoveryHandler;
        _messageRecoveryHandler = recoveryHandler;

        completeInitialisation();
    }

    private void completeInitialisation() throws ClassNotFoundException, SQLException, AMQStoreException
    {
        commonConfiguration();

        _stateManager.attainState(State.INITIALISED);
    }

    @Override
    public void activate() throws Exception
    {
        if(_stateManager.isInState(State.INITIALISING))
        {
            completeInitialisation();
        }
        _stateManager.attainState(State.ACTIVATING);

        // this recovers durable exchanges, queues, and bindings
        if(_configRecoveryHandler != null)
        {
            recoverConfiguration(_configRecoveryHandler);
        }
        if(_messageRecoveryHandler != null)
        {
            recoverMessages(_messageRecoveryHandler);
        }
        if(_tlogRecoveryHandler != null)
        {
            TransactionLogRecoveryHandler.DtxRecordRecoveryHandler dtxrh = recoverQueueEntries(_tlogRecoveryHandler);
            recoverXids(dtxrh);

        }

        _stateManager.attainState(State.ACTIVE);
    }

    private void commonConfiguration()
            throws ClassNotFoundException, SQLException, AMQStoreException
    {
        implementationSpecificConfiguration(_virtualHost.getName(), _virtualHost);
        createOrOpenDatabase();
        upgradeIfNecessary();
    }

    protected void upgradeIfNecessary() throws SQLException, AMQStoreException
    {
        Connection conn = newAutoCommitConnection();
        try
        {

            PreparedStatement statement = conn.prepareStatement(SELECT_FROM_DB_VERSION);
            try
            {
                ResultSet rs = statement.executeQuery();
                try
                {
                    if(!rs.next())
                    {
                        throw new AMQStoreException(DB_VERSION_TABLE_NAME + " does not contain the database version");
                    }
                    int version = rs.getInt(1);
                    switch (version)
                    {
                        case 6:
                            upgradeFromV6();
                        case DB_VERSION:
                            return;
                        default:
                            throw new AMQStoreException("Unknown database version: " + version);
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                statement.close();
            }
        }
        finally
        {
            conn.close();
        }

    }

    private void upgradeFromV6() throws SQLException
    {
        updateDbVersion(7);
    }

    private void updateDbVersion(int newVersion) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {

            PreparedStatement statement = conn.prepareStatement(UPDATE_DB_VERSION);
            try
            {
                statement.setInt(1,newVersion);
                statement.execute();
            }
            finally
            {
                statement.close();
            }
        }
        finally
        {
            conn.close();
        }
    }

    protected abstract void implementationSpecificConfiguration(String name,
                                                                VirtualHost virtualHost) throws ClassNotFoundException, SQLException;

    abstract protected Logger getLogger();

    abstract protected String getSqlBlobType();

    abstract protected String getSqlVarBinaryType(int size);

    abstract protected String getSqlBigIntType();

    protected void createOrOpenDatabase() throws SQLException
    {
        Connection conn = newAutoCommitConnection();

        createVersionTable(conn);
        createConfigVersionTable(conn);
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

    private void createConfigVersionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(CONFIGURATION_VERSION_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_CONFIG_VERSION_TABLE);
            }
            finally
            {
                stmt.close();
            }

            PreparedStatement pstmt = conn.prepareStatement(INSERT_INTO_CONFIG_VERSION);
            try
            {
                pstmt.setInt(1, DEFAULT_CONFIG_VERSION);
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
                stmt.execute("CREATE TABLE " + CONFIGURED_OBJECTS_TABLE_NAME
                        + " ( id VARCHAR(36) not null, object_type varchar(255), attributes "+getSqlBlobType()+",  PRIMARY KEY (id))");
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
                stmt.execute("CREATE TABLE "+ QUEUE_ENTRY_TABLE_NAME +" ( queue_id varchar(36) not null, message_id "
                        + getSqlBigIntType() + " not null, PRIMARY KEY (queue_id, message_id) )");
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
                stmt.execute("CREATE TABLE "
                             + META_DATA_TABLE_NAME
                             + " ( message_id "
                             + getSqlBigIntType()
                             + " not null, meta_data "
                             + getSqlBlobType()
                             + ", PRIMARY KEY ( message_id ) )");
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
                stmt.execute("CREATE TABLE "
                             + MESSAGE_CONTENT_TABLE_NAME
                             + " ( message_id "
                             + getSqlBigIntType()
                             + " not null, content "
                             + getSqlBlobType()
                             + ", PRIMARY KEY (message_id) )");
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
                stmt.execute("CREATE TABLE "+ LINKS_TABLE_NAME +" ( id_lsb " + getSqlBigIntType() + " not null,"
                                                + " id_msb " + getSqlBigIntType() + " not null,"
                                                 + " create_time " + getSqlBigIntType() + " not null,"
                                                 + " arguments "+getSqlBlobType()+",  PRIMARY KEY ( id_lsb, id_msb ))");
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
                stmt.execute("CREATE TABLE "+ BRIDGES_TABLE_NAME +" ( id_lsb " + getSqlBigIntType() + " not null,"
                + " id_msb " + getSqlBigIntType() + " not null,"
                + " create_time " + getSqlBigIntType() + " not null,"
                + " link_id_lsb " + getSqlBigIntType() + " not null,"
                + " link_id_msb " + getSqlBigIntType() + " not null,"
                + " arguments "+getSqlBlobType()+",  PRIMARY KEY ( id_lsb, id_msb ))");
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
                stmt.execute("CREATE TABLE "
                             + XID_TABLE_NAME
                             + " ( format " + getSqlBigIntType() + " not null,"
                             + " global_id "
                             + getSqlVarBinaryType(64)
                             + ", branch_id "
                             + getSqlVarBinaryType(64)
                             + " ,  PRIMARY KEY ( format, "
                             +
                             "global_id, branch_id ))");
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
                stmt.execute("CREATE TABLE " + XID_ACTIONS_TABLE_NAME + " ( format " + getSqlBigIntType() + " not null,"
                             + " global_id " + getSqlVarBinaryType(64) + " not null, branch_id " + getSqlVarBinaryType(
                        64) + " not null, " +
                             "action_type char not null, queue_id varchar(36) not null, message_id " + getSqlBigIntType() + " not null" +
                             ",  PRIMARY KEY ( " +
                             "format, global_id, branch_id, action_type, queue_id, message_id))");
            }
            finally
            {
                stmt.close();
            }
        }
    }

    protected boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        DatabaseMetaData metaData = conn.getMetaData();
        ResultSet rs = metaData.getTables(null, null, "%", null);

        try
        {

            while(rs.next())
            {
                final String table = rs.getString(3);
                if(tableName.equalsIgnoreCase(table))
                {
                    return true;
                }
            }
            return false;
        }
        finally
        {
            rs.close();
        }
    }

    protected void recoverConfiguration(ConfigurationRecoveryHandler recoveryHandler) throws AMQException
    {
        try
        {
            recoveryHandler.beginConfigurationRecovery(this, getConfigVersion());
            loadConfiguredObjects(recoveryHandler);

            setConfigVersion(recoveryHandler.completeConfigurationRecovery());
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
        }
    }

    private void setConfigVersion(int version) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {

            PreparedStatement stmt = conn.prepareStatement(UPDATE_CONFIG_VERSION);
            try
            {
                stmt.setInt(1, version);
                stmt.execute();

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

    private int getConfigVersion() throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {

            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_FROM_CONFIG_VERSION);
                try
                {

                    if(rs.next())
                    {
                        return rs.getInt(1);
                    }
                    return DEFAULT_CONFIG_VERSION;
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

    @Override
    public void close() throws Exception
    {
        if (_closed.compareAndSet(false, true))
        {
            _stateManager.attainState(State.CLOSING);

            doClose();

            _stateManager.attainState(State.CLOSED);
        }
    }


    protected abstract void doClose() throws Exception;

    @Override
    public StoredMessage addMessage(StorableMessageMetaData metaData)
    {
        if(metaData.isPersistent())
        {
            return new StoredJDBCMessage(_messageId.incrementAndGet(), metaData);
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
                        getLogger().warn("Message metadata not found for message id " + messageId);
                    }

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("Deleted metadata for message " + messageId);
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
    public void create(UUID id, String type, Map<String,Object> attributes) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            insertConfiguredObject(new ConfiguredObjectRecord(id, type, attributes));
        }

    }

    @Override
    public void remove(UUID id, String type) throws AMQStoreException
    {
        int results = removeConfiguredObject(id);
        if (results == 0)
        {
            throw new AMQStoreException(type + " with id " + id + " not found");
        }
    }

    @Override
    public void update(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            ConfiguredObjectRecord queueConfiguredObject = loadConfiguredObject(id);
            if (queueConfiguredObject != null)
            {
                ConfiguredObjectRecord newQueueRecord = new ConfiguredObjectRecord(id, type, attributes);
                updateConfiguredObject(newQueueRecord);
            }
        }

    }

    /**
     * Convenience method to create a new Connection configured for TRANSACTION_READ_COMMITED
     * isolation and with auto-commit transactions enabled.
     */
    protected Connection newAutoCommitConnection() throws SQLException
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
    protected Connection newConnection() throws SQLException
    {
        final Connection connection = getConnection();
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

    protected abstract Connection getConnection() throws SQLException;

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
        return new JDBCTransaction();
    }

    public void enqueueMessage(ConnectionWrapper connWrapper, final TransactionLogResource queue, Long messageId) throws AMQStoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {
            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Enqueuing message "
                                   + messageId
                                   + " on queue "
                                   + (queue instanceof AMQQueue
                                      ? ((AMQQueue) queue).getName()
                                      : "")
                                   + queue.getId()
                                   + "[Connection"
                                   + conn
                                   + "]");
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
            getLogger().error("Failed to enqueue: " + e.getMessage(), e);
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
                stmt.setLong(2, messageId);
                int results = stmt.executeUpdate();



                if(results != 1)
                {
                    throw new AMQStoreException("Unable to find message with id " + messageId + " on queue " + (queue instanceof AMQQueue ? ((AMQQueue)queue).getName() : "" )
                           + " with id " + queue.getId());
                }

                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("Dequeuing message " + messageId + " on queue " + (queue instanceof AMQQueue
                                                                                          ? ((AMQQueue) queue).getName()
                                                                                          : "")
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
            getLogger().error("Failed to dequeue: " + e.getMessage(), e);
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
            getLogger().error("Failed to dequeue: " + e.getMessage(), e);
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
            getLogger().error("Failed to enqueue: " + e.getMessage(), e);
            throw new AMQStoreException("Error writing xid ", e);
        }

    }

    protected boolean isConfigStoreOnly()
    {
        return _messageRecoveryHandler == null;
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

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("commit tran completed");
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

        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("abort tran called: " + connWrapper.getConnection());
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
        if(getLogger().isDebugEnabled())
        {
            getLogger().debug("Adding metadata for message " + messageId);
        }

        PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_META_DATA);
        try
        {
            stmt.setLong(1,messageId);

            final int bodySize = 1 + metaData.getStorableSize();
            byte[] underlying = new byte[bodySize];
            underlying[0] = (byte) metaData.getType().ordinal();
            ByteBuffer buf = ByteBuffer.wrap(underlying);
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

    protected void recoverMessages(MessageStoreRecoveryHandler recoveryHandler) throws SQLException
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
                        if(messageId > maxId)
                        {
                            maxId = messageId;
                        }

                        byte[] dataAsBytes = getBlobAsBytes(rs, 2);

                        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType type = MessageMetaDataTypeRegistry.fromOrdinal(dataAsBytes[0]);
                        StorableMessageMetaData metaData = type.createMetaData(buf);
                        StoredJDBCMessage message = new StoredJDBCMessage(messageId, metaData, true);
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


    protected TransactionLogRecoveryHandler.DtxRecordRecoveryHandler recoverQueueEntries(TransactionLogRecoveryHandler recoveryHandler) throws SQLException
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

    private static class RecordImpl implements Transaction.Record, TransactionLogResource, EnqueueableMessage
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
        public EnqueueableMessage getMessage()
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

    protected void recoverXids(TransactionLogRecoveryHandler.DtxRecordRecoveryHandler dtxrh) throws SQLException
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
                        byte[] dataAsBytes = getBlobAsBytes(rs, 1);
                        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType type = MessageMetaDataTypeRegistry.fromOrdinal(dataAsBytes[0]);
                        StorableMessageMetaData metaData = type.createMetaData(buf);

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

    protected abstract byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException;

    private void addContent(Connection conn, long messageId, ByteBuffer src)
    {
        if(getLogger().isDebugEnabled())
        {
            getLogger().debug("Adding content for message " + messageId);
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

                byte[] dataAsBytes = getBlobAsBytes(rs, 1);
                int size = dataAsBytes.length;

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


    protected class JDBCTransaction implements Transaction
    {
        private final ConnectionWrapper _connWrapper;
        private int _storeSizeIncrease;


        protected JDBCTransaction()
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
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
        {
            final StoredMessage storedMessage = message.getStoredMessage();
            if(storedMessage instanceof StoredJDBCMessage)
            {
                try
                {
                    ((StoredJDBCMessage) storedMessage).store(_connWrapper.getConnection());
                }
                catch (SQLException e)
                {
                    throw new AMQStoreException("Exception on enqueuing message " + _messageId, e);
                }
            }
            _storeSizeIncrease += storedMessage.getMetaData().getContentSize();
            AbstractJDBCMessageStore.this.enqueueMessage(_connWrapper, queue, message.getMessageNumber());
        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
        {
            AbstractJDBCMessageStore.this.dequeueMessage(_connWrapper, queue, message.getMessageNumber());

        }

        @Override
        public void commitTran() throws AMQStoreException
        {
            AbstractJDBCMessageStore.this.commitTran(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
        }

        @Override
        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            final StoreFuture storeFuture = AbstractJDBCMessageStore.this.commitTranAsync(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
            return storeFuture;
        }

        @Override
        public void abortTran() throws AMQStoreException
        {
            AbstractJDBCMessageStore.this.abortTran(_connWrapper);
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId) throws AMQStoreException
        {
            AbstractJDBCMessageStore.this.removeXid(_connWrapper, format, globalId, branchId);
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
                throws AMQStoreException
        {
            AbstractJDBCMessageStore.this.recordXid(_connWrapper, format, globalId, branchId, enqueues, dequeues);
        }
    }

    private class StoredJDBCMessage implements StoredMessage
    {

        private final long _messageId;
        private final boolean _isRecovered;

        private StorableMessageMetaData _metaData;
        private volatile SoftReference<StorableMessageMetaData> _metaDataRef;
        private byte[] _data;
        private volatile SoftReference<byte[]> _dataRef;


        StoredJDBCMessage(long messageId, StorableMessageMetaData metaData)
        {
            this(messageId, metaData, false);
        }


        StoredJDBCMessage(long messageId,
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
                    metaData = AbstractJDBCMessageStore.this.getMetaData(_messageId);
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
        public void addContent(int offsetInMessage, ByteBuffer src)
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
        public int getContent(int offsetInMessage, ByteBuffer dst)
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
                return AbstractJDBCMessageStore.this.getContent(_messageId, offsetInMessage, dst);
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
                if(getLogger().isDebugEnabled())
                {
                    getLogger().debug("Error when trying to flush message " + _messageId + " to store: " + e);
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
            AbstractJDBCMessageStore.this.removeMessage(_messageId);
            storedSizeChange(-delta);
        }

        private synchronized void store(final Connection conn) throws SQLException
        {
            if (!stored())
            {
                try
                {
                    storeMetaData(conn, _messageId, _metaData);
                    AbstractJDBCMessageStore.this.addContent(conn, _messageId,
                                                      _data == null ? ByteBuffer.allocate(0) : ByteBuffer.wrap(_data));
                }
                finally
                {
                    _metaData = null;
                    _data = null;
                }

                if(getLogger().isDebugEnabled())
                {
                    getLogger().debug("Storing message " + _messageId + " to store");
                }
            }
        }

        private boolean stored()
        {
            return _metaData == null || _isRecovered;
        }
    }

    protected void closeConnection(final Connection conn)
    {
        if(conn != null)
        {
           try
           {
               conn.close();
           }
           catch (SQLException e)
           {
               getLogger().error("Problem closing connection", e);
           }
        }
    }

    protected void closePreparedStatement(final PreparedStatement stmt)
    {
        if (stmt != null)
        {
            try
            {
                stmt.close();
            }
            catch(SQLException e)
            {
                getLogger().error("Problem closing prepared statement", e);
            }
        }
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
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
                                        final Map<String, Object> attributes = configuredObject.getAttributes();
                                        byte[] attributesAsBytes = new ObjectMapper().writeValueAsBytes(attributes);
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
            catch (JsonMappingException e)
            {
                throw new AMQStoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
            }
            catch (JsonGenerationException e)
            {
                throw new AMQStoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
            }
            catch (IOException e)
            {
                throw new AMQStoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
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
                results = removeConfiguredObject(id, conn);
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

    public UUID[] removeConfiguredObjects(UUID... objects) throws AMQStoreException
    {
        Collection<UUID> removed = new ArrayList<UUID>(objects.length);
        try
        {

            Connection conn = newAutoCommitConnection();
            try
            {
                for(UUID id : objects)
                {
                    if(removeConfiguredObject(id, conn) != 0)
                    {
                        removed.add(id);
                    }
                }
            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error deleting of configured objects " + Arrays.asList(objects) + " from database: " + e.getMessage(), e);
        }
        return removed.toArray(new UUID[removed.size()]);
    }

    private int removeConfiguredObject(final UUID id, final Connection conn) throws SQLException
    {
        final int results;PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_CONFIGURED_OBJECTS);
        try
        {
            stmt.setString(1, id.toString());
            results = stmt.executeUpdate();
        }
        finally
        {
            stmt.close();
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
                    updateConfiguredObject(configuredObject, false, conn);
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

    @Override
    public void update(ConfiguredObjectRecord... records) throws AMQStoreException
    {
        update(false, records);
    }

    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE) || _stateManager.isInState(State.ACTIVATING))
        {
            try
            {
                Connection conn = newConnection();
                try
                {
                    for(ConfiguredObjectRecord record : records)
                    {
                        updateConfiguredObject(record, createIfNecessary, conn);
                    }
                    conn.commit();
                }
                finally
                {
                    conn.close();
                }
            }
            catch (SQLException e)
            {
                throw new AMQStoreException("Error updating configured objects in database: " + e.getMessage(), e);
            }

        }

    }

    private void updateConfiguredObject(ConfiguredObjectRecord configuredObject,
                                        boolean createIfNecessary,
                                        Connection conn)
            throws SQLException, AMQStoreException
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
                                byte[] attributesAsBytes = (new ObjectMapper()).writeValueAsBytes(
                                        configuredObject.getAttributes());
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
                    else if(createIfNecessary)
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
                                final Map<String, Object> attributes = configuredObject.getAttributes();
                                byte[] attributesAsBytes = new ObjectMapper().writeValueAsBytes(attributes);
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
            catch (JsonMappingException e)
            {
                throw new AMQStoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
            }
            catch (JsonGenerationException e)
            {
                throw new AMQStoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
            }
            catch (IOException e)
            {
                throw new AMQStoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
            }
            finally
            {
                stmt.close();
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
                            String attributes = getBlobAsString(rs, 2);
                            result = new ConfiguredObjectRecord(id, type,
                                    (new ObjectMapper()).readValue(attributes,Map.class));
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
        catch (JsonMappingException e)
        {
            throw new AMQStoreException("Error loading of configured object with id " + id + " from database: "
                            + e.getMessage(), e);
        }
        catch (JsonParseException e)
        {
            throw new AMQStoreException("Error loading of configured object with id " + id + " from database: "
                                + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new AMQStoreException("Error loading of configured object with id " + id + " from database: "
                            + e.getMessage(), e);
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error loading of configured object with id " + id + " from database: "
                    + e.getMessage(), e);
        }
        return result;
    }

    private void loadConfiguredObjects(ConfigurationRecoveryHandler recoveryHandler) throws SQLException, AMQStoreException
    {
        Connection conn = newAutoCommitConnection();

        final ObjectMapper objectMapper = new ObjectMapper();
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
                        String attributes = getBlobAsString(rs, 3);
                        recoveryHandler.configuredObject(UUID.fromString(id), objectType,
                                objectMapper.readValue(attributes,Map.class));
                    }
                }
                catch (JsonMappingException e)
                {
                    throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                catch (JsonParseException e)
                {
                    throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                catch (IOException e)
                {
                    throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
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

    protected abstract String getBlobAsString(ResultSet rs, int col) throws SQLException;

    protected abstract void storedSizeChange(int storeSizeIncrease);


    @Override
    public void onDelete()
    {
        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                for (String tableName : ALL_TABLES)
                {
                    Statement stmt = conn.createStatement();
                    try
                    {
                        stmt.execute("DROP TABLE " +  tableName);
                    }
                    finally
                    {
                        stmt.close();
                    }
                }
            }
            finally
            {
                conn.close();
            }
        }
        catch(SQLException e)
        {
            getLogger().error("Exception while deleting store tables", e);
        }
    }

}
