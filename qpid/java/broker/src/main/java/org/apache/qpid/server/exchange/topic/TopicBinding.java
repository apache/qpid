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
package org.apache.qpid.server.exchange.topic;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.exchange.TopicExchange;

public class TopicBinding
{
    private final AMQShortString _bindingKey;
    private final AMQQueue _queue;
    private final FieldTable _args;

    public TopicBinding(AMQShortString bindingKey, AMQQueue queue, FieldTable args)
    {
        _bindingKey = bindingKey;
        _queue = queue;
        _args = args;
    }

    public AMQShortString getBindingKey()
    {
        return _bindingKey;
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public int hashCode()
    {
        return (_bindingKey == null ? 1 : _bindingKey.hashCode())*31 +_queue.hashCode();
    }

    public boolean equals(Object o)
    {
        if(this == o)
        {
            return true;
        }
        if(o instanceof TopicBinding)
        {
            TopicBinding other = (TopicBinding) o;
            return (_queue == other._queue)
                    && ((_bindingKey == null) ? other._bindingKey == null : _bindingKey.equals(other._bindingKey));
        }
        return false;
    }
}
