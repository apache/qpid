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

package org.apache.qpid.nclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.client.url.URLParser_0_10;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.url.QpidURL;
import org.apache.qpid.ErrorCode;
import org.apache.qpid.QpidException;
import org.apache.qpid.nclient.impl.ClientSession;
import org.apache.qpid.nclient.impl.ClientSessionDelegate;
import org.apache.qpid.transport.Channel;
import org.apache.qpid.transport.ClientDelegate;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionClose;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ConnectionCloseOk;
import org.apache.qpid.transport.ProtocolHeader;
import org.apache.qpid.transport.ProtocolVersionException;
import org.apache.qpid.transport.SessionDelegate;
import org.apache.qpid.transport.network.io.IoTransport;
import org.apache.qpid.transport.network.mina.MinaHandler;
import org.apache.qpid.transport.network.nio.NioHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Client implements org.apache.qpid.nclient.Connection
{
    private Connection _conn;
    private ClosedListener _closedListner;
    private final Lock _lock = new ReentrantLock();
    private static Logger _logger = LoggerFactory.getLogger(Client.class);
    private Condition closeOk;
    private boolean closed = false;
    private long timeout = 60000;

    private ProtocolHeader header = null;

    /**
     *
     * @return returns a new connection to the broker.
     */
    public static org.apache.qpid.nclient.Connection createConnection()
    {
        return new Client();
    }

    public void connect(String host, int port,String virtualHost,String username, String password) throws QpidException
    {

        final Condition negotiationComplete = _lock.newCondition();
        closeOk = _lock.newCondition();
        _lock.lock();

        ClientDelegate connectionDelegate = new ClientDelegate()
        {
            private boolean receivedClose = false;
            public SessionDelegate getSessionDelegate()
            {
                return new ClientSessionDelegate();
            }

            public void exception(Throwable t)
            {
                if (_closedListner != null)
                {
                    _closedListner.onClosed(ErrorCode.CONNECTION_ERROR,ErrorCode.CONNECTION_ERROR.getDesc(),t);
                }
                else
                {
                    throw new RuntimeException("connection closed",t);
                }
            }

            public void closed()
            {
                if (_closedListner != null && !this.receivedClose)
                {
                    _closedListner.onClosed(ErrorCode.CONNECTION_ERROR,ErrorCode.CONNECTION_ERROR.getDesc(),null);
                }
            }

            @Override public void connectionCloseOk(Channel context, ConnectionCloseOk struct)
            {
                _lock.lock();
                try
                {
                    closed = true;
                    this.receivedClose = true;
                    closeOk.signalAll();
                }
                finally
                {
                    _lock.unlock();
                }
            }

            @Override public void connectionClose(Channel context, ConnectionClose connectionClose)
            {
                ErrorCode errorCode = ErrorCode.get(connectionClose.getReplyCode().getValue());
                if (_closedListner == null && errorCode != ErrorCode.NO_ERROR)
                {
                    throw new RuntimeException
                        (new QpidException("Server closed the connection: Reason " +
                                           connectionClose.getReplyText(),
                                           errorCode,
                                           null));
                }
                else
                {
                    _closedListner.onClosed(errorCode, connectionClose.getReplyText(),null);
                }

                this.receivedClose = true;
            }
            @Override public void init(Channel ch, ProtocolHeader hdr)
            {
                // TODO: once the merge is done we'll need to update this code
                // for handling 0.8 protocol version type i.e. major=8 and mino
                if (hdr.getMajor() != 0 || hdr.getMinor() != 10)
                {
                    Client.this.header = hdr;
                    _lock.lock();
                    negotiationComplete.signalAll();
                    _lock.unlock();
                }
            }
        };

        connectionDelegate.setCondition(_lock,negotiationComplete);
        connectionDelegate.setUsername(username);
        connectionDelegate.setPassword(password);
        connectionDelegate.setVirtualHost(virtualHost);

        String transport = System.getProperty("transport","io");
        if (transport.equalsIgnoreCase("nio"))
        {
            _logger.info("using NIO Transport");
            _conn = NioHandler.connect(host, port,connectionDelegate);
        }
        else if (transport.equalsIgnoreCase("io"))
        {
            _logger.info("using Plain IO Transport");
            _conn = IoTransport.connect(host, port,connectionDelegate);
        }
        else
        {
            _logger.info("using MINA Transport");
            _conn = MinaHandler.connect(host, port,connectionDelegate);
           // _conn = NativeHandler.connect(host, port,connectionDelegate);
        }

        // XXX: hardcoded version numbers
        _conn.send(new ProtocolHeader(1, 0, 10));

        try
        {
            negotiationComplete.await(timeout, TimeUnit.MILLISECONDS);
            if (header != null)
            {
                _conn.close();
                throw new ProtocolVersionException(header.getMajor(), header.getMinor());
            }
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            _lock.unlock();
        }
    }

    public void connect(String url)throws QpidException
    {
        URLParser_0_10 parser = null;
        try
        {
            parser = new URLParser_0_10(url);
        }
        catch(Exception e)
        {
            throw new QpidException("Error parsing the URL",ErrorCode.UNDEFINED,e);
        }
        List<BrokerDetails> brokers = parser.getAllBrokerDetails();
        BrokerDetails brokerDetail = brokers.get(0);
        connect(brokerDetail.getHost(), brokerDetail.getPort(), brokerDetail.getProperty("virtualhost"),
                brokerDetail.getProperty("username")== null? "guest":brokerDetail.getProperty("username"),
                brokerDetail.getProperty("password")== null? "guest":brokerDetail.getProperty("password"));
    }

    /*
     * Until the dust settles with the URL disucssion
     * I am not going to implement this.
     */
    public void connect(QpidURL url) throws QpidException
    {
        throw new UnsupportedOperationException("Not implemented");
    }

   /* {
        // temp impl to tests
        BrokerDetails details = url.getAllBrokerDetails().get(0);
        connect(details.getHost(),
                details.getPort(),
                details.getVirtualHost(),
                details.getUserName(),
                details.getPassword());
    }
*/

    public void close() throws QpidException
    {
        Channel ch = _conn.getChannel(0);
        ch.connectionClose(ConnectionCloseCode.NORMAL, "client is closing");
        _lock.lock();
        try
        {
            try
            {
                long start = System.currentTimeMillis();
                long elapsed = 0;
                while (!closed && elapsed < timeout)
                {
                    closeOk.await(timeout - elapsed, TimeUnit.MILLISECONDS);
                    elapsed = System.currentTimeMillis() - start;
                }
                if(!closed)
                {
                    throw new QpidException("Timed out when closing connection", ErrorCode.CONNECTION_ERROR, null);
                }
            }
            catch (InterruptedException e)
            {
                 throw new QpidException("Interrupted when closing connection", ErrorCode.CONNECTION_ERROR, null);
            }
        }
        finally
        {
            _lock.unlock();
        }
        _conn.close();
    }

    public Session createSession(long expiryInSeconds)
    {
        Channel ch = _conn.getChannel();
        ClientSession ssn = new ClientSession(UUID.randomUUID().toString().getBytes());
        ssn.attach(ch);
        ssn.sessionAttach(ssn.getName());
        ssn.sessionRequestTimeout(expiryInSeconds);
        return ssn;
    }

    public DtxSession createDTXSession(int expiryInSeconds)
    {
         ClientSession clientSession =  (ClientSession) createSession(expiryInSeconds);
         clientSession.dtxSelect();
         return (DtxSession) clientSession;
    }

    public void setClosedListener(ClosedListener closedListner)
    {

        _closedListner = closedListner;
    }

}
