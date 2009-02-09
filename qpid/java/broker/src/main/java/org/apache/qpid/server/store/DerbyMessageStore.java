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

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.queue.QueueRegistry;

import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.sql.DriverManager;
import java.sql.Driver;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Blob;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;


public class DerbyMessageStore implements MessageStore
{

    private static final Logger _logger = Logger.getLogger(DerbyMessageStore.class);

    private static final String ENVIRONMENT_PATH_PROPERTY = "environment-path";


    private static final String SQL_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";

    private static final String DB_VERSION_TABLE_NAME = "QPID_DB_VERSION";

    private static final String EXCHANGE_TABLE_NAME = "QPID_EXCHANGE";
    private static final String QUEUE_TABLE_NAME = "QPID_QUEUE";
    private static final String BINDINGS_TABLE_NAME = "QPID_BINDINGS";
    private static final String QUEUE_ENTRY_TABLE_NAME = "QPID_QUEUE_ENTRY";
    private static final String MESSAGE_META_DATA_TABLE_NAME = "QPID_MESSAGE_META_DATA";
    private static final String MESSAGE_CONTENT_TABLE_NAME = "QPID_MESSAGE_CONTENT";

    private static final int DB_VERSION = 1;



    private VirtualHost _virtualHost;
    private static Class<Driver> DRIVER_CLASS;

    private final AtomicLong _messageId = new AtomicLong(1);
    private AtomicBoolean _closed = new AtomicBoolean(false);

    private String _connectionURL;



    private static final String CREATE_DB_VERSION_TABLE = "CREATE TABLE "+DB_VERSION_TABLE_NAME+" ( version int not null )";
    private static final String INSERT_INTO_DB_VERSION = "INSERT INTO "+DB_VERSION_TABLE_NAME+" ( version ) VALUES ( ? )";
    private static final String CREATE_EXCHANGE_TABLE = "CREATE TABLE "+EXCHANGE_TABLE_NAME+" ( name varchar(255) not null, type varchar(255) not null, autodelete SMALLINT not null, PRIMARY KEY ( name ) )";
    private static final String CREATE_QUEUE_TABLE = "CREATE TABLE "+QUEUE_TABLE_NAME+" ( name varchar(255) not null, owner varchar(255), PRIMARY KEY ( name ) )";
    private static final String CREATE_BINDINGS_TABLE = "CREATE TABLE "+BINDINGS_TABLE_NAME+" ( exchange_name varchar(255) not null, queue_name varchar(255) not null, binding_key varchar(255) not null, arguments blob , PRIMARY KEY ( exchange_name, queue_name, binding_key ) )";
    private static final String CREATE_QUEUE_ENTRY_TABLE = "CREATE TABLE "+QUEUE_ENTRY_TABLE_NAME+" ( queue_name varchar(255) not null, message_id bigint not null, PRIMARY KEY (queue_name, message_id) )";
    private static final String CREATE_MESSAGE_META_DATA_TABLE = "CREATE TABLE "+MESSAGE_META_DATA_TABLE_NAME+" ( message_id bigint not null, exchange_name varchar(255) not null, routing_key varchar(255), flag_mandatory smallint not null, flag_immediate smallint not null, content_header blob, chunk_count int not null, PRIMARY KEY ( message_id ) )";
    private static final String CREATE_MESSAGE_CONTENT_TABLE = "CREATE TABLE "+MESSAGE_CONTENT_TABLE_NAME+" ( message_id bigint not null, chunk_id int not null, content_chunk blob , PRIMARY KEY (message_id, chunk_id) )";
    private static final String SELECT_FROM_QUEUE = "SELECT name, owner FROM " + QUEUE_TABLE_NAME;
    private static final String SELECT_FROM_EXCHANGE = "SELECT name, type, autodelete FROM " + EXCHANGE_TABLE_NAME;
    private static final String SELECT_FROM_BINDINGS =
            "SELECT queue_name, binding_key, arguments FROM " + BINDINGS_TABLE_NAME + " WHERE exchange_name = ?";
    private static final String DELETE_FROM_MESSAGE_META_DATA = "DELETE FROM " + MESSAGE_META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String DELETE_FROM_MESSAGE_CONTENT = "DELETE FROM " + MESSAGE_CONTENT_TABLE_NAME + " WHERE message_id = ?";
    private static final String INSERT_INTO_EXCHANGE = "INSERT INTO " + EXCHANGE_TABLE_NAME + " ( name, type, autodelete ) VALUES ( ?, ?, ? )";
    private static final String DELETE_FROM_EXCHANGE = "DELETE FROM " + EXCHANGE_TABLE_NAME + " WHERE name = ?";
    private static final String INSERT_INTO_BINDINGS = "INSERT INTO " + BINDINGS_TABLE_NAME + " ( exchange_name, queue_name, binding_key, arguments ) values ( ?, ?, ?, ? )";
    private static final String DELETE_FROM_BINDINGS = "DELETE FROM " + BINDINGS_TABLE_NAME + " WHERE exchange_name = ? AND queue_name = ? AND binding_key = ?";
    private static final String INSERT_INTO_QUEUE = "INSERT INTO " + QUEUE_TABLE_NAME + " (name, owner) VALUES (?, ?)";
    private static final String DELETE_FROM_QUEUE = "DELETE FROM " + QUEUE_TABLE_NAME + " WHERE name = ?";
    private static final String INSERT_INTO_QUEUE_ENTRY = "INSERT INTO " + QUEUE_ENTRY_TABLE_NAME + " (queue_name, message_id) values (?,?)";
    private static final String DELETE_FROM_QUEUE_ENTRY = "DELETE FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_name = ? AND message_id =?";
    private static final String INSERT_INTO_MESSAGE_CONTENT = "INSERT INTO " + MESSAGE_CONTENT_TABLE_NAME + "( message_id, chunk_id, content_chunk ) values (?, ?, ?)";
    private static final String INSERT_INTO_MESSAGE_META_DATA = "INSERT INTO " + MESSAGE_META_DATA_TABLE_NAME + "( message_id , exchange_name , routing_key , flag_mandatory , flag_immediate , content_header , chunk_count ) values (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_FROM_MESSAGE_META_DATA =
            "SELECT exchange_name , routing_key , flag_mandatory , flag_immediate , content_header , chunk_count FROM " + MESSAGE_META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String SELECT_FROM_MESSAGE_CONTENT =
            "SELECT content_chunk FROM " + MESSAGE_CONTENT_TABLE_NAME + " WHERE message_id = ? and chunk_id = ?";
    private static final String SELECT_FROM_QUEUE_ENTRY = "SELECT queue_name, message_id FROM " + QUEUE_ENTRY_TABLE_NAME;
    private static final String TABLE_EXISTANCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";


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


    public void configure(VirtualHost virtualHost, String base, Configuration config) throws Exception
    {
        stateTransition(State.INITIAL, State.CONFIGURING);

        initialiseDriver();

        _virtualHost = virtualHost;

        _logger.info("Configuring Derby message store for virtual host " + virtualHost.getName());
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        final String databasePath = config.getString(base + "." + ENVIRONMENT_PATH_PROPERTY, "derbyDB");

        File environmentPath = new File(databasePath);
        if (!environmentPath.exists())
        {
            if (!environmentPath.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                    + "Ensure the path is correct and that the permissions are correct.");
            }
        }

        createOrOpenDatabase(databasePath);

        // this recovers durable queues and persistent messages

        recover();

        stateTransition(State.RECOVERING, State.STARTED);

    }

    private static synchronized void initialiseDriver() throws ClassNotFoundException
    {
        if(DRIVER_CLASS == null)
        {
            DRIVER_CLASS = (Class<Driver>) Class.forName(SQL_DRIVER_NAME);
        }
    }

    private void createOrOpenDatabase(final String environmentPath) throws SQLException
    {
        _connectionURL = "jdbc:derby:" + environmentPath + "/" + _virtualHost.getName() + ";create=true";

        Connection conn = newConnection();

        createVersionTable(conn);
        createExchangeTable(conn);
        createQueueTable(conn);
        createBindingsTable(conn);
        createQueueEntryTable(conn);
        createMessageMetaDataTable(conn);
        createMessageContentTable(conn);

        conn.close();
    }



    private void createVersionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(DB_VERSION_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();

            stmt.execute(CREATE_DB_VERSION_TABLE);
            stmt.close();

            PreparedStatement pstmt = conn.prepareStatement(INSERT_INTO_DB_VERSION);
            pstmt.setInt(1, DB_VERSION);
            pstmt.execute();
            pstmt.close();
        }

    }


    private void createExchangeTable(final Connection conn) throws SQLException
    {
        if(!tableExists(EXCHANGE_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();

            stmt.execute(CREATE_EXCHANGE_TABLE);
            stmt.close();
        }
    }

    private void createQueueTable(final Connection conn) throws SQLException
    {
        if(!tableExists(QUEUE_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            stmt.execute(CREATE_QUEUE_TABLE);
            stmt.close();
        }
    }

    private void createBindingsTable(final Connection conn) throws SQLException
    {
        if(!tableExists(BINDINGS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            stmt.execute(CREATE_BINDINGS_TABLE);

            stmt.close();
        }

    }

    private void createQueueEntryTable(final Connection conn) throws SQLException
    {
        if(!tableExists(QUEUE_ENTRY_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            stmt.execute(CREATE_QUEUE_ENTRY_TABLE);

            stmt.close();
        }

    }

    private void createMessageMetaDataTable(final Connection conn) throws SQLException
    {
        if(!tableExists(MESSAGE_META_DATA_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            stmt.execute(CREATE_MESSAGE_META_DATA_TABLE);

            stmt.close();
        }

    }


    private void createMessageContentTable(final Connection conn) throws SQLException
    {
        if(!tableExists(MESSAGE_CONTENT_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            stmt.execute(CREATE_MESSAGE_CONTENT_TABLE);

            stmt.close();
        }

    }



    private boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(TABLE_EXISTANCE_QUERY);
        stmt.setString(1, tableName);
        ResultSet rs = stmt.executeQuery();
        boolean exists = rs.next();
        rs.close();
        stmt.close();
        return exists;
    }

    public void recover() throws AMQException
    {
        stateTransition(State.CONFIGURING, State.RECOVERING);

        _logger.info("Recovering persistent state...");
        StoreContext context = new StoreContext();

        try
        {
            Map<AMQShortString, AMQQueue> queues = loadQueues();

            recoverExchanges();

            try
            {

                beginTran(context);

                deliverMessages(context, queues);
                _logger.info("Persistent state recovered successfully");
                commitTran(context);

            }
            finally
            {
                if(inTran(context))
                {
                    abortTran(context);
                }
            }
        }
        catch (SQLException e)
        {

            throw new AMQException("Error recovering persistent state: " + e, e);
        }

    }

    private Map<AMQShortString, AMQQueue> loadQueues() throws SQLException, AMQException
    {
        Connection conn = newConnection();


        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(SELECT_FROM_QUEUE);
        Map<AMQShortString, AMQQueue> queueMap = new HashMap<AMQShortString, AMQQueue>();
        while(rs.next())
        {
            String queueName = rs.getString(1);
            String owner = rs.getString(2);
            AMQShortString queueNameShortString = new AMQShortString(queueName);
            AMQQueue q =  AMQQueueFactory.createAMQQueueImpl(queueNameShortString, true, owner == null ? null : new AMQShortString(owner), false, _virtualHost,
                                                             null);
            _virtualHost.getQueueRegistry().registerQueue(q);
            queueMap.put(queueNameShortString,q);

        }
        return queueMap;
    }

    private void recoverExchanges() throws AMQException, SQLException
    {
        for (Exchange exchange : loadExchanges())
        {
            recoverExchange(exchange);
        }
    }


    private List<Exchange> loadExchanges() throws AMQException, SQLException
    {

        List<Exchange> exchanges = new ArrayList<Exchange>();
        Connection conn = null;
        try
        {
            conn = newConnection();


            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(SELECT_FROM_EXCHANGE);

            Exchange exchange;
            while(rs.next())
            {
                String exchangeName = rs.getString(1);
                String type = rs.getString(2);
                boolean autoDelete = rs.getShort(3) != 0;

                exchange = _virtualHost.getExchangeFactory().createExchange(new AMQShortString(exchangeName), new AMQShortString(type), true, autoDelete, 0);
                _virtualHost.getExchangeRegistry().registerExchange(exchange);
                exchanges.add(exchange);

            }
            return exchanges;

        }
        finally
        {
            if(conn != null)
            {
                conn.close();
            }
        }

    }

    private void recoverExchange(Exchange exchange) throws AMQException, SQLException
    {
        _logger.info("Recovering durable exchange " + exchange.getName() + " of type " + exchange.getType() + "...");

        QueueRegistry queueRegistry = _virtualHost.getQueueRegistry();

        Connection conn = null;
        try
        {
            conn = newConnection();

            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_BINDINGS);
            stmt.setString(1, exchange.getName().toString());

            ResultSet rs = stmt.executeQuery();


            while(rs.next())
            {
                String queueName = rs.getString(1);
                String bindingKey = rs.getString(2);
                Blob arguments = rs.getBlob(3);


                AMQQueue queue = queueRegistry.getQueue(new AMQShortString(queueName));
                if (queue == null)
                {
                    _logger.error("Unkown queue: " + queueName + " cannot be bound to exchange: "
                        + exchange.getName());
                }
                else
                {
                    _logger.info("Restoring binding: (Exchange: " + exchange.getName() + ", Queue: " + queueName
                        + ", Routing Key: " + bindingKey + ", Arguments: " + arguments
                        + ")");

                    FieldTable argumentsFT = null;
                    if(arguments != null)
                    {
                        byte[] argumentBytes = arguments.getBytes(0, (int) arguments.length());
                        ByteBuffer buf = ByteBuffer.wrap(argumentBytes);
                        argumentsFT = new FieldTable(buf,arguments.length());
                    }

                    queue.bind(exchange, bindingKey == null ? null : new AMQShortString(bindingKey), argumentsFT);

                }
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
        _closed.getAndSet(true);
    }

    public void removeMessage(StoreContext storeContext, Long messageId) throws AMQException
    {

        boolean localTx = getOrCreateTransaction(storeContext);

        Connection conn = getConnection(storeContext);
        ConnectionWrapper wrapper = (ConnectionWrapper) storeContext.getPayload();


        if (_logger.isDebugEnabled())
        {
            _logger.debug("Message Id: " + messageId + " Removing");
        }

        // first we need to look up the header to get the chunk count
        MessageMetaData mmd = getMessageMetaData(storeContext, messageId);
        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_MESSAGE_META_DATA);
            stmt.setLong(1,messageId);
            wrapper.setRequiresCommit();
            int results = stmt.executeUpdate();

            if (results == 0)
            {
                if (localTx)
                {
                    abortTran(storeContext);
                }

                throw new AMQException("Message metadata not found for message id " + messageId);
            }
            stmt.close();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Deleted metadata for message " + messageId);
            }

            stmt = conn.prepareStatement(DELETE_FROM_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);
            results = stmt.executeUpdate();

            if(results != mmd.getContentChunkCount())
            {
                if (localTx)
                {
                    abortTran(storeContext);
                }
                throw new AMQException("Unexpected number of content chunks when deleting message.  Expected " + mmd.getContentChunkCount() + " but found " + results);

            }

            if (localTx)
            {
                commitTran(storeContext);
            }
        }
        catch (SQLException e)
        {
            if ((conn != null) && localTx)
            {
                abortTran(storeContext);
            }

            throw new AMQException("Error writing AMQMessage with id " + messageId + " to database: " + e, e);
        }

    }

    public void createExchange(Exchange exchange) throws AMQException
    {
        if (_state != State.RECOVERING)
        {
            try
            {
                Connection conn = null;

                try
                {
                    conn = newConnection();

                    PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_EXCHANGE);
                    stmt.setString(1, exchange.getName().toString());
                    stmt.setString(2, exchange.getType().toString());
                    stmt.setShort(3, exchange.isAutoDelete() ? (short) 1 : (short) 0);
                    stmt.execute();
                    stmt.close();
                    conn.commit();

                }
                finally
                {
                    if(conn != null)
                    {
                        conn.close();
                    }
                }
            }
            catch (SQLException e)
            {
                throw new AMQException("Error writing Exchange with name " + exchange.getName() + " to database: " + e, e);
            }
        }

    }

    public void removeExchange(Exchange exchange) throws AMQException
    {
        Connection conn = null;

        try
        {
            conn = newConnection();
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_EXCHANGE);
            stmt.setString(1, exchange.getName().toString());
            int results = stmt.executeUpdate();
            if(results == 0)
            {
                throw new AMQException("Exchange " + exchange.getName() + " not found");
            }
            else
            {
                conn.commit();
                stmt.close();
            }
        }
        catch (SQLException e)
        {
            throw new AMQException("Error writing deleting with name " + exchange.getName() + " from database: " + e, e);
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

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args)
            throws AMQException
    {
        if (_state != State.RECOVERING)
        {
            Connection conn = null;


            try
            {
                conn = newConnection();
                PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_BINDINGS);
                stmt.setString(1, exchange.getName().toString() );
                stmt.setString(2, queue.getName().toString());
                stmt.setString(3, routingKey == null ? null : routingKey.toString());
                if(args != null)
                {
                    /* This would be the Java 6 way of setting a Blob
                    Blob blobArgs = conn.createBlob();
                    blobArgs.setBytes(0, args.getDataAsBytes());
                    stmt.setBlob(4, blobArgs);
                    */
                    byte[] bytes = args.getDataAsBytes();
                    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                    stmt.setBinaryStream(4, bis, bytes.length);
                }
                else
                {
                    stmt.setNull(4, Types.BLOB);
                }

                stmt.executeUpdate();
                conn.commit();
                stmt.close();
            }
            catch (SQLException e)
            {
                throw new AMQException("Error writing binding for AMQQueue with name " + queue.getName() + " to exchange "
                    + exchange.getName() + " to database: " + e, e);
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


    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args)
            throws AMQException
    {
        Connection conn = null;


        try
        {
            conn = newConnection();
            // exchange_name varchar(255) not null, queue_name varchar(255) not null, binding_key varchar(255), arguments blob
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_BINDINGS);
            stmt.setString(1, exchange.getName().toString() );
            stmt.setString(2, queue.getName().toString());
            stmt.setString(3, routingKey == null ? null : routingKey.toString());


            if(stmt.executeUpdate() != 1)
            {
                 throw new AMQException("Queue binding for queue with name " + queue.getName() + " to exchange "
                + exchange.getName() + "  not found");
            }
            conn.commit();
            stmt.close();
        }
        catch (SQLException e)
        {
            throw new AMQException("Error removing binding for AMQQueue with name " + queue.getName() + " to exchange "
                + exchange.getName() + " in database: " + e, e);
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

    public void createQueue(AMQQueue queue) throws AMQException
    {
        createQueue(queue, null);
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
    {
        _logger.debug("public void createQueue(AMQQueue queue = " + queue + "): called");

        if (_state != State.RECOVERING)
        {
            try
            {
                Connection conn = newConnection();

                PreparedStatement stmt =
                        conn.prepareStatement(INSERT_INTO_QUEUE);

                stmt.setString(1, queue.getName().toString());
                stmt.setString(2, queue.getOwner() == null ? null : queue.getOwner().toString());

                stmt.execute();

                stmt.close();

                conn.commit();

                conn.close();
            }
            catch (SQLException e)
            {
                throw new AMQException("Error writing AMQQueue with name " + queue.getName() + " to database: " + e, e);
            }
        }
    }

    private Connection newConnection() throws SQLException
    {
        final Connection connection = DriverManager.getConnection(_connectionURL);
        return connection;
    }

    public void removeQueue(final AMQQueue queue) throws AMQException
    {
        AMQShortString name = queue.getName();
        _logger.debug("public void removeQueue(AMQShortString name = " + name + "): called");
        Connection conn = null;


        try
        {
            conn = newConnection();
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_QUEUE);
            stmt.setString(1, name.toString());
            int results = stmt.executeUpdate();


            if (results == 0)
            {
                throw new AMQException("Queue " + name + " not found");
            }

            conn.commit();
            stmt.close();
        }
        catch (SQLException e)
        {
            throw new AMQException("Error writing deleting with name " + name + " from database: " + e, e);
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

    public void enqueueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        AMQShortString name = queue.getName();

        boolean localTx = getOrCreateTransaction(context);
        Connection conn = getConnection(context);
        ConnectionWrapper connWrapper = (ConnectionWrapper) context.getPayload();

        try
        {
            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_QUEUE_ENTRY);
            stmt.setString(1,name.toString());
            stmt.setLong(2,messageId);
            stmt.executeUpdate();
            connWrapper.requiresCommit();

            if(localTx)
            {
                commitTran(context);
            }



            if (_logger.isDebugEnabled())
            {
                _logger.debug("Enqueuing message " + messageId + " on queue " + name + "[Connection" + conn + "]");
            }
        }
        catch (SQLException e)
        {
            if(localTx)
            {
                abortTran(context);
            }
            _logger.error("Failed to enqueue: " + e, e);
            throw new AMQException("Error writing enqueued message with id " + messageId + " for queue " + name
                + " to database", e);
        }

    }

    public void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        AMQShortString name = queue.getName();

        boolean localTx = getOrCreateTransaction(context);
        Connection conn = getConnection(context);
        ConnectionWrapper connWrapper = (ConnectionWrapper) context.getPayload();

        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_QUEUE_ENTRY);
            stmt.setString(1,name.toString());
            stmt.setLong(2,messageId);
            int results = stmt.executeUpdate();

            connWrapper.requiresCommit();

            if(results != 1)
            {
                throw new AMQException("Unable to find message with id " + messageId + " on queue " + name);
            }

            if(localTx)
            {
                commitTran(context);
            }



            if (_logger.isDebugEnabled())
            {
                _logger.debug("Dequeuing message " + messageId + " on queue " + name + "[Connection" + conn + "]");
            }
        }
        catch (SQLException e)
        {
            if(localTx)
            {
                abortTran(context);
            }
            _logger.error("Failed to dequeue: " + e, e);
            throw new AMQException("Error deleting enqueued message with id " + messageId + " for queue " + name
                + " from database", e);
        }

    }

    private static final class ConnectionWrapper
    {
        private final Connection _connection;
        private boolean _requiresCommit;

        public ConnectionWrapper(Connection conn)
        {
            _connection = conn;
        }

        public void setRequiresCommit()
        {
            _requiresCommit = true;
        }

        public boolean requiresCommit()
        {
            return _requiresCommit;
        }

        public Connection getConnection()
        {
            return _connection;
        }
    }

    public void beginTran(StoreContext context) throws AMQException
    {
        if (context.getPayload() != null)
        {
            throw new AMQException("Fatal internal error: transactional context is not empty at beginTran: "
                + context.getPayload());
        }
        else
        {
            try
            {
                Connection conn = newConnection();


                context.setPayload(new ConnectionWrapper(conn));
            }
            catch (SQLException e)
            {
                throw new AMQException("Error starting transaction: " + e, e);
            }
        }
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        ConnectionWrapper connWrapper = (ConnectionWrapper) context.getPayload();

        if (connWrapper == null)
        {
            throw new AMQException("Fatal internal error: transactional context is empty at commitTran");
        }

        try
        {
            Connection conn = connWrapper.getConnection();
            if(connWrapper.requiresCommit())
            {
                conn.commit();

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("commit tran completed");
                }

            }
            conn.close();
        }
        catch (SQLException e)
        {
            throw new AMQException("Error commit tx: " + e, e);
        }
        finally
        {
            context.setPayload(null);
        }
    }

    public void abortTran(StoreContext context) throws AMQException
    {
        ConnectionWrapper connWrapper = (ConnectionWrapper) context.getPayload();

        if (connWrapper == null)
        {
            throw new AMQException("Fatal internal error: transactional context is empty at abortTran");
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("abort tran called: " + connWrapper.getConnection());
        }

        try
        {
            Connection conn = connWrapper.getConnection();
            if(connWrapper.requiresCommit())
            {
                conn.rollback();
            }

            conn.close();
        }
        catch (SQLException e)
        {
            throw new AMQException("Error aborting transaction: " + e, e);
        }
        finally
        {
            context.setPayload(null);
        }
    }

    public boolean inTran(StoreContext context)
    {
        return context.getPayload() != null;
    }

    public Long getNewMessageId()
    {
        return _messageId.getAndIncrement();
    }

    public void storeContentBodyChunk(StoreContext context,
                                      Long messageId,
                                      int index,
                                      ContentChunk contentBody,
                                      boolean lastContentBody) throws AMQException
    {
        boolean localTx = getOrCreateTransaction(context);
        Connection conn = getConnection(context);
        ConnectionWrapper connWrapper = (ConnectionWrapper) context.getPayload();

        try
        {
            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);
            stmt.setInt(2, index);
            byte[] chunkData = new byte[contentBody.getSize()];
            contentBody.getData().duplicate().get(chunkData);
            /* this would be the Java 6 way of doing things
            Blob dataAsBlob = conn.createBlob();
            dataAsBlob.setBytes(1L, chunkData);
            stmt.setBlob(3, dataAsBlob);
            */
            ByteArrayInputStream bis = new ByteArrayInputStream(chunkData);
            stmt.setBinaryStream(3, bis, chunkData.length);
            stmt.executeUpdate();
            connWrapper.requiresCommit();

            if(localTx)
            {
                commitTran(context);
            }
        }
        catch (SQLException e)
        {
            if(localTx)
            {
                abortTran(context);
            }

            throw new AMQException("Error writing AMQMessage with id " + messageId + " to database: " + e, e);
        }

    }

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData mmd)
            throws AMQException
    {

        boolean localTx = getOrCreateTransaction(context);
        Connection conn = getConnection(context);
        ConnectionWrapper connWrapper = (ConnectionWrapper) context.getPayload();

        try
        {

            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_MESSAGE_META_DATA);
            stmt.setLong(1,messageId);
            stmt.setString(2, mmd.getMessagePublishInfo().getExchange().toString());
            stmt.setString(3, mmd.getMessagePublishInfo().getRoutingKey().toString());
            stmt.setShort(4, mmd.getMessagePublishInfo().isMandatory() ? (short) 1 : (short) 0);
            stmt.setShort(5, mmd.getMessagePublishInfo().isImmediate() ? (short) 1 : (short) 0);

            ContentHeaderBody headerBody = mmd.getContentHeaderBody();
            final int bodySize = headerBody.getSize();
            byte[] underlying = new byte[bodySize];
            ByteBuffer buf = ByteBuffer.wrap(underlying);
            headerBody.writePayload(buf);
/*
            Blob dataAsBlob = conn.createBlob();
            dataAsBlob.setBytes(1L, underlying);
            stmt.setBlob(6, dataAsBlob);
*/
            ByteArrayInputStream bis = new ByteArrayInputStream(underlying);
            stmt.setBinaryStream(6,bis,underlying.length);

            stmt.setInt(7, mmd.getContentChunkCount());

            stmt.executeUpdate();
            connWrapper.requiresCommit();

            if(localTx)
            {
                commitTran(context);
            }
        }
        catch (SQLException e)
        {
            if(localTx)
            {
                abortTran(context);
            }

            throw new AMQException("Error writing AMQMessage with id " + messageId + " to database: " + e, e);
        }


    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException
    {
        boolean localTx = getOrCreateTransaction(context);
        Connection conn = getConnection(context);


        try
        {

            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_MESSAGE_META_DATA);
            stmt.setLong(1,messageId);
            ResultSet rs = stmt.executeQuery();

            if(rs.next())
            {
                final AMQShortString exchange = new AMQShortString(rs.getString(1));
                final AMQShortString routingKey = rs.getString(2) == null ? null : new AMQShortString(rs.getString(2));
                final boolean mandatory = (rs.getShort(3) != (short)0);
                final boolean immediate = (rs.getShort(4) != (short)0);
                MessagePublishInfo info = new MessagePublishInfoImpl(exchange,immediate,mandatory,routingKey);

                Blob dataAsBlob = rs.getBlob(5);

                byte[] dataAsBytes = dataAsBlob.getBytes(1,(int) dataAsBlob.length());
                ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);

                ContentHeaderBody chb = ContentHeaderBody.createFromBuffer(buf, dataAsBytes.length);

                if(localTx)
                {
                    commitTran(context);
                }

                return new MessageMetaData(info, chb, rs.getInt(6));

            }
            else
            {
                if(localTx)
                {
                    abortTran(context);
                }
                throw new AMQException("Metadata not found for message with id " + messageId);
            }
        }
        catch (SQLException e)
        {
            if(localTx)
            {
                abortTran(context);
            }

            throw new AMQException("Error reading AMQMessage with id " + messageId + " from database: " + e, e);
        }


    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException
    {
        boolean localTx = getOrCreateTransaction(context);
                Connection conn = getConnection(context);


                try
                {

                    PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_MESSAGE_CONTENT);
                    stmt.setLong(1,messageId);
                    stmt.setInt(2, index);
                    ResultSet rs = stmt.executeQuery();

                    if(rs.next())
                    {
                        Blob dataAsBlob = rs.getBlob(1);

                        final int size = (int) dataAsBlob.length();
                        byte[] dataAsBytes = dataAsBlob.getBytes(1, size);
                        final ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);

                        ContentChunk cb = new ContentChunk()
                        {

                            public int getSize()
                            {
                                return size;
                            }

                            public ByteBuffer getData()
                            {
                                return buf;
                            }

                            public void reduceToFit()
                            {

                            }
                        };

                        if(localTx)
                        {
                            commitTran(context);
                        }

                        return cb;

                    }
                    else
                    {
                        if(localTx)
                        {
                            abortTran(context);
                        }
                        throw new AMQException("Message not found for message with id " + messageId);
                    }
                }
                catch (SQLException e)
                {
                    if(localTx)
                    {
                        abortTran(context);
                    }

                    throw new AMQException("Error reading AMQMessage with id " + messageId + " from database: " + e, e);
                }



    }

    public boolean isPersistent()
    {
        return true;
    }

    private void checkNotClosed() throws MessageStoreClosedException
    {
        if (_closed.get())
        {
            throw new MessageStoreClosedException();
        }
    }


    private static final class ProcessAction
    {
        private final AMQQueue _queue;
        private final StoreContext _context;
        private final AMQMessage _message;

        public ProcessAction(AMQQueue queue, StoreContext context, AMQMessage message)
        {
            _queue = queue;
            _context = context;
            _message = message;
        }

        public void process() throws AMQException
        {
            _queue.enqueue(_context, _message);

        }

    }


    private void deliverMessages(final StoreContext context, Map<AMQShortString, AMQQueue> queues)
        throws SQLException, AMQException
    {
        Map<Long, AMQMessage> msgMap = new HashMap<Long,AMQMessage>();
        List<ProcessAction> actions = new ArrayList<ProcessAction>();

        Map<AMQShortString, Integer> queueRecoveries = new TreeMap<AMQShortString, Integer>();

        final boolean inLocaltran = inTran(context);
        Connection conn = null;
        try
        {

            if(inLocaltran)
            {
                conn = getConnection(context);
            }
            else
            {
                conn = newConnection();
            }


            MessageHandleFactory messageHandleFactory = new MessageHandleFactory();
            long maxId = 1;

            TransactionalContext txnContext = new NonTransactionalContext(this, new StoreContext(), null, null);

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(SELECT_FROM_QUEUE_ENTRY);


            while (rs.next())
            {



                AMQShortString queueName = new AMQShortString(rs.getString(1));


                AMQQueue queue = queues.get(queueName);
                if (queue == null)
                {
                    queue = AMQQueueFactory.createAMQQueueImpl(queueName, false, null, false, _virtualHost, null);

                    _virtualHost.getQueueRegistry().registerQueue(queue);
                    queues.put(queueName, queue);
                }

                long messageId = rs.getLong(2);
                maxId = Math.max(maxId, messageId);
                AMQMessage message = msgMap.get(messageId);

                if(message != null)
                {
                    message.incrementReference();
                }
                else
                {
                    message = new AMQMessage(messageId, this, messageHandleFactory, txnContext);
                    msgMap.put(messageId,message);
                }

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("On recovery, delivering " + message.getMessageId() + " to " + queue.getName());
                }

                if (_logger.isInfoEnabled())
                {
                    Integer count = queueRecoveries.get(queueName);
                    if (count == null)
                    {
                        count = 0;
                    }

                    queueRecoveries.put(queueName, ++count);

                }

                actions.add(new ProcessAction(queue, context, message));

            }

            for(ProcessAction action : actions)
            {
                action.process();
            }

            _messageId.set(maxId + 1);
        }
        catch (SQLException e)
        {
            _logger.error("Error: " + e, e);
            throw e;
        }
        finally
        {
            if (inLocaltran && conn != null)
            {
                conn.close();
            }
        }

        if (_logger.isInfoEnabled())
        {
            _logger.info("Recovered message counts: " + queueRecoveries);
        }
    }

    private Connection getConnection(final StoreContext context)
    {
        return ((ConnectionWrapper)context.getPayload()).getConnection();
    }

    private boolean getOrCreateTransaction(StoreContext context) throws AMQException
    {

        ConnectionWrapper tx = (ConnectionWrapper) context.getPayload();
        if (tx == null)
        {
            beginTran(context);
            return true;
        }

        return false;
    }

    private synchronized void stateTransition(State requiredState, State newState) throws AMQException
    {
        if (_state != requiredState)
        {
            throw new AMQException("Cannot transition to the state: " + newState + "; need to be in state: " + requiredState
                + "; currently in state: " + _state);
        }

        _state = newState;
    }
}
