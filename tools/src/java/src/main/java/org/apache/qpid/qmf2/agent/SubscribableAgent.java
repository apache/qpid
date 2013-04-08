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
package org.apache.qpid.qmf2.agent;

// Misc Imports
import java.util.List;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.QmfQuery;

/**
 * This interface provides a number of methods that are called by a Subscription in order to interact with an
 * Agent or an Agent's managed data.
 * <p>
 * The purpose of this interface is primarily about removing a circular dependency between Subscription and Agent
 * so the Subscription doesn't invoke these methods on an Agent instance, rather it invokes them on a
 * SubscribeableAgent instance.
 * <p>
 * The following diagram illustrates the interactions between the Agent, Subscription and SubscribableAgent.
 * <p>
 * <img src="doc-files/Subscriptions.png"/>
 *
 * @author Fraser Adams
 */
public interface SubscribableAgent
{
    /**
     * Send a list of updated subscribed data to the Console.
     *
     * @param handle the console reply handle
     * @param results a list of subscribed data in Map encoded form
     */
    public void sendSubscriptionIndicate(Handle handle, List<Map> results);

    /**
     * This method evaluates a QmfQuery over the Agent's data on behalf of a Subscription
     *
     * @param query the QmfQuery that the Subscription wants to be evaluated over the Agent's data
     * @return a List of QmfAgentData objects that match the specified QmfQuery
     */
    public List<QmfAgentData> evaluateQuery(QmfQuery query);

    /**
     * This method is called by the Subscription to tell the SubscriberProxy that the Subscription has been cancelled.
     *
     * @param subscription the Subscription that has been cancelled and is requesting removal.
     */
    public void removeSubscription(Subscription subscription);
}

