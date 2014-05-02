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

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Misc Imports
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

// QMF2 Imports
import org.apache.qpid.qmf2.common.QmfQuery;

/** 
 * A SubscriptionManager represents a running Subscription on the Console.
 * <p>
 * The main reason we have SubscriptionManagers as TimerTasks is to enable proper cleanup of the references stored in
 * the subscriptionByHandle and subscriptionById Maps. Ideally these will be cleaned up by a client calling 
 * cancelSubscription but we can't rely on that as the client may forget or the Agent may not respond.
 * <p>
 * The SubscriptionManager acts like a client/Console side representation of a Subscription running on an Agent.
 * As mentioned above its primary purpose is to enable references to Subscriptions maintained by the Console to
 * be cleaned up should the Subscription time out rather than being cancelled, however as a side effect it is
 * used to enable emulation of Subscriptions to the broker ManagementAgent, which does not yet natively implement
 * Subscription.
 * <p>
 * To emulate Subscriptions the Console receives the periodic _data indications pushed by the ManagementAgent. The
 * Console then iterates through Subscriptions referencing the broker Agent and evaluates their queries against
 * the QmfConsoleData returned by the _data indication. Any QmfConsoleData that match the query are passed to the
 * client application with the consoleHandle of the matching Subscription.
 * <p>
 * The following diagram illustrates the Subscription relationships with the Console and local Agent proxy.
 * <p>
 * <img src="doc-files/Subscriptions.png"/>
 *
 * @author Fraser Adams
 */
public final class SubscriptionManager extends TimerTask
{
    private static final Logger _log = LoggerFactory.getLogger(SubscriptionManager.class);

    private final Agent _agent;
    private long     _startTime = System.currentTimeMillis();
    private String   _subscriptionId;
    private String   _consoleHandle;
    private String   _replyHandle;
    private QmfQuery _query;
    private long     _duration = 0;
    private long     _interval = 0;
    private boolean  _waiting = true;

    /**
     * Construct a Console side proxy of a Subscription. Primarily to manage references to the Subscription.
     *
     * @param agent the Agent from which the Subscription has been requested
     * @param query the QmfQuery that the Subscription will run
     * @param consoleHandle the handle that uniquely identifies the Subscription
     * @param interval the interval between subscription updates
     * @param duration the duration of the subscription (assuming it doesn't get refreshed)
     */
    SubscriptionManager(final Agent agent, final QmfQuery query, final String consoleHandle,
                        final String replyHandle, final long interval, final long duration)
    {
        _agent = agent;
        _query = query;
        _consoleHandle = consoleHandle;
        _replyHandle = replyHandle;
        _interval = interval;
        _duration = duration;
        _log.debug("Creating SubscriptionManager {}, on Agent {}",_consoleHandle, _agent.getName());
    }

    /**
     * This method gets called periodically by the Timer scheduling this TimerTask.
     * <p>
     * First a check is made to see if the Subscription has expired, if it has then it is cancelled.
     */
    public void run()
    {
        long elapsed = (long)Math.round((System.currentTimeMillis() - _startTime)/1000.0f);
        if (elapsed >= _duration || !_agent.isActive())
        {
            _log.debug("Subscription {} has expired, removing", _subscriptionId);
            // The Subscription has expired so cancel it
            cancel();
        }
    }

    /**
     * Causes the current thread to wait until it is signalled or times out.
     * <p>
     * This method is primarily used as a means to enable a synchronous call to createSubscription().
     * For most synchronous calls we simply use the receive() call on the synchronous session, but we can't do that
     * for createSubscription() as we specifically need to use the replyTo on the asynchronous session as once
     * subscriptions are created the results are asynchronously pushed. This means we have to get the response to 
     * createSession() on the asynchronous replyTo then signal the (blocked) main thread that the response has
     * been received.
     *
     * @param timeout the maximum time to wait to be signalled.
     */
    public synchronized void await(final long timeout)
    {
        while (_waiting)
        {
            long _startTime = System.currentTimeMillis();
            try
            {
                wait(timeout);
            }
            catch (InterruptedException ie)
            {
                continue;
            }
            // Measure elapsed time to test against spurious wakeups and ensure we really have timed out
            long elapsedTime = (System.currentTimeMillis() - _startTime);
            if (elapsedTime >= timeout)
            {
                break;
            }
        }
        _waiting = true;
    }

    /**
     * Wakes up all waiting threads.
     */
    public synchronized void signal()
    {
        _waiting = false;
        notifyAll();
    }

    /**
     * Refresh the subscription by zeroing its elapsed time.
     */
    public void refresh()
    {
        _log.debug("Refreshing Subscription {}", _subscriptionId);
        _startTime = System.currentTimeMillis();
    }

    /**
     * Cancel the Subscription, tidying references up and cancelling the TimerTask.
     */
    @Override
    public boolean cancel()
    {
        _log.debug("Cancelling Subscription {}, {}", _consoleHandle, _subscriptionId);
        _agent.removeSubscription(this);
        signal(); // Just in case anything is blocking on this Subscription.
        return super.cancel(); // Cancel the TimerTask
    }

    /**
     * Set the SubscriptionId.
     * @param subscriptionId the new SubscriptionId of this Subscription.
     */
    public void setSubscriptionId(final String subscriptionId)
    {
        _subscriptionId = subscriptionId;
    }

    /**
     * return the SubscriptionId of this Subscription.
     * @return the SubscriptionId of this Subscription.
     */
    public String getSubscriptionId()
    {
        return _subscriptionId;
    }

    /**
     * Return the consoleHandle of this Subscription.
     * @return the consoleHandle of this Subscription.
     */
    public String getConsoleHandle()
    {
        return _consoleHandle;
    }

    /**
     * Return the replyHandle of this Subscription.
     * @return the replyHandle of this Subscription.
     */
    public String getReplyHandle()
    {
        return _replyHandle;
    }

    /**
     * Return the Agent running this Subscription.
     * @return the Agent running this Subscription.
     */
    public Agent getAgent()
    {
        return _agent;
    }

    /**
     * Set the Subscription lifetime in seconds.
     *
     * @param duration the new Subscription lifetime in seconds
     */
    public void setDuration(final long duration)
    {
        _duration = duration;
    }

    /**
     * Return The Subscription's QmfQuery.
     * @return The Subscription's QmfQuery.
     */
    public QmfQuery getQuery()
    {
        return _query;
    }

    /**
     * Create a Map encoded version.
     * <p>
     * When we do a synchronous createSubscription the Subscription itself holds the info needed to populate
     * the SubscriptionParams result. We encode the info in a Map to pass to the SubscribeParams Constructor
     */
    public Map<String, Object> mapEncode()
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_interval", _interval);
        map.put("_duration", _duration);
        map.put("_subscription_id", _subscriptionId);
        return map;
    }
}


