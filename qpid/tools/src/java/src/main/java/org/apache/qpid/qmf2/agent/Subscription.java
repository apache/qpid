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

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.UUID;

// QMF2 Imports
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;

/** 
 * This TimerTask represents a running Subscription on the Agent.
 * <p>
 * The main reason we have Subscriptions as TimerTasks is to enable proper cleanup of the references stored in
 * the _subscriptions Map when the Subscription expires. The timer also causes QmfAgenData that have been updated
 * since the last interval to be published.
 * <p>
 * The following diagram illustrates the Subscription relationships with the Agent and QmfAgentData.
 * <p>
 * <img src="doc-files/Subscriptions.png"/>
 * @author Fraser Adams
 */
public final class Subscription extends TimerTask
{
    private static final Logger _log = LoggerFactory.getLogger(Subscription.class);

    // Duration is the time (in seconds) the Subscription is active before it automatically expires unless refreshed
    private static final int DEFAULT_DURATION = 300;
    private static final int MAX_DURATION = 3600;
    private static final int MIN_DURATION = 10;

    // Interval is the period (in milliseconds) between subscription ubdates.
    private static final int DEFAULT_INTERVAL = 30000;
    private static final int MIN_INTERVAL = 1000;

    private SubscribableAgent _agent;
    private long _startTime = System.currentTimeMillis();
    private long _lastUpdate = _startTime*1000000l;
    private String _subscriptionId;
    private Handle _consoleHandle;
    private QmfQuery _query;
    private long _duration = 0;
    private long _interval = 0;

    /**
     * Tells the SubscribableAgent to send the results to the Console via a subscription indicate message.
     *
     * @param results the list of mapEncoded QmfAgentData that currently match the query associated with this
     * Subscription.
     */
    protected void publish(List<Map> results)
    {
        _agent.sendSubscriptionIndicate(_consoleHandle, results);
        _lastUpdate = System.currentTimeMillis()*1000000l;
    }

    /**
     * Construct a new Subscription.
     * @param agent the SubscribableAgent to which this Subscription is associated.
     * @param params the SubscriptionParams object that contains the information needed to create a Subscription.
     */
    public Subscription(SubscribableAgent agent, SubscriptionParams params) throws QmfException
    {
        _agent = agent;
        _subscriptionId = UUID.randomUUID().toString();
        _consoleHandle = params.getConsoleHandle();
        _query = params.getQuery();
        setDuration(params.getLifetime());
        setInterval(params.getPublishInterval());

        _log.debug("Creating Subscription {}, duration = {}, interval = {}", new Object[] {_subscriptionId, _duration, _interval});
    }

    /**
     * This method gets called periodically by the Timer scheduling this TimerTask.
     * <p>
     * First a check is made to see if the Subscription has expired, if it has then it is cancelled.
     * <p>
     * If the Subscription isn't cancelled the Query gets evaluated against all registered objects and any that match
     * which are new to the Subscription or have changed since the last update get published.
     */
    public void run()
    {
        long elapsed = (long)Math.round((System.currentTimeMillis() - _startTime)/1000.0f);
        if (elapsed >= _duration)
        {
            _log.debug("Subscription {} has expired, removing", _subscriptionId);
            // The Subscription has expired so cancel it
            cancel();
        }
        else
        {
            List<QmfAgentData> objects = _agent.evaluateQuery(_query);
            List<Map> results = new ArrayList<Map>(objects.size());
            for (QmfAgentData object : objects)
            {
                if (object.getSubscription(_subscriptionId) == null)
                {
                    // The object is new to this Subscription so publish it
                    object.addSubscription(_subscriptionId, this);
                    results.add(object.mapEncode());
                }
                else
                {
                    // If the object has had update() called since last Subscription update publish it.
                    // Note that in many cases an Agent might call publish() on a managed object rather than
                    // update() which immediately forces a data indication to be sent to the subscriber on
                    // the Console.
                    if (object.getUpdateTime() > _lastUpdate)
                    {
                        results.add(object.mapEncode());
                    }
                }
            }

            if (results.size() > 0)
            {
                publish(results);
            }
        }
    }

    /**
     * Refresh the subscription by zeroing its elapsed time.
     *
     * @param resubscribeParams the ResubscribeParams passed by the Console potentially containing new duration
     * information.
     */
    public void refresh(ResubscribeParams resubscribeParams)
    {
        _log.debug("Refreshing Subscription {}", _subscriptionId);
        _startTime = System.currentTimeMillis();
        setDuration(resubscribeParams.getLifetime());
    }

    /**
     * Cancel the Subscription, tidying references up and cancelling the TimerTask.
     */
    @Override
    public boolean cancel()
    {
        _log.debug("Cancelling Subscription {}", _subscriptionId);
        // This Subscription is about to be deleted, remove it from any Objects that may be referencing it.
        List<QmfAgentData> objects = _agent.evaluateQuery(_query);
        for (QmfAgentData object : objects)
        {
            object.removeSubscription(_subscriptionId);
        }

        _agent.removeSubscription(this);
        return super.cancel(); // Cancel the TimerTask
    }

    /**
     * Return the SubscriptionId of this subscription.
     * @return the SubscriptionId of this subscription.
     */
    public String getSubscriptionId()
    {
        return _subscriptionId;
    }

    /**
     * Return the consoleHandle of this subscription.
     * @return the consoleHandle of this subscription.
     */
    public Handle getConsoleHandle()
    {
        return _consoleHandle;
    }

    /**
     * Set the Subscription lifetime in seconds. If the value passed to this method is zero the duration gets
     * set to the Agent's DEFAULT_DURATION is the duration has not already been set, if the duration has already
     * been set passing in a zero value has no effect on the duration.
     * If the value passed is non-zero the duration passed gets restricted between the Agent's MIN_DURATION
     * and MAX_DURATION.
     *
     * @param duration the new Subscription lifetime in seconds.
     */
    public void setDuration(long duration)
    {
        if (duration == 0)
        {
            if (_duration == 0)
            {
                _duration = DEFAULT_DURATION;
            } 
            return;
        }
        else
        {
            if (duration > MAX_DURATION)
            {
                duration = MAX_DURATION;
            }
            else if (duration < MIN_DURATION)
            {
                duration = MIN_DURATION;
            }
        }
        _duration = duration;
    }

    /**
     * Return the current Subscription lifetime value in seconds.
     * @return the current Subscription lifetime value in seconds.
     */
    public long getDuration()
    {
        return _duration;
    }

    /**
     * Set the Subscription refresh interval in seconds. If the value passed to this method is zero the interval gets
     * set to the Agent's DEFAULT_INTERVAL otherwise the interval passed gets restricted to be >= the Agent's
     * MIN_INTERVAL.
     *
     * @param interval the time (in milliseconds) between periodic updates of data in this Subscription. 
     */
    public void setInterval(long interval)
    {
        if (interval == 0)
        {
            interval = DEFAULT_INTERVAL;
        }
        else if (interval < MIN_INTERVAL)
        {
            interval = MIN_INTERVAL;
        }
        _interval = interval;
    }

    /**
     * Return The time (in milliseconds) between periodic updates of data in this Subscription. 
     * @return The time (in milliseconds) between periodic updates of data in this Subscription. 
     */
    public long getInterval()
    {
        return _interval;
    }

    /**
     * Return The Subscription's QmfQuery.
     * @return The Subscription's QmfQuery.
     */
    public QmfQuery getQuery()
    {
        return _query;
    }
}

