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
package org.apache.qpid.systest.disttest;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class DistributedTestSystemTestBase extends QpidBrokerTestCase
{
    protected Context _context;

    protected Connection _connection;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        final Properties properties = new Properties();
        properties.load(DistributedTestSystemTestBase.class.getResourceAsStream("perftests.systests.properties"));
        _context = new InitialContext(properties);

        _connection = getConnection();
        _connection.start();
    }

    @Override
    protected void tearDown() throws Exception
    {
        // no need to close connections - this is done by superclass

        super.tearDown();
    }

    public Context getContext()
    {
        return _context;
    }

    @Override
    public Connection getConnection() throws JMSException, NamingException
    {
        final ConnectionFactory connectionFactory = (ConnectionFactory) _context.lookup("connectionfactory");
        final Connection connection = connectionFactory.createConnection();
        _connections.add(connection);
        return connection;
    }
}
