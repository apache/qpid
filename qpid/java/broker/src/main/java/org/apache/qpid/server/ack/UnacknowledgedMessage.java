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
package org.apache.qpid.server.ack;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;

public class UnacknowledgedMessage
{
    public final AMQMessage message;
    public final String consumerTag;
    public final long deliveryTag;
    public AMQQueue queue;
    
    public UnacknowledgedMessage(AMQQueue queue, AMQMessage message, String consumerTag, long deliveryTag)
    {
        this.queue = queue;
        this.message = message;
        this.consumerTag = consumerTag;
        this.deliveryTag = deliveryTag;
    }

    public void discard() throws AMQException
    {
        if (queue != null)
        {
            message.dequeue(queue);
        }
        message.decrementReference();
    }
}

