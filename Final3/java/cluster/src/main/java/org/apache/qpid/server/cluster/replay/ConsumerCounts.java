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
package org.apache.qpid.server.cluster.replay;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.AMQShortString;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

class ConsumerCounts
{
    private final Map<AMQShortString, Integer> _counts = new HashMap<AMQShortString, Integer>();

    synchronized void increment(AMQShortString queue)
    {
        _counts.put(queue, get(queue) + 1);
    }

   synchronized void decrement(AMQShortString queue)
    {
        _counts.put(queue,  get(queue) - 1);
    }

    private int get(AMQShortString queue)
    {
        Integer count = _counts.get(queue);
        return count == null ? 0 : count;
    }

    synchronized void replay(List<AMQMethodBody> messages)
    {
        for(AMQShortString queue : _counts.keySet())
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            BasicConsumeBody m = new BasicConsumeBody((byte)8,
                                                      (byte)0,
                                                      BasicConsumeBody.getClazz((byte)8, (byte)0),
                                                      BasicConsumeBody.getMethod((byte)8, (byte)0),
                                                      null,
                                                      queue,
                                                      false,
                                                      false,
                                                      false,
                                                      false,
                                                      queue,
                                                      0);
            m.queue = queue;
            m.consumerTag = queue;
            replay(m, messages);
        }
    }

    private void replay(BasicConsumeBody msg, List<AMQMethodBody> messages)
    {
        int count = _counts.get(msg.queue);
        for(int i = 0; i < count; i++)
        {
            messages.add(msg);
        }
    }
}
