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
package org.apache.qpid.management.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPoolFactory;
import org.apache.qpid.ErrorCode;
import org.apache.qpid.QpidException;
import org.apache.qpid.nclient.Client;
import org.apache.qpid.nclient.ClosedListener;
import org.apache.qpid.nclient.Connection;
import org.apache.qpid.nclient.DtxSession;
import org.apache.qpid.nclient.Session;
import org.apache.qpid.transport.util.Logger;

/**
 * Qpid datasource.
 * Basically it is a connection pool manager used for optimizing broker connections usage. 
 * 
 * @author Andrea Gazzarini
 */
public final class QpidDatasource 
{
    private final static Logger LOGGER = Logger.get(QpidDatasource.class);
    
    /**
     * A connection decorator used for adding pool interaction behaviour to an existing connection.
     * 
     * @author Andrea Gazzarini
     */
    public class ConnectionDecorator implements Connection,ClosedListener 
    {
        private final Connection _decoratee;
        private final UUID _brokerId;
        private boolean _valid;
        private BrokerConnectionData _connectionData;
      
        /**
         * Builds a new decorator with the given connection.
         * 
         * @param brokerId the broker identifier.
         * @param decoratee the underlying connection.
         * @param connectionData connection details used for logging purposes.
         */
        private ConnectionDecorator(UUID brokerId, Connection decoratee, BrokerConnectionData connectionData)
        {
            this._decoratee = decoratee;
            this._brokerId = brokerId;
            this._decoratee.setClosedListener(this);
            this._connectionData = connectionData;
            _valid = true;
            LOGGER.debug("<QMAN-200045> : Connection %s for pool %s created.",this,_connectionData);
        }
        
        /**
         * Returns true if the underlying connection is still valid and can be used.
         * 
         * @return true if the underlying connection is still valid and can be used.
         */
        boolean isValid()
        {
            if (_valid)
            {
                LOGGER.debug("<QMAN-200013> : Pooled connection %s for %s  seems to be valid.",this,_connectionData);
            } else 
            {
                LOGGER.debug("<QMAN-200013> : Pooled connection %s for %s  has been marked as invalid.",this,_connectionData);                
            }
            return _valid;
        }
        
        /**
         * Returns the connection to the pool. That is, marks this connections as available.
         * After that, this connection will be available for further operations.
         */
        public void close () throws QpidException
        {
            try
            {
                pools.get(_brokerId).returnObject(this);
                LOGGER.debug("<QMAN-200012> : <Connection for pool %s released.", _connectionData);
            } catch (Exception exception)
            {
                throw new QpidException("<QMAN-100203> : Error while releasing pooled connection.",ErrorCode.CONNECTION_ERROR,exception);
            }  
        }

        /**
         * Do nothing : underlying connection is already connected.
         */
        public void connect (String host, int port, String virtualHost, String username, String password)
                throws QpidException
        {
            // DO NOTHING : DECORATEE CONNECTION IS ALREADY CONNECTED.
        }
        
        /**
         * Do nothing : underlying connection is already connected.
         */
        public void connect (String url) throws QpidException
        {
            // DO NOTHING : DECORATEE CONNECTION IS ALREADY CONNECTED.
        }

        /**
         * @see Connection#createDTXSession(int)
         */
        public DtxSession createDTXSession (int expiryInSeconds)
        {
            return _decoratee.createDTXSession(expiryInSeconds);
        }

        /**
         * @see Connection#createSession(long)
         */
        public Session createSession (long expiryInSeconds)
        {
            return _decoratee.createSession(expiryInSeconds);
        }

        /**
         * Do nothing : closed listener  has been already injected.
         */
        public void setClosedListener (ClosedListener exceptionListner)
        {
        }
        
        /**
         * Callback method used for error notifications while underlying connection is closing.
         */
        public void onClosed (ErrorCode errorCode, String reason, Throwable throwable)
        {
            _valid = false;
            LOGGER.error(
                    throwable,
                    "<QMAN-100012> : Error on closing connection. Reason is : %s, error code is %s. Connection %s to %s will be " +
                    "marked as invalid and therefore will be purged..",
                    reason,
                    errorCode.getCode(),
                    this,
                    _connectionData);
        }        
    };
        
    /**
     * This is the connection factory, that is, the factory used to manage the lifecycle (create, validate & destroy) of 
     * the broker connection(s).
     * 
     * @author Andrea Gazzarini
     */
    class QpidConnectionFactory extends BasePoolableObjectFactory
    {    
        private final BrokerConnectionData _connectionData;
        private final UUID _brokerId;
        
        /**
         * Builds a new connection factory with the given parameters.
         * 
         * @param brokerId the broker identifier.
         * @param connectionData the connecton data.
         */
        private QpidConnectionFactory(UUID brokerId, BrokerConnectionData connectionData)
        {
            this._connectionData = connectionData;
            this._brokerId = brokerId;
        }
        
        /**
         * Creates a new underlying connection.
         */
        @Override
        public Connection makeObject () throws Exception
        {
            Connection connection = Client.createConnection();
            connection.connect(
                    _connectionData.getHost(), 
                    _connectionData.getPort(), 
                    _connectionData.getVirtualHost(), 
                    _connectionData.getUsername(), 
                    _connectionData.getPassword());
            return new ConnectionDecorator(_brokerId,connection,_connectionData);
        }
    
        /**
         * Validates the underlying connection.
         */
        @Override
        public boolean validateObject (Object obj)
        {
            ConnectionDecorator connection = (ConnectionDecorator) obj;
            return connection.isValid();
        }
        
        /**
         * Closes the underlying connection.
         */
        @Override
        public void destroyObject (Object obj) throws Exception
        {
            ConnectionDecorator connection = (ConnectionDecorator) obj;
            try
            {
                connection._decoratee.close();
                LOGGER.debug("<QMAN-200014> : Connection for %s has been closed.",connection._connectionData);
            } catch (Exception exception)
            {
                LOGGER.debug(
                        exception,
                        "<QMAN-200015> : Unable to close an underlying qpid connection (target address is %s) .",
                        connection._connectionData);
            }
        }
    }
   
    // Singleton instance.
    private static QpidDatasource instance = new QpidDatasource();

    // Each entry contains a connection pool for a specific broker.
    private Map<UUID, ObjectPool> pools = new HashMap<UUID, ObjectPool>();
    
    // Private constructor.
    private QpidDatasource()
    {
    }
    
    /**
     * Gets an available connection from the pool of the given broker.
     * 
     * @param brokerId the broker identifier.
     * @return a valid connection to the broker associated with the given identifier.
     */
    public Connection getConnection(UUID brokerId) throws Exception
    {
        return (Connection) pools.get(brokerId).borrowObject();
    }
    
    /**
     * Entry point method for retrieving the singleton instance of this datasource.
     * 
     * @return the qpid datasource singleton instance.
     */
    public static QpidDatasource getInstance() 
    {
        return instance;
    }
    
    /**
     * Adds a connection pool to this datasource.
     * 
     * @param brokerId the broker identifier that will be associated with the new connection pool.
     * @param connectionData the broker connection data.
     * @throws Exception when the pool cannot be created.
     */
    void addConnectionPool(UUID brokerId,BrokerConnectionData connectionData) throws Exception {
        GenericObjectPoolFactory factory = new GenericObjectPoolFactory(
                new QpidConnectionFactory(brokerId,connectionData),
                connectionData.getMaxPoolCapacity(),
                GenericObjectPool.WHEN_EXHAUSTED_BLOCK,
                connectionData.getMaxWaitTimeout(),-1,
                true,
                false);
        ObjectPool pool = factory.createPool();

        // Open connections at startup according to initial capacity param value.
        int howManyConnectionAtStartup = connectionData.getInitialPoolCapacity(); 
        Object [] openStartupList = new Object[howManyConnectionAtStartup];
        
        // Open...
        for (int index  = 0; index < howManyConnectionAtStartup; index++)
        {
            openStartupList[index] = pool.borrowObject();
        }
        
        // ...and immediately return them to pool. In this way the pooled connection has been opened.
        for (int index = 0; index < howManyConnectionAtStartup; index++)
        {
            pool.returnObject(openStartupList[index]);
        }

        pools.put(brokerId,pool);
    }
}