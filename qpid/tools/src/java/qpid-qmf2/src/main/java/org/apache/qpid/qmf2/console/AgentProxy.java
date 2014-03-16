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
package org.apache.qpid.qmf2.console;

// Misc Imports
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfException;

/**
 * This interface is implemented by the Console and provides a number of "Agent" related behaviours.
 * <p>
 * Arguably it would be possible to implement these directly in the org.apache.qpid.qmf2.console.Agent class but as
 * it happens they tend to use more Console behaviours, for example refresh() and invokeMethod() are pretty much
 * about constructing the appropriate JMS Message, so have more in common with the rest of   
 * org.apache.qpid.qmf2.console.Console.
 * <p>
 * The purpose of this interface is primarily about removing a circular dependency between Console and Agent so the
 * Agent doesn't invoke these methods on a Console instance, rather it invokes them on an AgentProxy instance.
 * <p>
 * The following diagram illustrates the interactions between the Console, AgentProxy and the client side Agent
 * representation.
 * <p>
 * <img src="doc-files/Subscriptions.png"/>
 * @author Fraser Adams
 */
public interface AgentProxy
{
    /**
     * Releases the Agent instance. Once called, the console application should not reference this instance again.
     *
     * @param agent the Agent to be destroyed.
     */
    public void destroy(Agent agent);

    /**
     * Request that the Agent update the value of an object's contents.
     *
     * @param agent the Agent to get the refresh from.
     * @param objectId the ObjectId being queried for.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses.
     * @param timeout the maximum time to wait for a response, overrides default replyTimeout.
     * @return the refreshed object.
     */
    public QmfConsoleData refresh(Agent agent, ObjectId objectId, String replyHandle, int timeout);

    /**
     * Invoke the named method on the named Agent.
     *
     * @param agent the Agent to invoke the method on.
     * @param content an unordered set of key/value pairs comprising the method arguments.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses.
     * @param timeout the maximum time to wait for a response, overrides default replyTimeout.
     * @return the MethodResult.
     */
    public MethodResult invokeMethod(Agent agent, Map<String, Object> content, String replyHandle, int timeout) throws QmfException;

    /**
     * Remove a Subscription.
     *
     * @param subscription the SubscriptionManager that we wish to remove.
     */
    public void removeSubscription(SubscriptionManager subscription);
}

