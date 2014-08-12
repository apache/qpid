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
package org.apache.qpid.ra.admin;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class QpidConnectionFactoryProxyTest extends QpidBrokerTestCase
{
    private static final String BROKER_PORT = "15672";

    private static final String URL = "amqp://guest:guest@client/test?brokerlist='tcp://localhost:" + BROKER_PORT + "?sasl_mechs='PLAIN%2520CRAM-MD5''";

    public void testQueueConnectionFactory() throws Exception
    {
        QueueConnectionFactory cf = null;
        QueueConnection c = null;

        try
        {
            cf = new QpidConnectionFactoryProxy();
            ((QpidConnectionFactoryProxy)cf).setConnectionURL(URL);
            c = cf.createQueueConnection();
            assertTrue(c instanceof QueueConnection);

        }
        finally
        {
            if(c != null)
            {
                c.close();
            }
        }
    }

    public void testTopicConnectionFactory() throws Exception
    {
        TopicConnectionFactory cf = null;
        TopicConnection c = null;

        try
        {
            cf = new QpidConnectionFactoryProxy();
            ((QpidConnectionFactoryProxy)cf).setConnectionURL(URL);
            c = cf.createTopicConnection();
            assertTrue(c instanceof TopicConnection);

        }
        finally
        {
            if(c != null)
            {
                c.close();
            }
        }
        try
        {

        }
        finally
        {

        }
    }

    public void testConnectionFactory() throws Exception
    {
        ConnectionFactory cf = null;
        Connection c = null;

        try
        {
            cf = new QpidConnectionFactoryProxy();
            ((QpidConnectionFactoryProxy)cf).setConnectionURL(URL);
            c = cf.createConnection();
            assertTrue(c instanceof Connection);

        }
        finally
        {
            if(c != null)
            {
                c.close();
            }

        }
    }
}

