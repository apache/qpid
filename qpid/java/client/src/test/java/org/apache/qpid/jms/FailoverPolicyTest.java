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
package org.apache.qpid.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.jms.failover.FailoverExchangeMethod;
import org.apache.qpid.jms.failover.FailoverMethod;
import org.apache.qpid.jms.failover.FailoverRoundRobinServers;
import org.apache.qpid.jms.failover.FailoverSingleServer;
import org.apache.qpid.jms.failover.NoFailover;

import junit.framework.TestCase;

/**
 * Tests the ability of FailoverPolicy to instantiate the correct FailoverMethod.
 *
 * This test presently does <i>not</i> test {@link FailoverPolicy#FailoverPolicy(FailoverMethod) or
 * {@link FailoverPolicy#addMethod(FailoverMethod)} as it appears that this functionality
 * is no longer in use.
 *
 */
public class FailoverPolicyTest extends TestCase
{
    private FailoverPolicy _failoverPolicy = null; // class under test
    private String _url;
    private Connection _connection = null;
    private ConnectionURL _connectionUrl = null;

    /**
     * Tests single server method is selected for a brokerlist with one broker when
     * the failover option is not specified.
     */
    public void testBrokerListWithOneBrokerDefaultsToSingleServerPolicy() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof FailoverSingleServer);
    }

    /**
     * Tests round robin method is selected for a brokerlist with two brokers when
     * the failover option is not specified.
     */
    public void testBrokerListWithTwoBrokersDefaultsToRoundRobinPolicy() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof FailoverRoundRobinServers);
    }

    /**
     * Tests single server method is selected for a brokerlist with one broker when
     * the failover option passed as 'singlebroker'.
     */
    public void testExplictFailoverOptionSingleBroker() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672'&failover='singlebroker'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof FailoverSingleServer);
    }

    /**
     * Tests round robin method is selected for a brokerlist with two brokers when
     * the failover option passed as 'roundrobin'.
     */
    public void testExplictFailoverOptionRoundrobin() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672;tcp://localhost:5673'&failover='roundrobin'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof FailoverRoundRobinServers);
    }

    /**
     * Tests no failover method is selected for a brokerlist with one broker when
     * the failover option passed as 'nofailover'.
     */
    public void testExplictFailoverOptionNofailover() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672'&failover='nofailover'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof NoFailover);
    }

    /**
     * Tests failover exchange method is selected for a brokerlist with one broker when
     * the failover option passed as 'failover_exchange'.
     */
    public void testExplictFailoverOptionFailoverExchange() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672'&failover='failover_exchange'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof FailoverExchangeMethod);
    }

    /**
     * Tests that a custom method can be selected for a brokerlist with one brokers when
     * the failover option passed as a qualified class-name.
     */
    public void testExplictFailoverOptionDynamicallyLoadedFailoverMethod() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672'&failover='org.apache.qpid.jms.FailoverPolicyTest$MyFailoverMethod'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        _failoverPolicy = new FailoverPolicy(_connectionUrl, _connection);

        assertTrue("Unexpected failover method", _failoverPolicy.getCurrentMethod() instanceof MyFailoverMethod);
    }

    /**
     * Tests that an unknown method caused an exception.
     */
    public void testUnknownFailoverMethod() throws Exception
    {
        _url = "amqp://user:pass@clientid/test?brokerlist='tcp://localhost:5672'&failover='unknown'";
        _connectionUrl = new AMQConnectionURL(_url);
        _connection = createStubConnection();

        try
        {
            new FailoverPolicy(_connectionUrl, _connection);
            fail("Exception not thrown");
        }
        catch(IllegalArgumentException iae)
        {
            // PASS
        }
    }

    private Connection createStubConnection()
    {
        return new Connection()
        {

            @Override
            public Session createSession(boolean transacted,
                    int acknowledgeMode, int prefetch) throws JMSException
            {
                return null;
            }

            @Override
            public Session createSession(boolean transacted,
                    int acknowledgeMode, int prefetchHigh, int prefetchLow)
                    throws JMSException
            {
                return null;
            }

            @Override
            public ConnectionListener getConnectionListener()
            {
                return null;
            }

            @Override
            public long getMaximumChannelCount() throws JMSException
            {
                return 0;
            }

            @Override
            public void setConnectionListener(ConnectionListener listener)
            {
            }

            @Override
            public void close() throws JMSException
            {
            }

            @Override
            public ConnectionConsumer createConnectionConsumer(
                    Destination arg0, String arg1, ServerSessionPool arg2,
                    int arg3) throws JMSException
            {
                return null;
            }

            @Override
            public ConnectionConsumer createDurableConnectionConsumer(
                    Topic arg0, String arg1, String arg2,
                    ServerSessionPool arg3, int arg4) throws JMSException
            {
                return null;
            }

            @Override
            public javax.jms.Session createSession(boolean arg0, int arg1)
                    throws JMSException
            {
                return null;
            }

            @Override
            public String getClientID() throws JMSException
            {
                return null;
            }

            @Override
            public ExceptionListener getExceptionListener() throws JMSException
            {
                return null;
            }

            @Override
            public ConnectionMetaData getMetaData() throws JMSException
            {
                return null;
            }

            @Override
            public void setClientID(String arg0) throws JMSException
            {
            }

            @Override
            public void setExceptionListener(ExceptionListener arg0)
                    throws JMSException
            {
            }

            @Override
            public void start() throws JMSException
            {
            }

            @Override
            public void stop() throws JMSException
            {
            }
        };
    }

    // Class used to test the ability of FailoverPolicy to load an implementation.
    static class MyFailoverMethod implements FailoverMethod
    {
        public MyFailoverMethod(ConnectionURL connectionDetails)
        {
        }

        @Override
        public void attainedConnection()
        {
        }

        @Override
        public boolean failoverAllowed()
        {
            return false;
        }

        @Override
        public BrokerDetails getCurrentBrokerDetails()
        {
            return null;
        }

        @Override
        public BrokerDetails getNextBrokerDetails()
        {
            return null;
        }

        @Override
        public String methodName()
        {
            return null;
        }

        @Override
        public void reset()
        {
        }

        @Override
        public void setBroker(BrokerDetails broker)
        {
        }

        @Override
        public void setRetries(int maxRetries)
        {
        }
    }

}
