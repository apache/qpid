/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.cluster.replay;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.BasicConsumeBody;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

class ConsumerCounts
{
    private final Map<String, Integer> _counts = new HashMap<String, Integer>();

    synchronized void increment(String queue)
    {
        _counts.put(queue, get(queue) + 1);
    }

   synchronized void decrement(String queue)
    {
        _counts.put(queue,  get(queue) - 1);
    }

    private int get(String queue)
    {
        Integer count = _counts.get(queue);
        return count == null ? 0 : count;
    }

    synchronized void replay(List<AMQMethodBody> messages)
    {
        for(String queue : _counts.keySet())
        {
            BasicConsumeBody m = new BasicConsumeBody();
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
