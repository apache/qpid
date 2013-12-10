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
 * SUBSCRIPTION_INDICATION: The SUBSCRIPTION_INDICATION WorkItem signals the arrival of an update to subscribed
 *                          data from the Agent. 
 *
 *                          The getParams() method of a SUBSCRIPTION_INDICATION  WorkItem will return an instance
 *                          of the SubscribeIndication class. The getHandle() method returns null.
 * </pre>
 * @author Fraser Adams
 */

public final class SubscriptionIndicationWorkItem extends WorkItem
{
    /**
     * Construct a SubscriptionIndicationWorkItem. Convenience constructor not in API
     *
     * @param params the SubscribeParams used to populate the WorkItem's param
     */
    public SubscriptionIndicationWorkItem(final SubscribeIndication params)
    {
        super(WorkItemType.SUBSCRIPTION_INDICATION, null, params);
    }

    /**
     * Return the SubscribeIndication stored in the params.
     * @return the SubscribeIndication stored in the params.
     */
    public SubscribeIndication getSubscribeIndication()
    {
        return (SubscribeIndication)getParams();
    }
}

