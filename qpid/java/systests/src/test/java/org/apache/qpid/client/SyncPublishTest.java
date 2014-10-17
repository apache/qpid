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
package org.apache.qpid.client;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class SyncPublishTest extends QpidBrokerTestCase
{
    private Connection _connection;

    @Override
    public void setUp() throws Exception
    {

        super.setUp();
        Map<String, String> options = new HashMap<>();
        options.put(ConnectionURL.OPTIONS_SYNC_PUBLISH, "all");
        _connection = getConnectionWithOptions(options);
    }

    @Override
    public void tearDown() throws Exception
    {
        _connection.close();
        super.tearDown();
    }

    public void testAnonPublisherUnknownDestination() throws Exception
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(null);
        try
        {
            producer.send(session.createQueue("direct://amq.direct/unknown/unknown"),session.createTextMessage("hello"));
            fail("Send to unknown destination should result in error");
        }
        catch (JMSException e)
        {
            // pass
        }
    }


    public void testAnonPublisherUnknownDestinationTransactional() throws Exception
    {
        Session session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = session.createProducer(null);
        try
        {
            producer.send(session.createQueue("direct://amq.direct/unknown/unknown"),session.createTextMessage("hello"));
            fail("Send to unknown destination should result in error");
        }
        catch (JMSException e)
        {
            // pass
        }
        try
        {
            session.commit();
        }
        catch (JMSException e)
        {
            fail("session should commit successfully even though the message was not sent");
        }

    }

    public void testQueueRemovedAfterConsumerCreated() throws JMSException
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        TemporaryQueue queue = session.createTemporaryQueue();
        MessageProducer producer = session.createProducer(queue);
        try
        {
            producer.send(session.createTextMessage("hello"));
        }
        catch (JMSException e)
        {
            fail("Send to temporary queue should succeed");
        }

        try
        {
            queue.delete();
        }
        catch (JMSException e)
        {
            fail("temporary queue should be deletable");
        }

        try
        {
            producer.send(session.createTextMessage("hello"));
            fail("Send to deleted temporary queue should not succeed");
        }
        catch (JMSException e)
        {
            // pass
        }


    }
}
