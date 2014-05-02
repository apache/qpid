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
import org.apache.qpid.qmf2.common.QmfData;

/**
 * Holds the result of a subscription request made by this Console.
 * <p>
 * The SubscriptionId object must be used when the subscription is refreshed or cancelled - it must be passed to the 
 * Console's refreshSubscription() and cancel_subscription() methods. The value of the SubscriptionId does not
 * change over the lifetime of the subscription.
 * <p>
 * The console handle will be provided by the Agent on each data indication event that corresponds to this subscription. 
 * It should not change for the lifetime of the subscription.
 * <p>
 * The getHandle() method returns the reply handle provided to the createSubscription() method call. This handle
 * is merely the handle used for the asynchronous response, it is not associated with the subscription in any other way.
 * <p>
 * Once a subscription is created, the Agent that maintains the subscription will periodically issue updates for the  
 * subscribed data. This update will contain the current values of the subscribed data, and will appear as the first 
 * SUBSCRIPTION_INDICATION WorkItem for this subscription.
 *
 * @author Fraser Adams
 */
public final class SubscribeParams extends QmfData
{
    private String _consoleHandle;

    /**
     * Construct SubscribeParams from a consoleHandle and the Map encoded representation.
     * @param consoleHandle the console handle as passed to the createSubscription() call.
     * @param m a Map containing the Map encoded representation of this SubscribeParams.
     */
    public SubscribeParams(final String consoleHandle, final Map m)
    {
        super(m);
        _consoleHandle = consoleHandle;
    }

    /**
     * If the subscription is successful, this method returns a SubscriptionId object.
     * Should the subscription fail, this method returns null, and getError() can be used to obtain an
     * application-specific QmfData error object.
     *
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
     * Return the time interval in seconds on which the Agent will publish updates for this subscription.
     * @return the time interval in seconds on which the Agent will publish updates for this subscription.
     */
    public long getPublishInterval()
    {
        return getLongValue("_interval");
    }

    /**
     * Return the lifetime in seconds for the subscription.
     * @return the lifetime in seconds for the subscription. The subscription will automatically expire after
     *         this interval if not renewed by the console.
     */
    public long getLifetime()
    {
        return getLongValue("_duration");
    }

    /**
     * Return the QmfData error object if method fails, else null.
     * @return the QmfData error object if method fails, else null.
     */
    public QmfData getError()
    {
        if (getSubscriptionId() == null)
        {
            return this;
        }
        return null;
    }

    /**
     * Sets the consoleHandle.
     * @param consoleHandle the new console handle.
     */
    public void setConsoleHandle(final String consoleHandle)
    {
        _consoleHandle = consoleHandle;
    }

    /**
     * Return the console handle as passed to the createSubscription() call.
     * @return the console handle as passed to the createSubscription() call.
     */
    public String getConsoleHandle()
    {
        return _consoleHandle;
    }
}



