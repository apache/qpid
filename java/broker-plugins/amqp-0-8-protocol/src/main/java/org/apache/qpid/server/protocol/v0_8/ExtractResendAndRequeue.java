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
package org.apache.qpid.server.protocol.v0_8;

import java.util.Map;

import org.apache.log4j.Logger;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.message.MessageInstance;

public class ExtractResendAndRequeue implements UnacknowledgedMessageMap.Visitor
{
    private static final Logger _log = Logger.getLogger(ExtractResendAndRequeue.class);

    private final Map<Long, MessageInstance> _msgToRequeue;
    private final Map<Long, MessageInstance> _msgToResend;
    private final UnacknowledgedMessageMap _unacknowledgedMessageMap;

    public ExtractResendAndRequeue(UnacknowledgedMessageMap unacknowledgedMessageMap,
                                   Map<Long, MessageInstance> msgToRequeue,
                                   Map<Long, MessageInstance> msgToResend)
    {
        _unacknowledgedMessageMap = unacknowledgedMessageMap;
        _msgToRequeue = msgToRequeue;
        _msgToResend = msgToResend;
    }

    public boolean callback(final long deliveryTag, MessageInstance message)
    {

        message.setRedelivered();
        final ConsumerImpl consumer = message.getDeliveredConsumer();
        if (consumer != null)
        {
            // Consumer exists
            if (!consumer.isClosed())
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
            _log.info("No DeadLetter Queue and requeue not requested so dropping message:" + message);
        }

        // false means continue processing
        return false;
    }

    public void visitComplete()
    {
        _unacknowledgedMessageMap.clear();
    }

}
