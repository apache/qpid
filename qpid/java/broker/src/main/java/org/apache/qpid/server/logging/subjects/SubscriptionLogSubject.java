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

import org.apache.qpid.server.subscription.Subscription;

public class SubscriptionLogSubject extends AbstractLogSubject
{

    /**
     * LOG FORMAT for the SubscriptionLogSubject,
     * Uses a MessageFormat call to insert the required values according to
     * these indices:
     *
     * 0 - Subscription ID
     * 1 - queue name
     */
    public static String SUBSCRIPTION_FORMAT = "sub:{0}(vh(/{1})/qu({2}))";

    /**
     * Create an QueueLogSubject that Logs in the following format.
     *
     * @param subscription
     */
    public SubscriptionLogSubject(Subscription subscription)
    {
        setLogStringWithFormat(SUBSCRIPTION_FORMAT, subscription.getSubscriptionID(),
                               subscription.getQueue().getVirtualHost().getName(),
                               subscription.getQueue().getName());
    }
}
