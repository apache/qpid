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
package org.apache.qpid.server.logging.subjects;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.flow.LimitlessCreditManager;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MockAMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionFactory;
import org.apache.qpid.server.subscription.SubscriptionFactoryImpl;
import org.apache.qpid.server.virtualhost.VirtualHost;

/**
 * Validate SubscriptionLogSubjects are logged as expected
 */
public class SubscriptionLogSubjectTest extends AbstractTestLogSubject
{

    AMQQueue _queue;
    VirtualHost _testVhost;
    private int _channelID = 1;
    Subscription _subscription;

    public void setUp() throws Exception
    {
        super.setUp();

        _testVhost = ApplicationRegistry.getInstance().getVirtualHostRegistry().
                getVirtualHost("test");

        _queue = new MockAMQQueue("SubscriptionLogSubjectTest");
        ((MockAMQQueue) _queue).setVirtualHost(_testVhost);

        AMQChannel channel = new AMQChannel(_session, _channelID, _session.getVirtualHost().getMessageStore());

        _session.addChannel(channel);

        SubscriptionFactory factory = new SubscriptionFactoryImpl();

        _subscription = factory.createSubscription(_channelID, _session, new AMQShortString("cTag"),
                                                   false, null, false,
                                                   new LimitlessCreditManager());

        _subscription.setQueue(_queue, false);

        _subject = new SubscriptionLogSubject(_subscription);
    }

    /**
     * Validate that the logged Subject  message is as expected:
     * MESSAGE [Blank][sub:0(qu(SubscriptionLogSubjectTest))] <Log Message>
     *
     * @param message the message whos format needs validation
     */
    @Override
    protected void validateLogStatement(String message)
    {
        String subscriptionSlice = getSlice("sub:"
                                            + _subscription.getSubscriptionID(),
                                            message);

        assertNotNull("Unable to locate subscription 'sub:" +
                      _subscription.getSubscriptionID() + "'");

        // Adding the ')' is a bit ugly but SubscriptionLogSubject is the only
        // Subject that nests () and so the simple parser of checking for the
        // next ')' falls down.
        verifyQueue(subscriptionSlice + ")", _queue);
    }
}
