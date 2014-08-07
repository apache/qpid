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
package org.apache.qpid.server.queue;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.QueueNotificationListener;


public enum NotificationCheck
{

    MESSAGE_COUNT_ALERT
    {
        public boolean notifyIfNecessary(ServerMessage<?> msg, AMQQueue queue, QueueNotificationListener listener)
        {
            int msgCount;
            final long maximumMessageCount = queue.getAlertThresholdQueueDepthMessages();
            if (maximumMessageCount!= 0 && (msgCount =  queue.getQueueDepthMessages()) >= maximumMessageCount)
            {
                String notificationMsg = msgCount + ": Maximum count on queue threshold ("+ maximumMessageCount +") breached.";

                logNotification(this, queue, notificationMsg);
                listener.notifyClients(this, queue, notificationMsg);
                return true;
            }
            return false;
        }
    },
    MESSAGE_SIZE_ALERT(true)
    {
        public boolean notifyIfNecessary(ServerMessage<?> msg, AMQQueue queue, QueueNotificationListener  listener)
        {
            final long maximumMessageSize = queue.getAlertThresholdMessageSize();
            if(maximumMessageSize != 0)
            {
                // Check for threshold message size
                long messageSize;
                messageSize = (msg == null) ? 0 : msg.getSize();

                if (messageSize >= maximumMessageSize)
                {
                    String notificationMsg = messageSize + "b : Maximum message size threshold ("+ maximumMessageSize +") breached. [Message ID=" + msg.getMessageNumber() + "]";

                    logNotification(this, queue, notificationMsg);
                    listener.notifyClients(this, queue, notificationMsg);
                    return true;
                }
            }
            return false;
        }

    },
    QUEUE_DEPTH_ALERT
    {
        public boolean notifyIfNecessary(ServerMessage<?> msg, AMQQueue queue, QueueNotificationListener  listener)
        {
            // Check for threshold queue depth in bytes
            final long maximumQueueDepth = queue.getAlertThresholdQueueDepthBytes();

            if(maximumQueueDepth != 0)
            {
                final long queueDepth = queue.getQueueDepthBytes();

                if (queueDepth >= maximumQueueDepth)
                {
                    String notificationMsg = (queueDepth>>10) + "Kb : Maximum queue depth threshold ("+(maximumQueueDepth>>10)+"Kb) breached.";

                    logNotification(this, queue, notificationMsg);
                    listener.notifyClients(this, queue, notificationMsg);
                    return true;
                }
            }
            return false;
        }

    },
    MESSAGE_AGE_ALERT
    {
        public boolean notifyIfNecessary(ServerMessage<?> msg, AMQQueue queue, QueueNotificationListener  listener)
        {

            final long maxMessageAge = queue.getAlertThresholdMessageAge();
            if(maxMessageAge != 0)
            {
                final long currentTime = System.currentTimeMillis();
                final long thresholdTime = currentTime - maxMessageAge;
                final long firstArrivalTime = queue.getOldestMessageArrivalTime();

                if(firstArrivalTime != 0 && firstArrivalTime < thresholdTime)
                {
                    long oldestAge = currentTime - firstArrivalTime;
                    String notificationMsg = (oldestAge/1000) + "s : Maximum age on queue threshold ("+(maxMessageAge /1000)+"s) breached.";

                    logNotification(this, queue, notificationMsg);
                    listener.notifyClients(this, queue, notificationMsg);

                    return true;
                }
            }
            return false;

        }

    }
    ;

    private static final Logger LOGGER = Logger.getLogger(NotificationCheck.class);

    private final boolean _messageSpecific;

    NotificationCheck()
    {
        this(false);
    }

    NotificationCheck(boolean messageSpecific)
    {
        _messageSpecific = messageSpecific;
    }

    public boolean isMessageSpecific()
    {
        return _messageSpecific;
    }

    public abstract boolean notifyIfNecessary(ServerMessage<?> msg, AMQQueue queue, QueueNotificationListener  listener);

    //A bit of a hack, only for use until we do the logging listener
    private static void logNotification(NotificationCheck notification, AMQQueue queue, String notificationMsg)
    {
        LOGGER.info(notification.name() + " On Queue " + queue.getName() + " - " + notificationMsg);
    }
}
