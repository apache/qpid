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
import org.apache.qpid.qmf2.common.QmfData;

/**
 * Holds the information contained in a resubscription request made by a Console to an Agent
 *
 * @author Fraser Adams
 */
public final class ResubscribeParams extends QmfData
{
    /**
     * Construct ResubscribeParams.
     *
     * @param m the Map used to populate the ResubscribeParams state.
     */
    public ResubscribeParams(final Map m)
    {
        super(m);
    }

    /**
     * Return a SubscriptionId object.
     * @return a SubscriptionId object.
     */
    public String getSubscriptionId()
    {
        if (hasValue("_subscription_id"))
        {
            return getStringValue("_subscription_id");
        }
        return null;
    }

    /**
     * Return the requested lifetime for the subscription.
     * @return the requested lifetime for the subscription. Zero if the previous interval should be used.
     */
    public long getLifetime()
    {
        return getLongValue("_duration");
    }

    /**
     * Return authenticated user id of caller if present, else null.
     * @return authenticated user id of caller if present, else null.
     */
    public String getUserId()
    {
        return getStringValue("_user_id");
    }
}



