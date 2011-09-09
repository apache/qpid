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
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.Blob;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.queue.AMQQueue;

/**
 * An implementation of a {@link MessageStore} that uses Apache Derby as the persistance
 * mechanism.
 * 
 * TODO extract the SQL statements into a generic JDBC store
 */
public class DerbyMessageStore implements MessageStore
{

    private static final Logger _logger = Logger.getLogger(DerbyMessageStore.class);

    public static final String ENVIRONMENT_PATH_PROPERTY = "environment-path";


    private static final String SQL_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";

    private static final String DB_VERSION_TABLE_NAME = "QPID_DB_VERSION";

    private static final String EXCHANGE_TABLE_NAME = "QPID_EXCHANGE";
    private static final String QUEUE_TABLE_NAME = "QPID_QUEUE";
    private static final String BINDINGS_TABLE_NAME = "QPID_BINDINGS";
    private static final String QUEUE_ENTRY_TABLE_NAME = "QPID_QUEUE_ENTRY";

    private static final String META_DATA_TABLE_NAME = "QPID_META_DATA";
    private static final String MESSAGE_CONTENT_TABLE_NAME = "QPID_MESSAGE_CONTENT";

    private static final int DB_VERSION = 3;



    private static Class<Driver> DRIVER_CLASS;

    private final AtomicLong _messageId = new AtomicLong(0);
    private AtomicBoolean _closed = new AtomicBoolean(false);

    private String _connectionURL;

    private static final String TABLE_EXISTANCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";

    private static final String CREATE_DB_VERSION_TABLE = "CREATE TABLE "+DB_VERSION_TABLE_NAME+" ( version int not null )";
    private static final String INSERT_INTO_DB_VERSION = "INSERT INTO "+DB_VERSION_TABLE_NAME+" ( version ) VALUES ( ? )";

    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE "+EXCHANGE_TABLE_NAME+" ( name varchar(255) not null, type varchar(255) not null, autodelete SMALLINT not null, PRIMARY KEY ( name ) )";
    private static final String CREATE_QUEUE_TABLE = "CREATE TABLE "+QUEUE_TABLE_NAME+" ( name varchar(255) not null, owner varchar(255), exclusive SMALLINT not null, arguments blob, PRIMARY KEY ( name ))";
    private static final String CREATE_BINDINGS_TABLE = "CREATE TABLE "+BINDINGS_TABLE_NAME+" ( exchange_name varchar(255) not null, queue_name varchar(255) not null, binding_key varchar(255) not null, arguments blob , PRIMARY KEY ( exchange_name, queue_name, binding_key ) )";
    private static final String SELECT_FROM_QUEUE = "SELECT name, owner, exclusive, arguments FROM " + QUEUE_TABLE_NAME;
    private static final String FIND_QUEUE = "SELECT name, owner FROM " + QUEUE_TABLE_NAME + " WHERE name = ?";
    private static final String UPDATE_QUEUE_EXCLUSIVITY = "UPDATE " + QUEUE_TABLE_NAME + " SET exclusive = ? WHERE name = ?";
    private static final String SELECT_FROM_EXCHANGE = "SELECT name, type, autodelete FROM " + EXCHANGE_TABLE_NAME;
    private static final String SELECT_FROM_BINDINGS =
            "SELECT exchange_name, queue_name, binding_key, arguments FROM " + BINDINGS_TABLE_NAME + " ORDER BY exchange_name";
    private static final String FIND_BINDING =
            "SELECT * FROM " + BINDINGS_TABLE_NAME + " WHERE exchange_name = ? AND queue_name = ? AND binding_key = ? ";
    private static final String INSERT_INTO_EXCHANGE = "INSERT INTO " + EXCHANGE_TABLE_NAME + " ( name, type, autodelete ) VALUES ( ?, ?, ? )";
    private static final String DELETE_FROM_EXCHANGE = "DELETE FROM " + EXCHANGE_TABLE_NAME + " WHERE name = ?";
    private static final String FIND_EXCHANGE = "SELECT name FROM " + EXCHANGE_TABLE_NAME + " WHERE name = ?";
    private static final String INSERT_INTO_BINDINGS = "INSERT INTO " + BINDINGS_TABLE_NAME + " ( exchange_name, queue_name, binding_key, arguments ) values ( ?, ?, ?, ? )";
    private static final String DELETE_FROM_BINDINGS = "DELETE FROM " + BINDINGS_TABLE_NAME + " WHERE exchange_name = ? AND queue_name = ? AND binding_key = ?";
    private static final String INSERT_INTO_QUEUE = "INSERT INTO " + QUEUE_TABLE_NAME + " (name, owner, exclusive, arguments) VALUES (?, ?, ?, ?)";
    private static final String DELETE_FROM_QUEUE = "DELETE FROM " + QUEUE_TABLE_NAME + " WHERE name = ?";

    private static final String CREATE_QUEUE_ENTRY_TABLE = "CREATE TABLE "+QUEUE_ENTRY_TABLE_NAME+" ( queue_name varchar(255) not null, message_id bigint not null, PRIMARY KEY (queue_name, message_id) )";
    private static final String INSERT_INTO_QUEUE_ENTRY = "INSERT INTO " + QUEUE_ENTRY_TABLE_NAME + " (queue_name, message_id) values (?,?)";
    private static final String DELETE_FROM_QUEUE_ENTRY = "DELETE FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_name = ? AND message_id =?";
    private static final String SELECT_FROM_QUEUE_ENTRY = "SELECT queue_name, message_id FROM " + QUEUE_ENTRY_TABLE_NAME + " ORDER BY queue_name, message_id";


    private static final String CREATE_META_DATA_TABLE = "CREATE TABLE "+META_DATA_TABLE_NAME+" ( message_id bigint not null, meta_data blob, PRIMARY KEY ( message_id ) )";
    private static final String CREATE_MESSAGE_CONTENT_TABLE = "CREATE TABLE "+MESSAGE_CONTENT_TABLE_NAME+" ( message_id bigint not null, offset int not null, last_byte int not null, content blob , PRIMARY KEY (message_id, offset) )";

    private static final String INSERT_INTO_MESSAGE_CONTENT = "INSERT INTO " + MESSAGE_CONTENT_TABLE_NAME + "( message_id, offset, last_byte, content ) values (?, ?, ?, ?)";
    private static final String SELECT_FROM_MESSAGE_CONTENT =
            "SELECT offset, content FROM " + MESSAGE_CONTENT_TABLE_NAME + " WHERE message_id = ? AND last_byte > ? AND offset < ? ORDER BY message_id, offset";
    private static final String DELETE_FROM_MESSAGE_CONTENT = "DELETE FROM " + MESSAGE_CONTENT_TABLE_NAME + " WHERE message_id = ?";

    private static final String INSERT_INTO_META_DATA = "INSERT INTO " + META_DATA_TABLE_NAME + "( message_id , meta_data ) values (?, ?)";;
    private static final String SELECT_FROM_META_DATA =
            "SELECT meta_data FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String DELETE_FROM_META_DATA = "DELETE FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String SELECT_ALL_FROM_META_DATA = "SELECT message_id, meta_data FROM " + META_DATA_TABLE_NAME;

    private static final String DERBY_SINGLE_DB_SHUTDOWN_CODE = "08006";


    private LogSubject _logSubject;
    private boolean _configured;


    private enum State
    {
        INITIAL,
        CONFIGURING,
        RECOVERING,
        STARTED,
        CLOSING,
        CLOSED
    }

    private State _state = State.INITIAL;


    public void configureConfigStore(String name,
                          ConfigurationRecoveryHandler recoveryHandler,
                          Configuration storeConfiguration,
                          LogSubject logSubject) throws Exception
    {
        stateTransition(State.INITIAL, State.CONFIGURING);
        _logSubject = logSubject;
        CurrentActor.get().message(_logSubject, ConfigStoreMessages.CREATED(this.getClass().getName()));

        if(!_configured)
        {
            commonConfiguration(name, storeConfiguration, logSubject);
            _configured = true;
        }

        // this recovers durable exchanges, queues, and bindings
        recover(recoveryHandler);


        stateTransition(State.RECOVERING, State.STARTED);

    }


    public void configureMessageStore(String name,
                          MessageStoreRecoveryHandler recoveryHandler,
                          Configuration storeConfiguration,
                          LogSubject logSubject) throws Exception
    {
        CurrentActor.get().message(_logSubject, MessageStoreMessages.CREATED(this.getClass().getName()));

        if(!_configured)
        {

            _logSubject = logSubject;

            commonConfiguration(name, storeConfiguration, logSubject);
            _configured = true;
        }

        recoverMessages(recoveryHandler);

    }



    public void configureTransactionLog(String name,
                          TransactionLogRecoveryHandler recoveryHandler,
                          Configuration storeConfiguration,
                          LogSubject logSubject) throws Exception
    {
        CurrentActor.get().message(_logSubject, TransactionLogMessages.CREATED(this.getClass().getName()));

        if(!_configured)
        {

            _logSubject = logSubject;

            commonConfiguration(name, storeConfiguration, logSubject);
            _configured = true;
        }

        recoverQueueEntries(recoveryHandler);

    }



    private void commonConfiguration(String name, Configuration storeConfiguration, LogSubject logSubject)
            throws ClassNotFoundException, SQLException
    {
        initialiseDriver();

        //Update to pick up QPID_WORK and use that as the default location not just derbyDB

        final String databasePath = storeConfiguration.getString(ENVIRONMENT_PATH_PROPERTY, System.getProperty("QPID_WORK")
                + File.separator + "derbyDB");

        File environmentPath = new File(databasePath);
        if (!environmentPath.exists())
        {
            if (!environmentPath.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                    + "Ensure the path is correct and that the permissions are correct.");
            }
        }

        CurrentActor.get().message(_logSubject, MessageStoreMessages.STORE_LOCATION(environmentPath.getAbsolutePath()));

        createOrOpenDatabase(name, databasePath);
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
        _connectionURL = "jdbc:derby:" + environmentPath + "/" + name + ";create=true";

        Connection conn = newAutoCommitConnection();

        createVersionTable(conn);
        createExchangeTable(conn);
        createQueueTable(conn);
        createBindingsTable(conn);
        createQueueEntryTable(conn);
        createMetaDataTable(conn);
        createMessageContentTable(conn);

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


    private void createExchangeTable(final Connection conn) throws SQLException
    {
        if(!tableExists(EXCHANGE_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_EXCHANGE_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createQueueTable(final Connection conn) throws SQLException
    {
        if(!tableExists(QUEUE_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_QUEUE_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createBindingsTable(final Connection conn) throws SQLException
    {
        if(!tableExists(BINDINGS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_BINDINGS_TABLE);
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

    public void recover(ConfigurationRecoveryHandler recoveryHandler) throws AMQException
    {
        stateTransition(State.CONFIGURING, State.RECOVERING);

        CurrentActor.get().message(_logSubject,MessageStoreMessages.RECOVERY_START());

        try
        {
            ConfigurationRecoveryHandler.QueueRecoveryHandler qrh = recoveryHandler.begin(this);
            loadQueues(qrh);

            ConfigurationRecoveryHandler.ExchangeRecoveryHandler erh = qrh.completeQueueRecovery();
            List<String> exchanges = loadExchanges(erh);
            ConfigurationRecoveryHandler.BindingRecoveryHandler brh = erh.completeExchangeRecovery();
            recoverBindings(brh, exchanges);
            brh.completeBindingRecovery();
        }
        catch (SQLException e)
        {

            throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
        }


    }

    private void loadQueues(ConfigurationRecoveryHandler.QueueRecoveryHandler qrh) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {
            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_FROM_QUEUE);
                try
                {

                    while(rs.next())
                    {
                        String queueName = rs.getString(1);
                        String owner = rs.getString(2);
                        boolean exclusive = rs.getBoolean(3);
                        Blob argumentsAsBlob = rs.getBlob(4);

                        byte[] dataAsBytes = argumentsAsBlob.getBytes(1,(int) argumentsAsBlob.length());
                        FieldTable arguments;
                        if(dataAsBytes.length > 0)
                        {

                            try
                            {
                                arguments = new FieldTable(new DataInputStream(new ByteArrayInputStream(dataAsBytes)),dataAsBytes.length);
                            }
                            catch (IOException e)
                            {
                                throw new RuntimeException("IO Exception should not be thrown",e);
                            }
                        }
                        else
                        {
                            arguments = null;
                        }

                        qrh.queue(queueName, owner, exclusive, arguments);

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


    private List<String> loadExchanges(ConfigurationRecoveryHandler.ExchangeRecoveryHandler erh) throws SQLException
    {

        List<String> exchanges = new ArrayList<String>();
        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();

            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_FROM_EXCHANGE);
                try
                {
                    while(rs.next())
                    {
                        String exchangeName = rs.getString(1);
                        String type = rs.getString(2);
                        boolean autoDelete = rs.getShort(3) != 0;

                        exchanges.add(exchangeName);

                        erh.exchange(exchangeName, type, autoDelete);

                    }
                    return exchanges;
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
            if(conn != null)
            {
                conn.close();
            }
        }

    }

    private void recoverBindings(ConfigurationRecoveryHandler.BindingRecoveryHandler brh, List<String> exchanges) throws SQLException
    {
        _logger.info("Recovering bindings...");

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();

            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_BINDINGS);

            try
            {
                ResultSet rs = stmt.executeQuery();

                try
                {

                    while(rs.next())
                    {
                        String exchangeName = rs.getString(1);
                        String queueName = rs.getString(2);
                        String bindingKey = rs.getString(3);
                        Blob arguments = rs.getBlob(4);
                        java.nio.ByteBuffer buf;

                        if(arguments != null  && arguments.length() != 0)
                        {
                            byte[] argumentBytes = arguments.getBytes(1, (int) arguments.length());
                            buf = java.nio.ByteBuffer.wrap(argumentBytes);
                        }
                        else
                        {
                            buf = null;
                        }

                        brh.binding(exchangeName, queueName, bindingKey, buf);
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
            if(conn != null)
            {
                conn.close();
            }
        }
    }



    public void close() throws Exception
    {
        CurrentActor.get().message(_logSubject,MessageStoreMessages.CLOSED());
        _closed.getAndSet(true);

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
    }

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
                        throw new RuntimeException("Message metadata not found for message id " + messageId);
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

    public void createExchange(Exchange exchange) throws AMQStoreException
    {
        if (_state != State.RECOVERING)
        {
            try
            {
                Connection conn = newAutoCommitConnection();

                try
                {


                    PreparedStatement stmt = conn.prepareStatement(FIND_EXCHANGE);
                    try
                    {
                        stmt.setString(1, exchange.getNameShortString().toString());
                        ResultSet rs = stmt.executeQuery();
                        try
                        {

                            // If we don't have any data in the result set then we can add this exchange
                            if (!rs.next())
                            {

                                PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_EXCHANGE);
                                try
                                {
                                    insertStmt.setString(1, exchange.getName().toString());
                                    insertStmt.setString(2, exchange.getTypeShortString().asString());
                                    insertStmt.setShort(3, exchange.isAutoDelete() ? (short) 1 : (short) 0);
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
                throw new AMQStoreException("Error writing Exchange with name " + exchange.getNameShortString() + " to database: " + e.getMessage(), e);
            }
        }

    }

    public void removeExchange(Exchange exchange) throws AMQStoreException
    {

        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_EXCHANGE);
                try
                {
                    stmt.setString(1, exchange.getNameShortString().toString());
                    int results = stmt.executeUpdate();
                    stmt.close();
                    if(results == 0)
                    {
                        throw new AMQStoreException("Exchange " + exchange.getNameShortString() + " not found");
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
            throw new AMQStoreException("Error deleting Exchange with name " + exchange.getNameShortString() + " from database: " + e.getMessage(), e);
        }
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args)
            throws AMQStoreException
    {
        if (_state != State.RECOVERING)
        {
            try
            {
                Connection conn = newAutoCommitConnection();

                try
                {

                    PreparedStatement stmt = conn.prepareStatement(FIND_BINDING);
                    try
                    {
                        stmt.setString(1, exchange.getNameShortString().toString() );
                        stmt.setString(2, queue.getNameShortString().toString());
                        stmt.setString(3, routingKey == null ? null : routingKey.toString());

                        ResultSet rs = stmt.executeQuery();
                        try
                        {
                            // If this binding is not already in the store then create it.
                            if (!rs.next())
                            {
                                PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_BINDINGS);
                                try
                                {
                                    insertStmt.setString(1, exchange.getNameShortString().toString() );
                                    insertStmt.setString(2, queue.getNameShortString().toString());
                                    insertStmt.setString(3, routingKey == null ? null : routingKey.toString());
                                    if(args != null)
                                    {
                                        /* This would be the Java 6 way of setting a Blob
                                        Blob blobArgs = conn.createBlob();
                                        blobArgs.setBytes(0, args.getDataAsBytes());
                                        stmt.setBlob(4, blobArgs);
                                        */
                                        byte[] bytes = args.getDataAsBytes();
                                        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                                        insertStmt.setBinaryStream(4, bis, bytes.length);
                                    }
                                    else
                                    {
                                        insertStmt.setNull(4, Types.BLOB);
                                    }

                                    insertStmt.executeUpdate();
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
                throw new AMQStoreException("Error writing binding for AMQQueue with name " + queue.getNameShortString() + " to exchange "
                    + exchange.getNameShortString() + " to database: " + e.getMessage(), e);
            }

        }


    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args)
            throws AMQStoreException
    {
        Connection conn = null;

        try
        {
            conn = newAutoCommitConnection();
            // exchange_name varchar(255) not null, queue_name varchar(255) not null, binding_key varchar(255), arguments blob
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_BINDINGS);
            stmt.setString(1, exchange.getNameShortString().toString() );
            stmt.setString(2, queue.getNameShortString().toString());
            stmt.setString(3, routingKey == null ? null : routingKey.toString());
            
            int result = stmt.executeUpdate();
            stmt.close();
            
            if(result != 1)
            {
                 throw new AMQStoreException("Queue binding for queue with name " + queue.getNameShortString() + " to exchange "
                + exchange.getNameShortString() + "  not found");
            }
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error removing binding for AMQQueue with name " + queue.getNameShortString() + " to exchange "
                + exchange.getNameShortString() + " in database: " + e.getMessage(), e);
        }
        finally
        {
            if(conn != null)
            {
               try
               {
                   conn.close();
               }
               catch (SQLException e)
               {
                   _logger.error(e);
               }
            }

        }


    }

    public void createQueue(AMQQueue queue) throws AMQStoreException
    {
        createQueue(queue, null);
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQStoreException
    {
        _logger.debug("public void createQueue(AMQQueue queue = " + queue + "): called");

        if (_state != State.RECOVERING)
        {
            try
            {
                Connection conn = newAutoCommitConnection();

                PreparedStatement stmt = conn.prepareStatement(FIND_QUEUE);
                try
                {
                    stmt.setString(1, queue.getNameShortString().toString());
                    ResultSet rs = stmt.executeQuery();
                    try
                    {

                        // If we don't have any data in the result set then we can add this queue
                        if (!rs.next())
                        {
                            PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_QUEUE);

                            try
                            {
                                String owner = queue.getOwner() == null ? null : queue.getOwner().toString();

                                insertStmt.setString(1, queue.getNameShortString().toString());
                                insertStmt.setString(2, owner);
                                insertStmt.setBoolean(3,queue.isExclusive());

                                final byte[] underlying;
                                if(arguments != null)
                                {
                                    underlying = arguments.getDataAsBytes();
                                }
                                else
                                {
                                    underlying = new byte[0];
                                }

                                ByteArrayInputStream bis = new ByteArrayInputStream(underlying);
                                insertStmt.setBinaryStream(4,bis,underlying.length);

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
                conn.close();

            }
            catch (SQLException e)
            {
                throw new AMQStoreException("Error writing AMQQueue with name " + queue.getNameShortString() + " to database: " + e.getMessage(), e);
            }
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
    public void updateQueue(final AMQQueue queue) throws AMQStoreException
    {
        if (_state != State.RECOVERING)
        {
            try
            {
                Connection conn = newAutoCommitConnection();

                try
                {
                    PreparedStatement stmt = conn.prepareStatement(FIND_QUEUE);
                    try
                    {
                        stmt.setString(1, queue.getNameShortString().toString());

                        ResultSet rs = stmt.executeQuery();
                        try
                        {
                            if (rs.next())
                            {
                                PreparedStatement stmt2 = conn.prepareStatement(UPDATE_QUEUE_EXCLUSIVITY);
                                try
                                {
                                    stmt2.setBoolean(1,queue.isExclusive());
                                    stmt2.setString(2, queue.getNameShortString().toString());

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
                throw new AMQStoreException("Error updating AMQQueue with name " + queue.getNameShortString() + " to database: " + e.getMessage(), e);
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

    public void removeQueue(final AMQQueue queue) throws AMQStoreException
    {
        AMQShortString name = queue.getNameShortString();
        _logger.debug("public void removeQueue(AMQShortString name = " + name + "): called");
        Connection conn = null;

        try
        {
            conn = newAutoCommitConnection();
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_QUEUE);
            stmt.setString(1, name.toString());
            int results = stmt.executeUpdate();
            stmt.close();
            
            if (results == 0)
            {
                throw new AMQStoreException("Queue " + name + " not found");
            }
        }
        catch (SQLException e)
        {
            throw new AMQStoreException("Error deleting AMQQueue with name " + name + " from database: " + e.getMessage(), e);
        }
        finally
        {
            if(conn != null)
            {
               try
               {
                   conn.close();
               }
               catch (SQLException e)
               {
                   _logger.error(e);
               }
            }

        }


    }

    public Transaction newTransaction()
    {
        return new DerbyTransaction();
    }

    public void enqueueMessage(ConnectionWrapper connWrapper, final TransactionLogResource queue, Long messageId) throws AMQStoreException
    {
        String name = queue.getResourceName();

        Connection conn = connWrapper.getConnection();


        try
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Enqueuing message " + messageId + " on queue " + name + "[Connection" + conn + "]");
            }
            
            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_QUEUE_ENTRY);
            try
            {
                stmt.setString(1,name);
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
            throw new AMQStoreException("Error writing enqueued message with id " + messageId + " for queue " + name
                + " to database", e);
        }

    }

    public void dequeueMessage(ConnectionWrapper connWrapper, final TransactionLogResource  queue, Long messageId) throws AMQStoreException
    {
        String name = queue.getResourceName();


        Connection conn = connWrapper.getConnection();


        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_QUEUE_ENTRY);
            try
            {
                stmt.setString(1,name);
                stmt.setLong(2,messageId);
                int results = stmt.executeUpdate();



                if(results != 1)
                {
                    throw new AMQStoreException("Unable to find message with id " + messageId + " on queue " + name);
                }

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeuing message " + messageId + " on queue " + name );//+ "[Connection" + conn + "]");
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
            throw new AMQStoreException("Error deleting enqueued message with id " + messageId + " for queue " + name
                + " from database", e);
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
        return new StoreFuture()
                    {
                        public boolean isComplete()
                        {
                            return true;
                        }

                        public void waitForCompletion()
                        {

                        }
                    };

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
                        StoredDerbyMessage message = new StoredDerbyMessage(messageId, metaData, false);
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



    private void recoverQueueEntries(TransactionLogRecoveryHandler recoveryHandler) throws SQLException
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

                        String queueName = rs.getString(1);
                        long messageId = rs.getLong(2);
                        queueEntryHandler.queueEntry(queueName,messageId);
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

            queueEntryHandler.completeQueueEntryRecovery();
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


    private void addContent(Connection conn, long messageId, int offset, ByteBuffer src)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Adding content chunk offset " + offset + " for message " +messageId);
        }

        try
        {
            src = src.slice();

            byte[] chunkData = new byte[src.limit()];
            src.duplicate().get(chunkData);

            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);
            stmt.setInt(2, offset);
            stmt.setInt(3, offset+chunkData.length);


            /* this would be the Java 6 way of doing things
            Blob dataAsBlob = conn.createBlob();
            dataAsBlob.setBytes(1L, chunkData);
            stmt.setBlob(3, dataAsBlob);
            */
            ByteArrayInputStream bis = new ByteArrayInputStream(chunkData);
            stmt.setBinaryStream(4, bis, chunkData.length);
            stmt.executeUpdate();
            stmt.close();
        }
        catch (SQLException e)
        {
            if(conn != null)
            {
                try
                {
                    conn.close();
                }
                catch (SQLException e1)
                {

                }
            }

            throw new RuntimeException("Error adding content chunk offset " + offset + " for message " + messageId + ": " + e.getMessage(), e);
        }

    }


    public int getContent(long messageId, int offset, ByteBuffer dst)
    {
        Connection conn = null;


        try
        {
            conn = newAutoCommitConnection();

            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);
            stmt.setInt(2, offset);
            stmt.setInt(3, offset+dst.remaining());
            ResultSet rs = stmt.executeQuery();

            int written = 0;

            while(rs.next())
            {
                int offsetInMessage = rs.getInt(1);
                Blob dataAsBlob = rs.getBlob(2);

                final int size = (int) dataAsBlob.length();
                byte[] dataAsBytes = dataAsBlob.getBytes(1, size);

                int posInArray = offset + written - offsetInMessage;
                int count = size - posInArray;
                if(count > dst.remaining())
                {
                    count = dst.remaining();
                }
                dst.put(dataAsBytes,posInArray,count);
                written+=count;

                if(dst.remaining() == 0)
                {
                    break;
                }
            }

            stmt.close();
            conn.close();
            return written;

        }
        catch (SQLException e)
        {
            if(conn != null)
            {
                try
                {
                    conn.close();
                }
                catch (SQLException e1)
                {

                }
            }

            throw new RuntimeException("Error retrieving content from offset " + offset + " for message " + messageId + ": " + e.getMessage(), e);
        }



    }

    public boolean isPersistent()
    {
        return true;
    }


    private synchronized void stateTransition(State requiredState, State newState) throws AMQStoreException
    {
        if (_state != requiredState)
        {
            throw new AMQStoreException("Cannot transition to the state: " + newState + "; need to be in state: " + requiredState
                + "; currently in state: " + _state);
        }

        _state = newState;
    }


    private class DerbyTransaction implements Transaction
    {
        private final ConnectionWrapper _connWrapper;


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

        public void enqueueMessage(TransactionLogResource queue, Long messageId) throws AMQStoreException
        {
            DerbyMessageStore.this.enqueueMessage(_connWrapper, queue, messageId);
        }

        public void dequeueMessage(TransactionLogResource queue, Long messageId) throws AMQStoreException
        {
            DerbyMessageStore.this.dequeueMessage(_connWrapper, queue, messageId);

        }

        public void commitTran() throws AMQStoreException
        {
            DerbyMessageStore.this.commitTran(_connWrapper);
        }

        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            return DerbyMessageStore.this.commitTranAsync(_connWrapper);
        }

        public void abortTran() throws AMQStoreException
        {
            DerbyMessageStore.this.abortTran(_connWrapper);
        }
    }

    private class StoredDerbyMessage implements StoredMessage
    {

        private final long _messageId;
        private volatile SoftReference<StorableMessageMetaData> _metaDataRef;
        private Connection _conn;

        StoredDerbyMessage(long messageId, StorableMessageMetaData metaData)
        {
            this(messageId, metaData, true);
        }


        StoredDerbyMessage(long messageId,
                           StorableMessageMetaData metaData, boolean persist)
        {
            try
            {
                _messageId = messageId;

                _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
                if(persist)
                {
                    _conn = newConnection();
                    storeMetaData(_conn, messageId, metaData);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }

        }

        public StorableMessageMetaData getMetaData()
        {
            StorableMessageMetaData metaData = _metaDataRef.get();
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

        public long getMessageNumber()
        {
            return _messageId;
        }

        public void addContent(int offsetInMessage, java.nio.ByteBuffer src)
        {
            DerbyMessageStore.this.addContent(_conn, _messageId, offsetInMessage, src);
        }

        public int getContent(int offsetInMessage, java.nio.ByteBuffer dst)
        {
            return DerbyMessageStore.this.getContent(_messageId, offsetInMessage, dst);
        }

        public StoreFuture flushToStore()
        {
            try
            {
                if(_conn != null)
                {
                    if(_logger.isDebugEnabled())
                    {
                        _logger.debug("Flushing message " + _messageId + " to store");
                    }
                    
                    _conn.commit();
                    _conn.close();
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
                _conn = null;
            }
            return IMMEDIATE_FUTURE;
        }

        public void remove()
        {
            flushToStore();
            DerbyMessageStore.this.removeMessage(_messageId);
        }
    }


}
