/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.test.unit.basic.close;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

public class CloseTests extends TestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(CloseTests.class);

    private static final String BROKER = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();

        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.setUp();

        TransportConnection.killVMBroker(1);
    }

    public void testCloseQueueReceiver() throws AMQException, URLSyntaxException, JMSException
    {
        AMQConnection connection = new AMQConnection(BROKER, "guest", "guest", this.getName(), "test");

        Session session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        AMQQueue queue = new AMQQueue(new AMQBindingURL("test-queue"));
        MessageConsumer consumer = session.createConsumer(queue);

        MessageProducer producer_not_used_but_created_for_testing = session.createProducer(queue);

        connection.start();

        _logger.info("About to close consumer");

        consumer.close();

        _logger.info("Closed Consumer");

    }
}
