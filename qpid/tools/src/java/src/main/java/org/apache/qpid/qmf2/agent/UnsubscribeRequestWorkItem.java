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
 * UNSUBSCRIBE_REQUEST: The UNSUBSCRIBE_REQUEST is sent by a Console to terminate an existing subscription. The Agent 
 *                      application should terminate the given subscription if it exists, and cancel sending any further 
 *                      updates against it.
 *
 *                      The getParams() method of a UNSUBSCRIBE_REQUEST WorkItem will return a String holding the 
 *                      subscriptionId
 *
 *                      The getHandle() method returns null.
 * </pre>
 * @author Fraser Adams
 */

public final class UnsubscribeRequestWorkItem extends WorkItem
{
    /**
     * Construct an UnsubscribeRequestWorkItem. Convenience constructor not in API
     *
     * @param params the ResubscribeParams used to populate the WorkItem's param
     */
    public UnsubscribeRequestWorkItem(final String params)
    {
        super(WorkItemType.UNSUBSCRIBE_REQUEST, null, params);
    }

    /**
     * Return the subscriptionId String stored in the params Map.
     * @return the subscriptionId String stored in the params Map.
     */
    public String getSubscriptionId()
    {
        return (String)getParams();
    }
}

