/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.server.messageStore;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.exception.InternalErrorException;
import org.apache.qpid.server.exception.InvalidXidException;
import org.apache.qpid.server.exception.MessageAlreadyStagedException;
import org.apache.qpid.server.exception.MessageDoesntExistException;
import org.apache.qpid.server.exception.QueueAlreadyExistsException;
import org.apache.qpid.server.exception.QueueDoesntExistException;
import org.apache.qpid.server.exception.UnknownXidException;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.JDBCAbstractRecord;
import org.apache.qpid.server.txn.JDBCDequeueRecord;
import org.apache.qpid.server.txn.JDBCEnqueueRecord;
import org.apache.qpid.server.txn.JDBCTransaction;
import org.apache.qpid.server.txn.JDBCTransactionManager;
import org.apache.qpid.server.txn.Transaction;
import org.apache.qpid.server.txn.TransactionManager;
import org.apache.qpid.server.txn.TransactionRecord;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.txn.XidImpl;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.transaction.xa.Xid;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Arnaud Simon
 * Date: 15-May-2007
 * Time: 09:59:12
 */
public class JDBCStore implements MessageStore
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(JDBCStore.class);
    // the database connection pool
    public static ConnectionPool _connectionPool = null;
    // the prepared statements
    //==== IMPORTANT: remember to update if we add more prepared statements!
    private static final int CREATE_EXCHANGE = 0;
    private static final int DELETE_EXCHANGE = 1;
    private static final int BIND_QUEUE = 2;
    private static final int UNBIND_QUEUE = 3;
    private static final int CREATE_QUEUE = 4;
    private static final int DELETE_QUEUE = 5;
    private static final int STAGE_MESSAGE = 6;
    private static final int UPDATE_MESSAGE_PAYLOAD = 7;
    private static final int SELECT_MESSAGE_PAYLOAD = 8;
    private static final int DELETE_MESSAGE = 9;
    private static final int ENQUEUE = 10;
    private static final int DEQUEUE = 11;
    private static final int GET_ALL_QUEUES = 12;
    private static final int GET_ALL_MESSAGES = 13;
    private static final int SAVE_RECORD = 14;
    private static final int SAVE_XID = 15;
    private static final int DELETE_RECORD = 16;
    private static final int DELETE_XID = 17;
    private static final int UPDATE_QMR = 18;
    private static final int GET_CONTENT_HEADER = 19;
    private static final int GET_MESSAGE_INFO = 20;
    //==== size:
    private static final int STATEMENT_SIZE = 21;
    //========================================================================
    // field properties
    //========================================================================
    //The default URL
    protected String _connectionURL = "jdbc:derby:derbyDB;create=true";
    // The default driver
    private String _driver = "org.apache.derby.jdbc.EmbeddedDriver";
    // The pool max size
    private int _maxSize = 40;
    // The tables
    // the table containing the messages
    private String _tableNameMessage = "MessageTable";
    private String _tableNameQueue = "QueueTable";
    private String _tableNameQueueMessageRelation = "QeueMessageRelation";
    private String _tableNameExchange = "Exchange";
    private String _tableNameExchangeQueueRelation = "ExchangeQueueRelation";
    private String _tableNameTransaction = "TransactionTable";
    private String _tableNameRecord = "RecordTable";

    // The transaction maanger
    private JDBCTransactionManager _tm;
    // the message ID
    private long _messageID = 0;
    // the virtual host
    private VirtualHost _virtualHost;
    // indicate whether this store is recovering
    private boolean _recovering = false;
    // the recovered queues
    private HashMap<Integer, AMQQueue> _queueMap;

    //========================================================================
    // Interface MessageStore
    //========================================================================
    public void configure(VirtualHost virtualHost, TransactionManager tm, String base, Configuration config)
            throws
            InternalErrorException,
            IllegalArgumentException
    {
        _log.info("Configuring Derby message store");
        // the virtual host
        _virtualHost = virtualHost;
        // Specify that the tables must be dropped.
        // If true then this means that recovery is not possible.
        boolean dropTables = true;
        if (config != null)
        {
            dropTables = config.getBoolean(base + "dropTables", false);
            _driver = config.getString(base + "driver", _driver);
            _connectionURL = config.getString(base + "connectionURL", _connectionURL);
            _maxSize = config.getInt(base + "connectionPoolSize", 20);
        }
        if (dropTables)
        {
            _log.info("Dropping table of Derby message store");
        }
        if (!setupStore(dropTables))
        {
            _log.error("Error configuration of Derby store failed");
            throw new InternalErrorException("Error configuration of Derby store failed");
        }
        // recovery
        _recovering = true;
        _queueMap = recover(); //==> recover the queues and the messages
        // recreate the excahnges and bind the queues
        recoverExchanges(_queueMap);
        _recovering = false;
        _tm = (JDBCTransactionManager) tm;
        _tm.configure(this, "txn", config);
        _queueMap.clear();
        _queueMap = null;
    }

    public void close()
            throws
            InternalErrorException
    {
        // nothing has to be done
    }

    public void createExchange(Exchange exchange)
            throws
            InternalErrorException
    {
        if (!_recovering)
        {
            MyConnection connection = null;
            try
            {
                connection = (MyConnection) _connectionPool.acquireInstance();
                PreparedStatement pstmt = connection.getStatements()[CREATE_EXCHANGE];
                if (pstmt == null)
                {
                    pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameExchange +
                                                                        " (Name,Type) VALUES (?,?)");
                    connection.getStatements()[CREATE_EXCHANGE] = pstmt;
                }
                pstmt.setString(1, exchange.getName().asString());
                pstmt.setString(2, exchange.getType().asString());
                pstmt.executeUpdate();
            }
            catch (Exception e)
            {
                throw new InternalErrorException("Cannot create Exchange: " + exchange, e);
            }
            finally
            {
                if (connection != null)
                {
                    try
                    {
                        connection.getConnection().commit();
                        _connectionPool.releaseInstance(connection);
                    }
                    catch (SQLException e)
                    {
                        // we did not manage to commit this connection
                        // it is better to release it
                        _connectionPool.releaseDeadInstance();
                        throw new InternalErrorException("Cannot create Exchange: " + exchange, e);
                    }
                }
            }
        }
    }

    public void removeExchange(Exchange exchange)
            throws
            InternalErrorException
    {
        if (!_recovering)
        {
            MyConnection connection = null;
            try
            {
                connection = (MyConnection) _connectionPool.acquireInstance();
                PreparedStatement pstmt = connection.getStatements()[DELETE_EXCHANGE];
                if (pstmt == null)
                {
                    pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameExchange +
                                                                        " WHERE Name = ?");
                    connection.getStatements()[DELETE_EXCHANGE] = pstmt;
                }
                pstmt.setString(1, exchange.getName().asString());
                pstmt.executeUpdate();
            }
            catch (Exception e)
            {
                throw new InternalErrorException("Cannot remove Exchange: " + exchange, e);
            }
            finally
            {
                if (connection != null)
                {
                    try
                    {
                        connection.getConnection().commit();
                        _connectionPool.releaseInstance(connection);
                    }
                    catch (SQLException e)
                    {
                        // we did not manage to commit this connection
                        // it is better to release it
                        _connectionPool.releaseDeadInstance();
                        throw new InternalErrorException("Cannot remove Exchange: " + exchange, e);
                    }
                }
            }
        }
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, StorableQueue queue, FieldTable args)
            throws
            InternalErrorException
    {
        if (!_recovering)
        {
            MyConnection connection = null;
            try
            {
                connection = (MyConnection) _connectionPool.acquireInstance();
                PreparedStatement pstmt = connection.getStatements()[BIND_QUEUE];
                if (pstmt == null)
                {
                    pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameExchangeQueueRelation +
                                                                        " (QueueID,Name,RoutingKey,fieldTable) VALUES (?,?,?,?)");
                    connection.getStatements()[BIND_QUEUE] = pstmt;
                }
                pstmt.setInt(1, queue.getQueueID());
                pstmt.setString(2, exchange.getName().asString());
                pstmt.setString(3, routingKey.asString());
                if (args != null)
                {
                    pstmt.setBytes(4, args.getDataAsBytes());
                }
                else
                {
                    pstmt.setBytes(4, null);
                }
                pstmt.executeUpdate();
            }
            catch (Exception e)
            {
                throw new InternalErrorException("Cannot create Exchange: " + exchange, e);
            }
            finally
            {
                if (connection != null)
                {
                    try
                    {
                        connection.getConnection().commit();
                        _connectionPool.releaseInstance(connection);
                    }
                    catch (SQLException e)
                    {
                        // we did not manage to commit this connection
                        // it is better to release it
                        _connectionPool.releaseDeadInstance();
                        throw new InternalErrorException("Cannot create Exchange: " + exchange, e);
                    }
                }
            }
        }
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, StorableQueue queue, FieldTable args)
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[UNBIND_QUEUE];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameExchangeQueueRelation +
                                                                    " WHERE QueueID = ? AND NAME = ? AND RoutingKey = ?");
                connection.getStatements()[UNBIND_QUEUE] = pstmt;
            }
            pstmt.setInt(1, queue.getQueueID());
            pstmt.setString(2, exchange.getName().asString());
            pstmt.setString(3, routingKey.asString());
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot remove Exchange: " + exchange, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot remove Exchange: " + exchange, e);
                }
            }
        }
    }

    public void createQueue(StorableQueue queue)
            throws
            InternalErrorException,
            QueueAlreadyExistsException
    {
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[CREATE_QUEUE];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameQueue +
                                                                    " (QueueID,Name,Owner) VALUES (?,?,?)");
                connection.getStatements()[CREATE_QUEUE] = pstmt;
            }
            pstmt.setInt(1, queue.getQueueID());
            pstmt.setString(2, queue.getName().asString());
            if (queue.getOwner() != null)
            {
                pstmt.setString(3, queue.getOwner().asString());
            }
            else
            {
                pstmt.setString(3, null);
            }
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot create Queue: " + queue, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot create Queue: " + queue, e);
                }
            }
        }
    }

    public void destroyQueue(StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException
    {
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[DELETE_QUEUE];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameQueue +
                                                                    " WHERE QueueID = ?");
                connection.getStatements()[DELETE_QUEUE] = pstmt;
            }
            pstmt.setInt(1, queue.getQueueID());
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot remove Queue: " + queue, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot remove Queue: " + queue, e);
                }
            }
        }
    }

    public void stage(StorableMessage m)
            throws
            InternalErrorException,
            MessageAlreadyStagedException
    {
        if (m.isStaged() || m.isEnqueued())
        {
            _log.error("Message with Id " + m.getMessageId() + " is already staged");
            throw new MessageAlreadyStagedException("Message eith Id " + m.getMessageId() + " is already staged");
        }
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            stage(connection, m);
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot stage Message: " + m, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot stage Message: " + m, e);
                }
            }
        }
    }

    public void appendContent(StorableMessage m, byte[] data, int offset, int size)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        // The message must have been staged
        if (!m.isStaged())
        {
            _log.error("Cannot append content of message Id "
                       + m.getMessageId() + " as it has not been staged");
            throw new MessageDoesntExistException("Cannot append content of message Id "
                                                  + m.getMessageId() + " as it has not been staged");
        }
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            appendContent(connection, m, data, offset, size);
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot stage Message: " + m, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot stage Message: " + m, e);
                }
            }
        }
    }

    public byte[] loadContent(StorableMessage m, int offset, int size)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        MyConnection connection = null;
        try
        {
            byte[] result;
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[SELECT_MESSAGE_PAYLOAD];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("SELECT Payload FROM " + _tableNameMessage +
                                                                    " WHERE MessageID = ? ");
                connection.getStatements()[SELECT_MESSAGE_PAYLOAD] = pstmt;
            }
            pstmt.setLong(1, m.getMessageId());
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next())
            {
                throw new MessageDoesntExistException("Cannot load content of message Id "
                                                      + m.getMessageId() + " as it has not been found");
            }
            Blob myBlob = rs.getBlob(1);

            if (myBlob.length() > 0)
            {
                if (size == 0)
                {
                    result = myBlob.getBytes(offset, (int) myBlob.length());
                }
                else
                {
                    result = myBlob.getBytes(offset, size);
                }
            }
            else
            {
                throw new MessageDoesntExistException("Cannot load content of message Id "
                                                      + m.getMessageId() + " as it has not been found");
            }
            rs.close();
            return result;
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot load Message: " + m, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot load Message: " + m, e);
                }
            }
        }
    }

    public void destroy(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            destroy(connection, m);
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot destroy message: " + m, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot destroy message: " + m, e);
                }
            }
        }
    }

    public void enqueue(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException,
            MessageDoesntExistException
    {
        MyConnection connection = null;
        // Get the current tx
        JDBCTransaction tx = getTx(xid);
        // If this operation is transacted then we need to add a record
        if (tx != null && !tx.isPrepared())
        {
            // add an enqueue record
            tx.addRecord(new JDBCEnqueueRecord(m, queue));
        }
        else
        {
            try
            {
                if (tx != null)
                {
                    connection = tx.getConnection();
                }
                else
                {
                    connection = (MyConnection) _connectionPool.acquireInstance();
                }
                if (!m.isStaged() && !m.isEnqueued())
                {
                    //This is the first time this message is enqueued and it has not been staged.
                    stage(connection, m);
                    appendContent(connection, m, m.getData(), 0, m.getData().length);
                }
                PreparedStatement pstmt = connection.getStatements()[ENQUEUE];
                if (pstmt == null)
                {
                    pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameQueueMessageRelation +
                                                                        " (QueueID,MessageID,Prepared) VALUES (?,?,0)");
                    connection.getStatements()[ENQUEUE] = pstmt;
                }
                pstmt.setInt(1, queue.getQueueID());
                pstmt.setLong(2, m.getMessageId());
                pstmt.executeUpdate();
                m.enqueue(queue);
                queue.enqueue(m);
            }
            catch (Exception e)
            {
                throw new InternalErrorException("Cannot enqueue message : " + m + " in queue: " + queue, e);
            }
            finally
            {
                if (tx == null && connection != null)
                {
                    try
                    {
                        connection.getConnection().commit();
                        _connectionPool.releaseInstance(connection);
                    }
                    catch (SQLException e)
                    {
                        // we did not manage to commit this connection
                        // it is better to release it
                        _connectionPool.releaseDeadInstance();
                        throw new InternalErrorException("Cannot enqueue message : " + m + " in queue: " + queue, e);
                    }
                }
            }
        }
    }

    public void dequeue(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException
    {
        MyConnection connection = null;
        // Get the current tx
        JDBCTransaction tx = getTx(xid);
        // If this operation is transacted then we need to add a record
        if (tx != null && !tx.isPrepared())
        {
            // add an dequeue record
            tx.addRecord(new JDBCDequeueRecord(m, queue));
        }
        else
        {
            try
            {
                if (tx != null)
                {
                    connection = tx.getConnection();
                }
                else
                {
                    connection = (MyConnection) _connectionPool.acquireInstance();
                }
                PreparedStatement pstmt = connection.getStatements()[DEQUEUE];
                if (pstmt == null)
                {
                    pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameQueueMessageRelation +
                                                                        " WHERE QueueID = ? AND MessageID = ?");
                    connection.getStatements()[DEQUEUE] = pstmt;
                }
                pstmt.setInt(1, queue.getQueueID());
                pstmt.setLong(2, m.getMessageId());
                pstmt.executeUpdate();
                m.dequeue(queue);
                if (!m.isEnqueued())
                {
                    // delete this message from persistence store
                    destroy(connection, m);
                }
                queue.dequeue(m);
            }
            catch (Exception e)
            {
                throw new InternalErrorException("Cannot enqueue message : " + m + " in queue: " + queue, e);
            }
            finally
            {
                if (tx == null && connection != null)
                {
                    try
                    {
                        connection.getConnection().commit();
                        _connectionPool.releaseInstance(connection);
                    }
                    catch (SQLException e)
                    {
                        // we did not manage to commit this connection
                        // it is better to release it
                        _connectionPool.releaseDeadInstance();
                        throw new InternalErrorException("Cannot enqueue message : " + m + " in queue: " + queue, e);
                    }
                }
            }
        }
    }

    public Collection<StorableQueue> getAllQueues()
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        List<StorableQueue> result = new ArrayList<StorableQueue>();
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[GET_ALL_QUEUES];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("SELECT * FROM " + _tableNameQueue);
                connection.getStatements()[GET_ALL_QUEUES] = pstmt;
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next())
            {
                //the queue owner may be null
                AMQShortString queueOwner = null;
                if (rs.getString(3) != null)
                {
                    queueOwner = new AMQShortString(rs.getString(3));
                }
                result.add(new AMQQueue(new AMQShortString(rs.getString(2)), true, queueOwner,
                                        false, _virtualHost));
            }
            rs.close();
            return result;
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot get all queues", e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot get all queues", e);
                }
            }
        }
    }

    public Collection<StorableMessage> getAllMessages(StorableQueue queue)
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            return getAllMessages(connection, queue);
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot get all queues", e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot get all queues", e);
                }
            }
        }
    }

    public HashMap<Xid, Transaction> getAllInddoubt()
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        HashMap<Xid, Transaction> result = new HashMap<Xid, Transaction>();
        try
        {
            //TransactionalContext txnContext = new NonTransactionalContext(this, new StoreContext(), null, null, null);
            MessageHandleFactory messageHandleFactory = new MessageHandleFactory();
            // re-create all the tx
            connection = (MyConnection) _connectionPool.acquireInstance();
            Statement stmt = connection.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + _tableNameTransaction);
            JDBCTransaction foundTx;
            Xid foundXid;
            long foundXIDID;
            while (rs.next())
            {
                // set the XID_ID
                foundXIDID = rs.getLong(1);
                if (foundXIDID > JDBCTransaction._xidId)
                {
                    JDBCTransaction._xidId = foundXIDID;
                }
                foundTx = new JDBCTransaction();
                foundXid = new XidImpl(rs.getBlob(3).getBytes(1, (int) rs.getBlob(3).length()),
                                       rs.getInt(2), rs.getBlob(4).getBytes(1, (int) rs.getBlob(4).length()));
                // get all the records
                Statement stmtr = connection.getConnection().createStatement();
                ResultSet rsr = stmtr.executeQuery("SELECT * FROM " + _tableNameRecord +
                                                   " WHERE XID_ID = " + rs.getLong(1));
                int foundType;
                AMQQueue foundQueue;
                StorableMessage foundMessage;
                TransactionRecord foundRecord;
                while (rsr.next())
                {
                    // those messages were not recovered before so they need to be recreated
                    foundType = rsr.getInt(2);
                    foundQueue = _queueMap.get(new Integer(rsr.getInt(4)));

                    //DTX MessageStore - this -> null , txContext -> null
                    foundMessage = new AMQMessage(rs.getLong(3), null, messageHandleFactory, null);
                    if (foundType == JDBCAbstractRecord.TYPE_DEQUEUE)
                    {
                        foundRecord = new JDBCDequeueRecord(foundMessage, foundQueue);
                    }
                    else
                    {
                        foundRecord = new JDBCEnqueueRecord(foundMessage, foundQueue);
                    }
                    foundTx.addRecord(foundRecord);
                }
                rsr.close();
                // add this tx to the map
                result.put(foundXid, foundTx);
            }
            rs.close();
            return result;
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot recover: ", e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot recover: ", e);
                }
            }
        }
    }


    public long getNewMessageId()
    {
        return _messageID++;
    }

    //========================================================================
    // Public methods
    //========================================================================

    public MyConnection getConnection()
            throws
            Exception
    {
        return (MyConnection) _connectionPool.acquireInstance();
    }

    public void commitConnection(MyConnection connection)
            throws
            InternalErrorException
    {
        try
        {
            connection.getConnection().commit();
            _connectionPool.releaseInstance(connection);
        }
        catch (SQLException e)
        {
            // we did not manage to commit this connection
            // it is better to release it
            _connectionPool.releaseDeadInstance();
            throw new InternalErrorException("Cannot commit connection =", e);
        }
    }

    public void rollbackConnection(MyConnection connection)
            throws
            InternalErrorException
    {
        try
        {
            connection.getConnection().rollback();
            _connectionPool.releaseInstance(connection);
        }
        catch (SQLException e)
        {
            // we did not manage to rollback this connection
            // it is better to release it
            _connectionPool.releaseDeadInstance();
            throw new InternalErrorException("Cannot rollback connection", e);
        }
    }

    public void appendContent(MyConnection connection, StorableMessage m, byte[] data, int offset, int size)
            throws
            SQLException,
            MessageDoesntExistException
    {
        PreparedStatement pstmt = connection.getStatements()[SELECT_MESSAGE_PAYLOAD];
        if (pstmt == null)
        {
            pstmt = connection.getConnection().prepareStatement("SELECT Payload FROM " + _tableNameMessage +
                                                                " WHERE MessageID = ? ");
            connection.getStatements()[SELECT_MESSAGE_PAYLOAD] = pstmt;
        }
        pstmt.setLong(1, m.getMessageId());
        ResultSet rs = pstmt.executeQuery();
        if (!rs.next())
        {
            throw new MessageDoesntExistException("Cannot append content of message Id "
                                                  + m.getMessageId() + " as it has not been found");
        }
        Blob myBlob = rs.getBlob(1);
        byte[] oldPayload;
        if (myBlob != null && myBlob.length() > 0)
        {
            oldPayload = myBlob.getBytes(1, (int) myBlob.length());
        }
        else
        {
            oldPayload = new byte[0];
        }
        rs.close();
        byte[] newPayload = new byte[oldPayload.length + size];
        ByteBuffer buffer = ByteBuffer.wrap(newPayload);
        buffer.put(oldPayload);
        buffer.put(data, offset, size);
        PreparedStatement pstmtUpdate = connection.getStatements()[UPDATE_MESSAGE_PAYLOAD];
        if (pstmtUpdate == null)
        {
            pstmtUpdate = connection.getConnection().prepareStatement("UPDATE " + _tableNameMessage +
                                                                      " SET Payload = ? WHERE MessageID = ?");
            connection.getStatements()[UPDATE_MESSAGE_PAYLOAD] = pstmtUpdate;
        }
        pstmtUpdate.setBytes(1, newPayload);
        pstmtUpdate.setLong(2, m.getMessageId());
        pstmtUpdate.executeUpdate();
    }

    public void stage(MyConnection connection, StorableMessage m)
            throws
            Exception
    {
        PreparedStatement pstmt = connection.getStatements()[STAGE_MESSAGE];
        if (pstmt == null)
        {
            pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameMessage +
                                                                " (MessageID,Header,ExchangeName,RoutingKey,Mandatory,Is_Immediate) VALUES (?,?,?,?,?,?)");
            connection.getStatements()[STAGE_MESSAGE] = pstmt;
        }
        pstmt.setLong(1, m.getMessageId());
        pstmt.setBytes(2, m.getHeaderBody());
        pstmt.setString(3, ((AMQMessage) m).getMessagePublishInfo().getExchange().asString());
        pstmt.setString(4, ((AMQMessage) m).getMessagePublishInfo().getRoutingKey().asString());
        pstmt.setBoolean(5, ((AMQMessage) m).getMessagePublishInfo().isMandatory());
        pstmt.setBoolean(6, ((AMQMessage) m).getMessagePublishInfo().isImmediate());
        pstmt.executeUpdate();
        m.staged();
    }

    public void saveRecord(MyConnection connection, JDBCTransaction tx, JDBCAbstractRecord record)
            throws
            InternalErrorException
    {
        try
        {
            PreparedStatement pstmt = connection.getStatements()[SAVE_RECORD];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameRecord +
                                                                    " (XID_ID,Type,MessageID,QueueID) VALUES (?,?,?,?)");
                connection.getStatements()[SAVE_RECORD] = pstmt;
            }
            pstmt.setLong(1, tx.getXidID());
            pstmt.setInt(2, record.getType());
            pstmt.setLong(3, record.getMessageID());
            pstmt.setLong(4, record.getQueueID());
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot save record: " + record, e);
        }
    }

    public void saveXID(MyConnection connection, JDBCTransaction tx, Xid xid)
            throws
            InternalErrorException
    {
        try
        {
            PreparedStatement pstmt = connection.getStatements()[SAVE_XID];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("INSERT INTO " + _tableNameTransaction +
                                                                    " (XID_ID,FormatId, BranchQualifier,GlobalTransactionId) VALUES (?,?,?,?)");
                connection.getStatements()[SAVE_XID] = pstmt;
            }
            pstmt.setLong(1, tx.getXidID());
            pstmt.setInt(2, xid.getFormatId());
            pstmt.setBytes(3, xid.getBranchQualifier());
            pstmt.setBytes(4, xid.getGlobalTransactionId());
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot save xid: " + xid, e);
        }
    }

    public void deleteRecords(MyConnection connection, JDBCTransaction tx)
            throws
            InternalErrorException
    {
        try
        {
            PreparedStatement pstmt = connection.getStatements()[DELETE_RECORD];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameRecord +
                                                                    " WHERE XID_ID = ?");
                connection.getStatements()[DELETE_RECORD] = pstmt;
            }
            pstmt.setLong(1, tx.getXidID());
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot delete record: " + tx.getXidID(), e);
        }
    }

    public void deleteXID(MyConnection connection, JDBCTransaction tx)
            throws
            InternalErrorException
    {
        try
        {
            PreparedStatement pstmt = connection.getStatements()[DELETE_XID];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameTransaction +
                                                                    " WHERE XID_ID = ?");
                connection.getStatements()[DELETE_XID] = pstmt;
            }
            pstmt.setLong(1, tx.getXidID());
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot delete xid: " + tx.getXidID(), e);
        }
    }

    public void prepareDequeu(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            UnknownXidException,
            InternalErrorException
    {
        JDBCTransaction tx = getTx(xid);
        if (tx == null)
        {
            throw new UnknownXidException(xid, null);
        }
        updateQueueMessageRelation(tx.getConnection(), queue.getQueueID(), m.getMessageId(), 1);

    }

    public void rollbackDequeu(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            UnknownXidException,
            InternalErrorException
    {
        JDBCTransaction tx = getTx(xid);
        if (tx == null)
        {
            throw new UnknownXidException(xid, null);
        }
        updateQueueMessageRelation(tx.getConnection(), queue.getQueueID(), m.getMessageId(), 0);
    }

    //========================================================================
    // Private methods
    //========================================================================


    private void updateQueueMessageRelation(MyConnection connection,
                                            int queueID, long messageId, int prepared)
            throws
            InternalErrorException
    {
        try
        {
            PreparedStatement pstmt = connection.getStatements()[UPDATE_QMR];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("UPDATE " + _tableNameQueueMessageRelation +
                                                                    " SET Prepared = ? WHERE MessageID = ? AND QueueID = ?");
                connection.getStatements()[UPDATE_QMR] = pstmt;
            }
            pstmt.setInt(1, prepared);
            pstmt.setLong(2, messageId);
            pstmt.setInt(3, queueID);
            pstmt.executeUpdate();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot update QMR", e);
        }

    }

    public MessagePublishInfo getMessagePublishInfo(StorableMessage m)
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        MessagePublishInfo result;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[GET_MESSAGE_INFO];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("SELECT ExchangeName, RoutingKey," +
                                                                    " Mandatory, Is_Immediate from " + _tableNameMessage +
                                                                    " WHERE MessageID = ?");
                connection.getStatements()[GET_MESSAGE_INFO] = pstmt;
            }
            pstmt.setLong(1, m.getMessageId());
            final ResultSet rs = pstmt.executeQuery();
            if (rs.next())
            {
                
                result = new MessagePublishInfo()
                {
                    AMQShortString exchange = new AMQShortString(rs.getString(1));
                    final AMQShortString routingKey = new AMQShortString(rs.getString(2));
                    final boolean mandatory = rs.getBoolean(3);
                    final boolean immediate = rs.getBoolean(4);

                    public AMQShortString getExchange()
                    {
                        return exchange;
                    }

                    public boolean isImmediate()
                    {
                        return immediate;
                    }

                    public boolean isMandatory()
                    {
                        return mandatory;
                    }

                    public AMQShortString getRoutingKey()
                    {
                        return routingKey;
                    }

                    public void setExchange(AMQShortString ex) 
                    {
                        exchange = ex;
                    }
                };
            }
            else
            {
                throw new InternalErrorException("Cannot get MessagePublishInfo of message: " + m);
            }
            rs.close();
            return result;
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot get MessagePublishInfo of message: " + m, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot get MessagePublishInfo of message: " + m, e);
                }
            }
        }
    }

    public ContentHeaderBody getContentHeaderBody(StorableMessage m)
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        ContentHeaderBody result;
        try
        {
            connection = (MyConnection) _connectionPool.acquireInstance();
            PreparedStatement pstmt = connection.getStatements()[GET_CONTENT_HEADER];
            if (pstmt == null)
            {
                pstmt = connection.getConnection().prepareStatement("SELECT Header from " + _tableNameMessage +
                                                                    " WHERE MessageID = ?");
                connection.getStatements()[GET_CONTENT_HEADER] = pstmt;
            }
            pstmt.setLong(1, m.getMessageId());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next())
            {
                result = new ContentHeaderBody(ByteBuffer.wrap(rs.getBlob(1).getBytes(1, (int) rs.getBlob(1).length())), 0);
            }
            else
            {
                throw new InternalErrorException("Cannot get Content Header of message: " + m);
            }
            rs.close();
            return result;
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot get Content Header of message: " + m, e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot get Content Header of message: " + m, e);
                }
            }
        }
    }

    private List<StorableMessage> getAllMessages(MyConnection connection, StorableQueue queue)
            throws
            SQLException,
            AMQException
    {
        List<StorableMessage> result = new ArrayList<StorableMessage>();
//        TransactionalContext txnContext = new NonTransactionalContext(this, new StoreContext(), null, null, null);
        MessageHandleFactory messageHandleFactory = new MessageHandleFactory();
        PreparedStatement pstmt = connection.getStatements()[GET_ALL_MESSAGES];
        if (pstmt == null)
        {
            pstmt = connection.getConnection().prepareStatement("SELECT " + _tableNameMessage + ".MessageID, Header FROM " +
                                                                _tableNameMessage +
                                                                " INNER JOIN " +
                                                                _tableNameQueueMessageRelation +
                                                                " ON " +
                                                                _tableNameMessage + ".MessageID = " + _tableNameQueueMessageRelation + ".MessageID" +
                                                                " WHERE " +
                                                                _tableNameQueueMessageRelation + ".QueueID = ?" +
                                                                " AND " +
                                                                _tableNameQueueMessageRelation + ".Prepared = 0");
            connection.getStatements()[GET_ALL_MESSAGES] = pstmt;
        }
        pstmt.setInt(1, queue.getQueueID());
        ResultSet rs = pstmt.executeQuery();
        AMQMessage foundMessage;
        // ContentHeaderBody hb;
        while (rs.next())
        {

            //DTX MessageStore - this -> null , txContext -> null
            foundMessage = new AMQMessage(rs.getLong(1), null, messageHandleFactory, null);
            
            result.add(foundMessage);
        }
        rs.close();
        return result;
    }

    private HashMap<Integer, AMQQueue> recover()
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        HashMap<Integer, AMQQueue> result = new HashMap<Integer, AMQQueue>();
        try
        {
            // re-create all the queues
            connection = (MyConnection) _connectionPool.acquireInstance();
            Statement stmt = connection.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + _tableNameQueue);
            AMQQueue foundQueue;
            List<StorableMessage> foundMessages;
            StoreContext context = new StoreContext();
            while (rs.next())
            {
                AMQShortString owner = null;
                if (rs.getString(3) != null)
                {
                    owner = new AMQShortString(rs.getString(3));
                }
                foundQueue = new AMQQueue(new AMQShortString(rs.getString(2)),
                                          true, owner, false, _virtualHost);
                // get all the Messages of that queue
                foundMessages = getAllMessages(connection, foundQueue);
                // enqueue those messages
                if (_log.isDebugEnabled())
                {
                    _log.debug("Recovering " + foundMessages.size() + " messages for queue " + foundQueue.getName());
                }
                for (StorableMessage foundMessage : foundMessages)
                {
                    foundMessage.staged();
                    foundMessage.enqueue(foundQueue);
                    foundQueue.enqueue(foundMessage);
                    // FIXME: TGM AS foundQueue.process(context, (AMQMessage) foundMessage, false);
                }
                // add the queue in the result map
                result.put(foundQueue.getQueueID(), foundQueue);
                // add it in the registry
                _virtualHost.getQueueRegistry().registerQueue(foundQueue);
            }
            rs.close();
            return result;
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot recover: ", e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot recover: ", e);
                }
            }
        }
    }

    private void recoverExchanges(HashMap<Integer, AMQQueue> queueMap)
            throws
            InternalErrorException
    {
        MyConnection connection = null;
        try
        {
            // re-create all the exchanges
            connection = (MyConnection) _connectionPool.acquireInstance();
            Statement stmt = connection.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + _tableNameExchange);
            Exchange foundExchange;
            AMQQueue foundQueue;
            while (rs.next())
            {
                foundExchange = _virtualHost.getExchangeFactory().createExchange(
                        new AMQShortString(rs.getString(1)), new AMQShortString(rs.getString(2)), true, false);
                // get all the bindings
                Statement stmtb = connection.getConnection().createStatement();
                ResultSet rsb = stmtb.executeQuery("SELECT * FROM " + _tableNameExchangeQueueRelation +
                                                   " WHERE Name = '" + rs.getString(1) + "'");
                while (rsb.next())
                {
                    foundQueue = queueMap.get(new Integer(rsb.getInt(1)));
                    if (foundQueue != null)
                    {
                        // the field table
                        FieldTable ft = null;
                        if (rsb.getBlob(4) != null)
                        {
                            long length = rsb.getBlob(4).length();
                            ByteBuffer buffer = ByteBuffer.wrap(rsb.getBlob(4).getBytes(1, (int) length));
                            ft = new FieldTable(buffer, length);
                        }
                        foundQueue.bind(new AMQShortString(rsb.getString(3)), ft, foundExchange);
                    }
                }
                rsb.close();
                // register this exchange
                _virtualHost.getExchangeRegistry().registerExchange(foundExchange);
            }
            rs.close();
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Cannot recover: ", e);
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.getConnection().commit();
                    _connectionPool.releaseInstance(connection);
                }
                catch (SQLException e)
                {
                    // we did not manage to commit this connection
                    // it is better to release it
                    _connectionPool.releaseDeadInstance();
                    throw new InternalErrorException("Cannot recover: ", e);
                }
            }
        }
    }

    private void destroy(MyConnection connection, StorableMessage m)
            throws
            SQLException
    {
        PreparedStatement pstmt = connection.getStatements()[DELETE_MESSAGE];
        if (pstmt == null)
        {
            pstmt = connection.getConnection().prepareStatement("DELETE FROM " + _tableNameMessage +
                                                                " WHERE MessageID = ?");
            connection.getStatements()[DELETE_MESSAGE] = pstmt;
        }
        pstmt.setLong(1, m.getMessageId());
        pstmt.executeUpdate();
    }

    private JDBCTransaction getTx(Xid xid)
            throws
            UnknownXidException
    {
        JDBCTransaction tx = null;
        if (xid != null)
        {
            tx = _tm.getTransaction(xid);
        }
        return tx;
    }

    /**
     * setupConnections - Initialize the connections
     *
     * @return true if ok
     */
    private synchronized boolean setupConnections()
    {
        try
        {
            if (_connectionPool == null)
            {
                // In an embedded environment, loading the driver also starts Derby.
                Class.forName(_driver).newInstance();
                _connectionPool = new ConnectionPool(_maxSize);
            }
        }
        catch (Exception e)
        {
            _log.warn("Setup connections trouble", e);
            return false;
        }
        return true;
    }

    /**
     * Try to create the connection and table.
     * If this fails, then we will exit.
     */
    protected synchronized boolean setupStore(boolean dropTables)
    {
        if (!setupConnections())
        {
            return false;
        }
        MyConnection myconnection = null;
        try
        {
            myconnection = (MyConnection) _connectionPool.acquireInstance();
            Statement stmt = myconnection._connection.createStatement();
            /*
            * TODO Need some management interface to delete the table!
            */
            if (dropTables)
            {
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameMessage);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // don't want to print error - chances are it
                    // just reports that the table does not exist
                    // ex.printStackTrace();
                }
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameQueue);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // ex.printStackTrace();
                }
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameQueueMessageRelation);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // ex.printStackTrace();
                }
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameExchange);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // ex.printStackTrace();
                }
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameExchangeQueueRelation);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // ex.printStackTrace();
                }
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameRecord);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // ex.printStackTrace();
                }
                try
                {
                    stmt.executeUpdate("DROP TABLE " + _tableNameTransaction);
                    myconnection._connection.commit();
                }
                catch (SQLException ex)
                {
                    // ex.printStackTrace();
                }
            }
            // create the table for messages
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameMessage + " (MessageID FLOAT NOT NULL, Header BLOB," +
                                   " Payload BLOB, ExchangeName VARCHAR(1024), RoutingKey VARCHAR(1024)," +
                                   " Mandatory INTEGER, Is_Immediate INTEGER, PRIMARY KEY(MessageID))");
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                // ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
            // create the table for queues
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameQueue + " (QueueID INTEGER NOT NULL, " +
                                   "Name VARCHAR(1024) NOT NULL, Owner VARCHAR(1024), PRIMARY KEY(QueueID))");
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                //ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
            // create the table for queue to message mapping
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameQueueMessageRelation + " (QueueID INTEGER NOT NULL, " +
                                   "MessageID FLOAT NOT NULL, Prepared INTEGER)");
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                //ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameExchange + " (Name VARCHAR(1024) NOT NULL, " +
                                   "Type VARCHAR(1024) NOT NULL, PRIMARY KEY(Name))");
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                //ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameExchangeQueueRelation + " (QueueID INTEGER NOT NULL, " +
                                   "Name VARCHAR(1024) NOT NULL, RoutingKey VARCHAR(1024), FieldTable BLOB )");
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                //ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameRecord + " (XID_ID FLOAT, Type INTEGER, MessageID FLOAT, " +
                                   "QueueID INTEGER, PRIMARY KEY(Type, MessageID, QueueID))");
                // we could alter the table with QueueID as foreign key
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                //ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
            try
            {
                stmt.executeUpdate("CREATE TABLE " + _tableNameTransaction + " (XID_ID FLOAT, FormatId INTEGER, " +
                                   "BranchQualifier BLOB, GlobalTransactionId BLOB, PRIMARY KEY(XID_ID))");
                myconnection._connection.commit();
            }
            catch (SQLException ex)
            {
                // ex.printStackTrace();
                // assume this is reporting that the table already exists:
            }
        }
        catch (Throwable e)
        {
            _log.warn("Setup Store trouble: ", e);
            return false;
        }
        finally
        {
            if (myconnection != null)
            {
                _connectionPool.releaseInstance(myconnection);
            }
        }
        return true;
    }
    //========================================================================================
    //============== the connection pool =====================================================
    //========================================================================================

    private class ConnectionPool extends Pool
    {

        /**
         * Create a pool of specified size. Negative or null pool sizes are
         * disallowed.
         *
         * @param poolSize The size of the pool to create. Should be 1 or
         *                 greater.
         * @throws Exception If the pool size is less than 1.
         */
        public ConnectionPool(int poolSize)
                throws
                Exception
        {
            super(poolSize);
        }

        /**
         * @return An instance of the pooled object.
         * @throws Exception In case of internal error.
         */
        protected MyConnection createInstance()
                throws
                Exception
        {
            try
            {
                // standard way to obtain a Connection object is to call the method DriverManager.getConnection,
                // which takes a String containing a connection URL (uniform resource locator).
                Connection conn = DriverManager.getConnection(_connectionURL);
                //conn.setAutoCommit(true);
                PreparedStatement[] st = new PreparedStatement[STATEMENT_SIZE];
                for (int j = 0; j < STATEMENT_SIZE; j++)
                {
                    st[j] = null;
                }
                return new MyConnection(conn, st);
            }
            catch (SQLException e)
            {
                throw new Exception("sqlException when creating connection to " + _connectionURL, e);
            }
        }
    }

    public class MyConnection
    {
        // the connection
        private Connection _connection = null;
        // its associated prepared statements
        private PreparedStatement[] _preparedStatements = null;

        MyConnection(Connection con, PreparedStatement[] st)
        {
            _connection = con;
            _preparedStatements = st;
        }

        public Connection getConnection()
        {
            return _connection;
        }

        public PreparedStatement[] getStatements()
        {
            return _preparedStatements;
        }

    }
}
