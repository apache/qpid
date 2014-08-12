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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Session;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.disttest.controller.config.QueueConfig;
import org.apache.qpid.disttest.jms.QpidQueueCreator;

public class QpidQueueCreatorTest extends DistributedTestSystemTestBase
{
    private static final Map<String, Object> EMPTY_ATTRIBUTES = Collections.emptyMap();

    private static final boolean QUEUE_DURABILITY = true;

    private Connection _connection;
    private QpidQueueCreator _creator;
    private Session _session;
    private List<QueueConfig> _configs;
    private String _queueName;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _connection = getConnection();
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _creator = new QpidQueueCreator();
        _configs = new ArrayList<QueueConfig>();
        _queueName = "direct://amq.direct//" + getTestQueueName() + "?durable='" + QUEUE_DURABILITY + "'";
    }

    public void testCreateQueueWithoutAttributes() throws Exception
    {
        _configs.add(new QueueConfig(_queueName, QUEUE_DURABILITY, EMPTY_ATTRIBUTES));

        assertQueueBound(_queueName, false);

        _creator.createQueues(_connection, _session, _configs);

        assertQueueBound(_queueName, true);
    }

    public void testCreateWithAttributes() throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("x-qpid-priorities", Integer.valueOf(5));
        _configs.add(new QueueConfig(_queueName, QUEUE_DURABILITY, attributes));

        assertQueueBound(_queueName, false);

        _creator.createQueues(_connection, _session, _configs);

        assertQueueBound(_queueName, true);
    }

    public void testDeleteQueues() throws Exception
    {
        _configs.add(new QueueConfig(_queueName, QUEUE_DURABILITY, EMPTY_ATTRIBUTES));

        assertQueueBound(_queueName, false);

        _creator.createQueues(_connection, _session, _configs);
        assertQueueBound(_queueName, true);

        _creator.deleteQueues(_connection, _session, _configs);
        assertQueueBound(_queueName, false);
    }

    private void assertQueueBound(String queueName, boolean isBound) throws Exception
    {
        AMQDestination destination = (AMQDestination)_session.createQueue(queueName);
        assertEquals("Queue is not in expected bound state", isBound, ((AMQSession<?, ?>)_session).isQueueBound(destination));
    }
}
