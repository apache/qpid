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

import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.WorkItem;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <pre>
 * SUBSCRIBE_RESPONSE: The SUBSCRIBE_RESPONSE WorkItem returns the result of a subscription request made by
 *                     this Console.  This WorkItem is generated when the Console's createSubscription() is
 *                     called in an asychronous manner, rather than pending for the result. 
 *
 *                     The getParams() method of a SUBSCRIBE_RESPONSE  WorkItem will return an instance of the
 *                     SubscribeParams class.
 *
 *                     The SubscriptionId object must be used when the subscription is refreshed or cancelled.
 *                     It must be passed to the Console's refresh_subscription() and cancelSubscription() methods.
 *                     The value of the SubscriptionId does not change over the lifetime of the subscription.
 *
 *                     The console handle will be provided by the Agent on each data indication event that
 *                     corresponds to this subscription.  It should not change for the lifetime of the subscription.
 *
 *                     The getHandle() method returns the reply handle provided to the createSubscription()
 *                     method call.  This handle is merely the handle used for the asynchronous response, it is
 *                     not associated with the subscription in any other way.
 *
 *                     Once a subscription is created, the Agent that maintains the subscription will periodically
 *                     issue updates for the subscribed data.  This update will contain the current values of the   
 *                     subscribed data, and will appear as the first SUBSCRIPTION_INDICATION WorkItem for this
 *                     subscription.
 * </pre>
 * @author Fraser Adams
 */

public final class SubscribeResponseWorkItem extends WorkItem
{
    /**
     * Construct a SubscribeResponseWorkItem. Convenience constructor not in API
     *
     * @param handle the reply handle used to associate requests and responses
     * @param params the SubscribeParams used to populate the WorkItem's param
     */
    public SubscribeResponseWorkItem(final Handle handle, final SubscribeParams params)
    {
        super(WorkItemType.SUBSCRIBE_RESPONSE, handle, params);
    }

    /**
     * Return the SubscribeParams stored in the params.
     * @return the SubscribeParams stored in the params.
     */
    public SubscribeParams getSubscribeParams()
    {
        return (SubscribeParams)getParams();
    }
}

