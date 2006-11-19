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
package org.apache.qpid.test.unit.basic;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;

import javax.jms.MessageConsumer;

import junit.framework.TestCase;

public class ReceiveTest extends TestCase
{
    private AMQConnection _connection;
    private AMQDestination _destination;
    private AMQSession _session;
    private MessageConsumer _consumer;

    private static final String VM_BROKER = "vm://:1";
    public String _connectionString = VM_BROKER;

    protected void setUp() throws Exception
    {
        super.setUp();
        if (_connectionString.equals(VM_BROKER))
        {
            createVMBroker();
            String broker = _connectionString;
            init(new AMQConnection(broker, "guest", "guest", "ReceiveTestClient", "/test_path"));
        }
    }

    public void createVMBroker()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            fail("Unable to create broker: " + e);
        }
    }

    protected void tearDown() throws Exception
    {
        if (_connectionString.equals(VM_BROKER))
        {
            TransportConnection.killVMBroker(1);
        }
        super.tearDown();
    }

    private void init(AMQConnection connection) throws Exception
    {
        init(connection, new AMQQueue("ReceiveTest", true));
    }

    private void init(AMQConnection connection, AMQDestination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = (AMQSession) connection.createSession(true, AMQSession.NO_ACKNOWLEDGE);
        _consumer = _session.createConsumer(_destination);
        _connection.start();
    }

    public void test() throws Exception
    {
        _consumer.receive(5000);
        _connection.close();
    }

    public static void main(String[] argv) throws Exception
    {
        ReceiveTest test = new ReceiveTest();
        test._connectionString = argv.length == 0 ? VM_BROKER : argv[0];
        test.setUp();
        test.test();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ReceiveTest.class);
    }
}
