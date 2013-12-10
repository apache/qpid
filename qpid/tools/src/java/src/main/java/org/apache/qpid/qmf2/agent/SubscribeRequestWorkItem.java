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

import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.WorkItem;

/**
 * Descriptions below are taken from <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API Proposal</a> 
 * <pre>
 * SUBSCRIBE_REQUEST: The SUBSCRIBE_REQUEST WorkItem provides a query that the agent application must periodically
 *                    publish until the subscription is cancelled or expires. On receipt of this WorkItem, the
 *                    application should call the Agent subscriptionResponse() method to acknowledge the request.
 *                    On each publish interval, the application should call Agent subscriptionIndicate(), passing a
 *                    list of the objects that satisfy the query. The subscription remains in effect until an   
 *                    UNSUBSCRIBE_REQUEST WorkItem for the subscription is received, or the subscription expires.
 *
 *                    The getParams() method of a QUERY WorkItem will return an instance of the SubscriptionParams class.
 *
 *                    The getHandle() WorkItem method returns the reply handle which should be passed to the Agent's 
 *                    subscriptionResponse() method.
 * </pre>
 * @author Fraser Adams
 */

public final class SubscribeRequestWorkItem extends WorkItem
{
    /**
     * Construct a SubscribeRequestWorkItem. Convenience constructor not in API
     *
     * @param handle the reply handle
     * @param params the SubscriptionParams used to populate the WorkItem's param
     */
    public SubscribeRequestWorkItem(final Handle handle, final SubscriptionParams params)
    {
        super(WorkItemType.SUBSCRIBE_REQUEST, handle, params);
    }

    /**
     * Return the SubscriptionParams stored in the params Map.
     * @return the SubscriptionParams stored in the params Map.
     */
    public SubscriptionParams getSubscriptionParams()
    {
        return (SubscriptionParams)getParams();
    }
}

