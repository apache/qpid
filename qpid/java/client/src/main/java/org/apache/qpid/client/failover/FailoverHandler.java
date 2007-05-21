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
package org.apache.qpid.client.failover;

import java.util.concurrent.CountDownLatch;

import org.apache.log4j.Logger;
import org.apache.mina.common.IoSession;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.state.AMQStateManager;

/**
 * When failover is required, we need a separate thread to handle the establishment of the new connection and
 * the transfer of subscriptions.
 * </p>
 * The reason this needs to be a separate thread is because you cannot do this work inside the MINA IO processor
 * thread. One significant task is the connection setup which involves a protocol exchange until a particular state
 * is achieved. However if you do this in the MINA thread, you have to block until the state is achieved which means
 * the IO processor is not able to do anything at all.
 */
public class FailoverHandler implements Runnable
{
    private static final Logger _logger = Logger.getLogger(FailoverHandler.class);

    private final IoSession _session;
    private AMQProtocolHandler _amqProtocolHandler;

    /**
     * Used where forcing the failover host
     */
    private String _host;

    /**
     * Used where forcing the failover port
     */
    private int _port;

    public FailoverHandler(AMQProtocolHandler amqProtocolHandler, IoSession session)
    {
        _amqProtocolHandler = amqProtocolHandler;
        _session = session;
    }

    public void run()
    {
        if (Thread.currentThread().isDaemon())
        {
            throw new IllegalStateException("FailoverHandler must run on a non-daemon thread.");
        }
        //Thread.currentThread().setName("Failover Thread");

        _amqProtocolHandler.setFailoverLatch(new CountDownLatch(1));

        // We wake up listeners. If they can handle failover, they will extend the
        // FailoverSupport class and will in turn block on the latch until failover
        // has completed before retrying the operation
        _amqProtocolHandler.propagateExceptionToWaiters(new FailoverException("Failing over about to start"));

        // Since failover impacts several structures we protect them all with a single mutex. These structures
        // are also in child objects of the connection. This allows us to manipulate them without affecting
        // client code which runs in a separate thread.
        synchronized (_amqProtocolHandler.getConnection().getFailoverMutex())
        {
            // We switch in a new state manager temporarily so that the interaction to get to the "connection open"
            // state works, without us having to terminate any existing "state waiters". We could theoretically
            // have a state waiter waiting until the connection is closed for some reason. Or in future we may have
            // a slightly more complex state model therefore I felt it was worthwhile doing this.
            AMQStateManager existingStateManager = _amqProtocolHandler.getStateManager();
            _amqProtocolHandler.setStateManager(new AMQStateManager(_amqProtocolHandler.getProtocolSession()));
            if (!_amqProtocolHandler.getConnection().firePreFailover(_host != null))
            {
                _logger.info("Failover process veto-ed by client");

                _amqProtocolHandler.setStateManager(existingStateManager);
                if (_host != null)
                {
                    _amqProtocolHandler.getConnection().exceptionReceived(new AMQDisconnectedException("Redirect was vetoed by client", null));
                }
                else
                {
                    _amqProtocolHandler.getConnection().exceptionReceived(new AMQDisconnectedException("Failover was vetoed by client", null));
                }
                _amqProtocolHandler.getFailoverLatch().countDown();
                _amqProtocolHandler.setFailoverLatch(null);
                return;
            }

            _logger.info("Starting failover process");

            boolean failoverSucceeded;
            // when host is non null we have a specified failover host otherwise we all the client to cycle through
            // all specified hosts

            // if _host has value then we are performing a redirect.
            if (_host != null)
            {
                failoverSucceeded = _amqProtocolHandler.getConnection().attemptReconnection(_host, _port);
            }
            else
            {
                failoverSucceeded = _amqProtocolHandler.getConnection().attemptReconnection();
            }
            if (!failoverSucceeded)
            {
                _amqProtocolHandler.setStateManager(existingStateManager);
                _amqProtocolHandler.getConnection().exceptionReceived(
                        new AMQDisconnectedException("Server closed connection and no failover " +
                                "was successful", null));
            }
            else
            {
                _amqProtocolHandler.setStateManager(existingStateManager);
                try
                {
                    if (_amqProtocolHandler.getConnection().firePreResubscribe())
                    {
                        _logger.info("Resubscribing on new connection");
                        _amqProtocolHandler.getConnection().resubscribeSessions();
                    }
                    else
                    {
                        _logger.info("Client vetoed automatic resubscription");
                    }
                    _amqProtocolHandler.getConnection().fireFailoverComplete();
                    _amqProtocolHandler.setFailoverState(FailoverState.NOT_STARTED);
                    _logger.info("Connection failover completed successfully");
                }
                catch (Exception e)
                {
                    _logger.info("Failover process failed - exception being propagated by protocol handler");
                    _amqProtocolHandler.setFailoverState(FailoverState.FAILED);
                    try
                    {
                        _amqProtocolHandler.exceptionCaught(_session, e);
                    }
                    catch (Exception ex)
                    {
                        _logger.error("Error notifying protocol session of error: " + ex, ex);
                    }
                }
            }
        }
        _amqProtocolHandler.getFailoverLatch().countDown();
    }

    public String getHost()
    {
        return _host;
    }

    public void setHost(String host)
    {
        _host = host;
    }

    public int getPort()
    {
        return _port;
    }

    public void setPort(int port)
    {
        _port = port;
    }
}
