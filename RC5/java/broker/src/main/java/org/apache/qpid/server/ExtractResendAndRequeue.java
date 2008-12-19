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
package org.apache.qpid.server;

import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.ack.UnacknowledgedMessageMapImpl;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.Map;

public class ExtractResendAndRequeue implements UnacknowledgedMessageMap.Visitor
{
    private static final Logger _log = Logger.getLogger(ExtractResendAndRequeue.class);

    private Map<Long, QueueEntry> _msgToRequeue;
    private Map<Long, QueueEntry> _msgToResend;
    private boolean _requeueIfUnabletoResend;
    private StoreContext _storeContext;
    private UnacknowledgedMessageMap _unacknowledgedMessageMap;

    public ExtractResendAndRequeue(UnacknowledgedMessageMap unacknowledgedMessageMap, 
                                   Map<Long, QueueEntry> msgToRequeue,
                                   Map<Long, QueueEntry> msgToResend,
                                   boolean requeueIfUnabletoResend,
                                   StoreContext storeContext)
    {
        _unacknowledgedMessageMap = unacknowledgedMessageMap;
        _msgToRequeue = msgToRequeue;
        _msgToResend = msgToResend;
        _requeueIfUnabletoResend = requeueIfUnabletoResend;
        _storeContext = storeContext;
    }

    public boolean callback(final long deliveryTag, QueueEntry message) throws AMQException
    {

        AMQMessage msg = message.getMessage();
        msg.setRedelivered(true);
        final Subscription subscription = message.getDeliveredSubscription();
        if (subscription != null)
        {
            // Consumer exists
            if (!subscription.isClosed())
            {
                _msgToResend.put(deliveryTag, message);
            }
            else // consumer has gone
            {
                _msgToRequeue.put(deliveryTag, message);
            }
        }
        else
        {
            // Message has no consumer tag, so was "delivered" to a GET
            // or consumer no longer registered
            // cannot resend, so re-queue.
            if (!message.isQueueDeleted())
            {
                if (_requeueIfUnabletoResend)
                {
                    _msgToRequeue.put(deliveryTag, message);
                }
                else
                {
                    message.discard(_storeContext);
                    _log.info("No DeadLetter Queue and requeue not requested so dropping message:" + message);
                }
            }
            else
            {
                message.discard(_storeContext);
                _log.warn("Message.queue is null and no DeadLetter Queue so dropping message:" + message);
            }
        }

        // false means continue processing
        return false;
    }

    public void visitComplete()
    {
        _unacknowledgedMessageMap.clear();
    }

}
