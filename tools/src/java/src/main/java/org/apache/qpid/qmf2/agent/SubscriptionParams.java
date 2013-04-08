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
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfQuery;

/**
 * Holds the information contained in a subscription request made by a Console to an Agent
 *
 * @author Fraser Adams
 */
public final class SubscriptionParams extends QmfData
{
    private final Handle _consoleHandle;

    /**
     * Construct SubscriptionParams.
     *
     * @param handle the handle that the console uses to identify this subscription.
     * @param m the Map used to populate the SubscriptionParams state.
     */
    public SubscriptionParams(final Handle handle, final Map m)
    {
        super(m);
        _consoleHandle = handle;
    }

    /**
     * Return the handle that the console uses to identify this subscription.
     * @return the handle that the console uses to identify this subscription.
     * <p>
     * This handle must be passed along with every published update from the Agent.
     */
    public Handle getConsoleHandle()
    {
        return _consoleHandle;
    }

    /**
     * Return the QmfQuery object associated with the SubscriptionParams.
     * @return the QmfQuery object associated with the SubscriptionParams.
     */
    public QmfQuery getQuery() throws QmfException
    {
        return new QmfQuery((Map)getValue("_query"));
    }

    /**
     * Return the requested time interval in seconds for updates.
     * @return the requested time interval in seconds for updates. Zero if the Agent's default interval should be used.
     */
    public long getPublishInterval()
    {
        return getLongValue("_interval");
    }

    /**
     * Return the requested lifetime for the subscription.
     * @return the requested lifetime for the subscription. Zero if the Agent's default subscription lifetime
     *         should be used.
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



