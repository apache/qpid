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
package org.apache.qpid.server.virtualhost.plugins.policies;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MockAMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class TopicDeletePolicyTest extends InternalBrokerBaseCase
{

    TopicDeletePolicyConfiguration _config;

    VirtualHost _defaultVhost;
    InternalTestProtocolSession _connection;

    public void setUp() throws Exception
    {
        super.setUp();

        _defaultVhost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getDefaultVirtualHost();

        _connection = new InternalTestProtocolSession(_defaultVhost);

        _config = new TopicDeletePolicyConfiguration();

        XMLConfiguration config = new XMLConfiguration();

        _config.setConfiguration("", config);
    }

    private MockAMQQueue createOwnedQueue()
    {
        MockAMQQueue queue = new MockAMQQueue("testQueue");

        _defaultVhost.getQueueRegistry().registerQueue(queue);

        try
        {
            AMQChannel channel = new AMQChannel(_connection, 0, null);
            _connection.addChannel(channel);

            queue.setExclusiveOwningSession(channel);
        }
        catch (AMQException e)
        {
            fail("Unable to create Channel:" + e.getMessage());
        }

        return queue;
    }

    private void setQueueToAutoDelete(final AMQQueue queue)
    {
        ((MockAMQQueue) queue).setAutoDelete(true);

        queue.setDeleteOnNoConsumers(true);
        final AMQProtocolSession.Task deleteQueueTask =
                new AMQProtocolSession.Task()
                {
                    public void doTask(AMQProtocolSession session) throws AMQException
                    {
                        queue.delete();
                    }
                };

        ((AMQChannel) queue.getExclusiveOwningSession()).getProtocolSession().addSessionCloseTask(deleteQueueTask);
    }

    /** Check that a null queue passed in does not upset the policy. */
    public void testNullQueueParameter() throws ConfigurationException
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        try
        {
            policy.performPolicy(null);
        }
        catch (Exception e)
        {
            fail("Exception should not be thrown:" + e.getMessage());
        }

    }

    /**
     * Set a owning Session to null which means this is not an exclusive queue
     * so the queue should not be deleted
     */
    public void testNonExclusiveQueue()
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        MockAMQQueue queue = createOwnedQueue();

        queue.setExclusiveOwningSession(null);

        policy.performPolicy(queue);

        assertFalse("Queue should not be deleted", queue.isDeleted());
        assertFalse("Connection should not be closed", _connection.isClosed());
    }

    /**
     * Test that exclusive JMS Queues are not deleted.
     * Bind the queue to the direct exchange (so it is a JMS Queue).
     *
     * JMS Queues are not to be processed so this should not delete the queue.
     */
    public void testQueuesAreNotProcessed()
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        MockAMQQueue queue = createOwnedQueue();

        queue.addBinding(new Binding(null, "bindingKey", queue, new DirectExchange(), null));

        policy.performPolicy(queue);

        assertFalse("Queue should not be deleted", queue.isDeleted());
        assertFalse("Connection should not be closed", _connection.isClosed());
    }

    /**
     * Give a non auto-delete queue is bound to the topic exchange the
     * TopicDeletePolicy will close the connection and delete the queue,
     */
    public void testNonAutoDeleteTopicIsNotClosed()
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        MockAMQQueue queue = createOwnedQueue();

        queue.addBinding(new Binding(null, "bindingKey", queue, new TopicExchange(), null));

        queue.setAutoDelete(false);

        policy.performPolicy(queue);

        assertFalse("Queue should not be deleted", queue.isDeleted());
        assertTrue("Connection should be closed", _connection.isClosed());
    }

    /**
     * Give a auto-delete queue bound to the topic exchange the TopicDeletePolicy will
     * close the connection and delete the queue
     */
    public void testTopicIsClosed()
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        final MockAMQQueue queue = createOwnedQueue();

        queue.addBinding(new Binding(null, "bindingKey", queue, new TopicExchange(), null));

        setQueueToAutoDelete(queue);

        policy.performPolicy(queue);

        assertTrue("Queue should be deleted", queue.isDeleted());
        assertTrue("Connection should be closed", _connection.isClosed());
    }

    /**
     * Give a queue bound to the topic exchange the TopicDeletePolicy will
     * close the connection and NOT delete the queue
     */
    public void testNonAutoDeleteTopicIsClosedNotDeleted()
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        MockAMQQueue queue = createOwnedQueue();

        queue.addBinding(new Binding(null, "bindingKey", queue, new TopicExchange(), null));

        policy.performPolicy(queue);

        assertFalse("Queue should not be deleted", queue.isDeleted());
        assertTrue("Connection should be closed", _connection.isClosed());
    }

    /**
     * Give a queue bound to the topic exchange the TopicDeletePolicy suitably
     * configured with the delete-persistent tag will close the connection
     * and delete the queue
     */
    public void testPersistentTopicIsClosedAndDeleted()
    {
        //Set the config to delete persistent queues
        _config.getConfig().addProperty("delete-persistent", "");

        TopicDeletePolicy policy = new TopicDeletePolicy();
        policy.configure(_config);

        assertTrue("Config was not updated to delete Persistent topics",
                   _config.deletePersistent());

        MockAMQQueue queue = createOwnedQueue();

        queue.addBinding(new Binding(null, "bindingKey", queue, new TopicExchange(), null));

        policy.performPolicy(queue);

        assertTrue("Queue should be deleted", queue.isDeleted());
        assertTrue("Connection should be closed", _connection.isClosed());
    }

    /**
     * Give a queue bound to the topic exchange the TopicDeletePolicy not
     * configured to close a persistent queue
     */
    public void testPersistentTopicIsClosedAndDeletedNullConfig()
    {
        TopicDeletePolicy policy = new TopicDeletePolicy();
        // Explicity say we are not configuring the policy.
        policy.configure(null);

        MockAMQQueue queue = createOwnedQueue();

        queue.addBinding(new Binding(null, "bindingKey", queue, new TopicExchange(), null));

        policy.performPolicy(queue);

        assertFalse("Queue should not be deleted", queue.isDeleted());
        assertTrue("Connection should be closed", _connection.isClosed());
    }

    public void testNonExclusiveQueueNullConfig()
    {
        _config = null;
        testNonExclusiveQueue();
    }

    public void testQueuesAreNotProcessedNullConfig()
    {
        _config = null;
        testQueuesAreNotProcessed();
    }

    public void testNonAutoDeleteTopicIsNotClosedNullConfig()
    {
        _config = null;
        testNonAutoDeleteTopicIsNotClosed();
    }

    public void testTopicIsClosedNullConfig()
    {
        _config = null;
        testTopicIsClosed();
    }

    public void testNonAutoDeleteTopicIsClosedNotDeletedNullConfig() throws AMQException
    {
        _config = null;
        testNonAutoDeleteTopicIsClosedNotDeleted();
    }

}
