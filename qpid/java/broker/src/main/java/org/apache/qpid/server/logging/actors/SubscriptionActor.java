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
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.logging.subjects.SubscriptionLogSubject;
import org.apache.qpid.server.subscription.Subscription;

import java.text.MessageFormat;

/**
 * The subscription actor provides formatted logging for actions that are
 * performed by the subsciption. Such as SUB-1003 state changes.
 */
public class SubscriptionActor extends AbstractActor
{
    public static String SUBSCRIBER_FORMAT = "sub:{0}(vh({1})/qu({2}))";
    private final String _logString;

    public SubscriptionActor(RootMessageLogger logger, Subscription subscription)
    {
        super(logger);

        _logString = "[" + MessageFormat.format(SubscriptionLogSubject.SUBSCRIPTION_FORMAT,
                                                subscription.getSubscriptionID(),
                                                subscription.getQueue().getVirtualHost().getName(),
                                                subscription.getQueue().getName())
                     + "] ";
    }

    public String getLogMessage()
    {
        return _logString;
    }
}
