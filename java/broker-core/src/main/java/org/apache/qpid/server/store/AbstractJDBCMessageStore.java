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
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;

public abstract class AbstractJDBCMessageStore implements MessageStore
{
    private static final String DB_VERSION_TABLE_NAME = "QPID_DB_VERSION";

    private static final String QUEUE_ENTRY_TABLE_NAME = "QPID_QUEUE_ENTRIES";

    private static final String META_DATA_TABLE_NAME = "QPID_MESSAGE_METADATA";
    private static final String MESSAGE_CONTENT_TABLE_NAME = "QPID_MESSAGE_CONTENT";


    private static final String XID_TABLE_NAME = "QPID_XIDS";
    private static final String XID_ACTIONS_TABLE_NAME = "QPID_XID_ACTIONS";

    public static final Set<String> MESSAGE_STORE_TABLE_NAMES = new HashSet<String>(Arrays.asList(DB_VERSION_TABLE_NAME,
                                                                                                  META_DATA_TABLE_NAME, MESSAGE_CONTENT_TABLE_NAME,
                                                                                                  QUEUE_ENTRY_TABLE_NAME,
                                                                                                  XID_TABLE_NAME, XID_ACTIONS_TABLE_NAME));

    private static final int DB_VERSION = 8;

    private final AtomicLong _messageId = new AtomicLong(0);

    private static final String CREATE_DB_VERSION_TABLE = "CREATE TABLE "+ DB_VERSION_TABLE_NAME + " ( version int not null )";
    private static final String INSERT_INTO_DB_VERSION = "INSERT INTO "+ DB_VERSION_TABLE_NAME + " ( version ) VALUES ( ? )";
    private static final String SELECT_FROM_DB_VERSION = "SELECT version FROM " + DB_VERSION_TABLE_NAME;
    private static final String UPDATE_DB_VERSION = "UPDATE " + DB_VERSION_TABLE_NAME + " SET version = ?";


    private static final String INSERT_INTO_QUEUE_ENTRY = "INSERT INTO " + QUEUE_ENTRY_TABLE_NAME + " (queue_id, message_id) values (?,?)";
    private static final String DELETE_FROM_QUEUE_ENTRY = "DELETE FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_id = ? AND message_id =?";
    private static final String SELECT_FROM_QUEUE_ENTRY = "SELECT queue_id, message_id FROM " + QUEUE_ENTRY_TABLE_NAME + " ORDER BY queue_id, message_id";
    private static final String SELECT_FROM_QUEUE_ENTRY_FOR_QUEUE = "SELECT queue_id, message_id FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_id = ? ORDER BY queue_id, message_id";

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
    private static final String SELECT_ONE_FROM_META_DATA = "SELECT message_id, meta_data FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";

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

    protected final EventManager _eventManager = new EventManager();

    protected abstract boolean isMessageStoreOpen();

    protected abstract void checkMessageStoreOpen();


    protected void setMaximumMessageId()
    {

        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                setMaxMessageId(conn, "SELECT max(message_id) FROM " + MESSAGE_CONTENT_TABLE_NAME, 1);
                setMaxMessageId(conn, "SELECT max(message_id) FROM " + META_DATA_TABLE_NAME, 1);
                setMaxMessageId(conn, "SELECT queue_id, max(message_id) FROM " + QUEUE_ENTRY_TABLE_NAME + " GROUP BY queue_id " , 2);
            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException(e);
        }

    }

    private void setMaxMessageId(final Connection conn, final String query, int col) throws SQLException
    {
        PreparedStatement statement =
                conn.prepareStatement(query);
        try
        {
            ResultSet rs = statement.executeQuery();
            try
            {
                while(rs.next())
                {
                    long maxMessageId = rs.getLong(col);
                    if(_messageId.get() < maxMessageId)
                    {
                        _messageId.set(maxMessageId);
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
            statement.close();
        }
    }

    protected void upgrade(ConfiguredObject<?> parent) throws StoreException
    {
        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            if (tableExists(DB_VERSION_TABLE_NAME, conn))
            {
                upgradeIfNecessary(parent);
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Failed to upgrade database", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
    }

    private void upgradeIfNecessary(ConfiguredObject<?> parent) throws SQLException
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
                        throw new StoreException(DB_VERSION_TABLE_NAME + " does not contain the database version");
                    }
                    int version = rs.getInt(1);
                    switch (version)
                    {
                        case 6:
                            upgradeFromV6();
                        case 7:
                            upgradeFromV7();
                        case DB_VERSION:
                            return;
                        default:
                            throw new StoreException("Unknown database version: " + version);
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

    private void upgradeFromV7() throws SQLException
    {
        updateDbVersion(8);
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

    protected abstract Logger getLogger();

    protected abstract String getSqlBlobType();

    protected abstract String getSqlVarBinaryType(int size);

    protected abstract String getSqlBigIntType();

    protected void createOrOpenMessageStoreDatabase() throws StoreException
    {
        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();

            createVersionTable(conn);
            createQueueEntryTable(conn);
            createMetaDataTable(conn);
            createMessageContentTable(conn);
            createXidTable(conn);
            createXidActionTable(conn);
        }
        catch (SQLException e)
        {
            throw new StoreException("Failed to create message store tables", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
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
        return JdbcUtils.tableExists(tableName, conn);
    }

    @Override
    public StoredMessage addMessage(StorableMessageMetaData metaData)
    {
        checkMessageStoreOpen();

        if(metaData.isPersistent())
        {
            return new StoredJDBCMessage(getNextMessageId(), metaData);
        }
        else
        {
            return new StoredMemoryMessage(getNextMessageId(), metaData);
        }
    }

    @Override
    public long getNextMessageId()
    {
        return _messageId.incrementAndGet();
    }

    private void removeMessage(long messageId)
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
            throw new StoreException("Error removing message with id " + messageId + " from database: " + e.getMessage(), e);
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

    @Override
    public Transaction newTransaction()
    {
        checkMessageStoreOpen();

        return new JDBCTransaction();
    }

    private void enqueueMessage(ConnectionWrapper connWrapper, final TransactionLogResource queue, Long messageId) throws StoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {
            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Enqueuing message "
                                  + messageId
                                  + " on queue "
                                  + queue.getName()
                                  + " with id " + queue.getId()
                                  + " [Connection"
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
            throw new StoreException("Error writing enqueued message with id " + messageId + " for queue " + queue.getName() + " with id " + queue.getId()
                                     + " to database", e);
        }

    }

    private void dequeueMessage(ConnectionWrapper connWrapper, final TransactionLogResource  queue, Long messageId) throws StoreException
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
                    throw new StoreException("Unable to find message with id " + messageId + " on queue " + queue.getName()
                                             + " with id " + queue.getId());
                }

                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("Dequeuing message " + messageId + " on queue " + queue.getName()
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
            throw new StoreException("Error deleting enqueued message with id " + messageId + " for queue " + queue.getName()
                                     + " with id " + queue.getId() + " from database", e);
        }

    }

    private void removeXid(ConnectionWrapper connWrapper, long format, byte[] globalId, byte[] branchId)
            throws StoreException
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
                    throw new StoreException("Unable to find message with xid");
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
            throw new StoreException("Error deleting enqueued message with xid", e);
        }

    }

    private void recordXid(ConnectionWrapper connWrapper, long format, byte[] globalId, byte[] branchId,
                           Transaction.Record[] enqueues, Transaction.Record[] dequeues) throws StoreException
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
                        stmt.setString(5, record.getResource().getId().toString());
                        stmt.setLong(6, record.getMessage().getMessageNumber());
                        stmt.executeUpdate();
                    }
                }

                if(dequeues != null)
                {
                    stmt.setString(4, "D");
                    for(Transaction.Record record : dequeues)
                    {
                        stmt.setString(5, record.getResource().getId().toString());
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
            throw new StoreException("Error writing xid ", e);
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


    private void commitTran(ConnectionWrapper connWrapper) throws StoreException
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
            throw new StoreException("Error commit tx: " + e.getMessage(), e);
        }
        finally
        {

        }
    }

    private StoreFuture commitTranAsync(ConnectionWrapper connWrapper) throws StoreException
    {
        commitTran(connWrapper);
        return StoreFuture.IMMEDIATE_FUTURE;
    }

    private void abortTran(ConnectionWrapper connWrapper) throws StoreException
    {
        if (connWrapper == null)
        {
            throw new StoreException("Fatal internal error: transactional context is empty at abortTran");
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
            throw new StoreException("Error aborting transaction: " + e.getMessage(), e);
        }

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

            metaData.writeToBuffer(buf);
            ByteArrayInputStream bis = new ByteArrayInputStream(underlying);
            try
            {
                stmt.setBinaryStream(2,bis,underlying.length);
                int result = stmt.executeUpdate();

                if(result == 0)
                {
                    throw new StoreException("Unable to add meta data for message " +messageId);
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
        public TransactionLogResource getResource()
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
        public String getName()
        {
            return _queueId.toString();
        }

        @Override
        public UUID getId()
        {
            return _queueId;
        }

        @Override
        public boolean isDurable()
        {
            return true;
        }
    }

    private StorableMessageMetaData getMetaData(long messageId) throws SQLException
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
                        throw new StoreException("Meta data not found for message with id " + messageId);
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
            JdbcUtils.closeConnection(conn, getLogger());
            throw new StoreException("Error adding content for message " + messageId + ": " + e.getMessage(), e);
        }
        finally
        {
            JdbcUtils.closePreparedStatement(stmt, getLogger());
        }

    }

    private int getContent(long messageId, int offset, ByteBuffer dst)
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
                    throw new StoreException("Offset " + offset + " is greater than message size " + size
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
            throw new StoreException("Error retrieving content from offset " + offset + " for message " + messageId + ": " + e.getMessage(), e);
        }
        finally
        {
            JdbcUtils.closePreparedStatement(stmt, getLogger());
            JdbcUtils.closeConnection(conn, getLogger());
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
                throw new StoreException(e);
            }
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            checkMessageStoreOpen();

            final StoredMessage storedMessage = message.getStoredMessage();
            if(storedMessage instanceof StoredJDBCMessage)
            {
                try
                {
                    ((StoredJDBCMessage) storedMessage).store(_connWrapper.getConnection());
                }
                catch (SQLException e)
                {
                    throw new StoreException("Exception on enqueuing message into message store" + _messageId, e);
                }
            }
            _storeSizeIncrease += storedMessage.getMetaData().getContentSize();
            AbstractJDBCMessageStore.this.enqueueMessage(_connWrapper, queue, message.getMessageNumber());

        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.dequeueMessage(_connWrapper, queue, message.getMessageNumber());
        }

        @Override
        public void commitTran()
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.commitTran(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
        }

        @Override
        public StoreFuture commitTranAsync()
        {
            checkMessageStoreOpen();

            StoreFuture storeFuture = AbstractJDBCMessageStore.this.commitTranAsync(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
            return storeFuture;
        }

        @Override
        public void abortTran()
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.abortTran(_connWrapper);
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId)
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.removeXid(_connWrapper, format, globalId, branchId);
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
        {
            checkMessageStoreOpen();

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
                checkMessageStoreOpen();
                try
                {
                    metaData = AbstractJDBCMessageStore.this.getMetaData(_messageId);
                }
                catch (SQLException e)
                {
                    throw new StoreException(e);
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
                checkMessageStoreOpen();
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
            checkMessageStoreOpen();

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
                throw new StoreException(e);
            }
            finally
            {
                JdbcUtils.closeConnection(conn, AbstractJDBCMessageStore.this.getLogger());
            }
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        @Override
        public void remove()
        {
            checkMessageStoreOpen();

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

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    @Override
    public StoredMessage<?> getMessage(long messageId) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        StoredJDBCMessage message;
        try
        {
            conn = newAutoCommitConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_ONE_FROM_META_DATA))
            {
                stmt.setLong(1, messageId);
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (rs.next())
                    {
                        byte[] dataAsBytes = getBlobAsBytes(rs, 2);
                        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType<?> type = MessageMetaDataTypeRegistry.fromOrdinal(dataAsBytes[0]);
                        StorableMessageMetaData metaData = type.createMetaData(buf);
                        message = new StoredJDBCMessage(messageId, metaData, true);

                    }
                    else
                    {
                        message = null;
                    }
                }
            }
            return message;
        }
        catch (SQLException e)
        {
            throw new StoreException("Error encountered when visiting messages", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
    }


    @Override
    public void visitMessages(MessageHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_ALL_FROM_META_DATA);
                try
                {
                    while (rs.next())
                    {
                        long messageId = rs.getLong(1);
                        byte[] dataAsBytes = getBlobAsBytes(rs, 2);
                        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType<?> type = MessageMetaDataTypeRegistry.fromOrdinal(dataAsBytes[0]);
                        StorableMessageMetaData metaData = type.createMetaData(buf);
                        StoredJDBCMessage message = new StoredJDBCMessage(messageId, metaData, true);
                        if (!handler.handle(message))
                        {
                            break;
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
        catch (SQLException e)
        {
            throw new StoreException("Error encountered when visiting messages", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
    }

    @Override
    public void visitMessageInstances(TransactionLogResource queue, MessageInstanceHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_QUEUE_ENTRY_FOR_QUEUE);
            try
            {
                stmt.setString(1, queue.getId().toString());
                ResultSet rs = stmt.executeQuery();
                try
                {
                    while(rs.next())
                    {
                        String id = rs.getString(1);
                        long messageId = rs.getLong(2);
                        if (!handler.handle(UUID.fromString(id), messageId))
                        {
                            break;
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
        catch(SQLException e)
        {
            throw new StoreException("Error encountered when visiting message instances", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }

    }

    @Override
    public void visitMessageInstances(MessageInstanceHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
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
                        if (!handler.handle(UUID.fromString(id), messageId))
                        {
                            break;
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
        catch(SQLException e)
        {
            throw new StoreException("Error encountered when visiting message instances", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
    }

    @Override
    public void visitDistributedTransactions(DistributedTransactionHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
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

                if (!handler.handle(xid.getFormat(), xid.getGlobalId(), xid.getBranchId(),
                                    enqueues.toArray(new RecordImpl[enqueues.size()]),
                                    dequeues.toArray(new RecordImpl[dequeues.size()])))
                {
                    break;
                }
            }

        }
        catch (SQLException e)
        {
            throw new StoreException("Error encountered when visiting distributed transactions", e);

        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
    }

    protected abstract void storedSizeChange(int storeSizeIncrease);

    @Override
    public void onDelete()
    {
        // TODO should probably check we are closed
        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                List<String> tables = new ArrayList<String>();
                tables.addAll(MESSAGE_STORE_TABLE_NAMES);

                for (String tableName : tables)
                {
                    Statement stmt = conn.createStatement();
                    try
                    {
                        stmt.execute("DROP TABLE " +  tableName);
                    }
                    catch(SQLException e)
                    {
                        getLogger().warn("Failed to drop table '" + tableName + "' :" + e);
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
