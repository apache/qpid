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
 * RESUBSCRIBE_REQUEST: The RESUBSCRIBE_REQUEST is sent by a Console to renew an existing subscription.  The Console may 
 *                      request a new duration for the subscription, otherwise the previous lifetime interval is repeated.
 *
 *                      The getParams() method of a RESUBSCRIBE_REQUEST WorkItem will return an instance of the 
 *                      ResubscribeParams class.
 *
 *                      The getHandle() WorkItem method returns the reply handle which should be passed to the Agent's 
 *                      subscriptionResponse() method.
 * </pre>
 * @author Fraser Adams
 */

public final class ResubscribeRequestWorkItem extends WorkItem
{
    /**
     * Construct a ResubscribeRequestWorkItem. Convenience constructor not in API.
     *
     * @param handle the reply handle.
     * @param params the ResubscribeParams used to populate the WorkItem's param.
     */
    public ResubscribeRequestWorkItem(final Handle handle, final ResubscribeParams params)
    {
        super(WorkItemType.RESUBSCRIBE_REQUEST, handle, params);
    }

    /**
     * Return the ResubscribeParams stored in the params Map.
     * @return the ResubscribeParams stored in the params Map.
     */
    public ResubscribeParams getResubscribeParams()
    {
        return (ResubscribeParams)getParams();
    }
}

