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
package org.apache.qpid.jms;

import javax.jms.JMSException;
import javax.jms.Queue;

import org.apache.qpid.messaging.Address;

public class QpidQueue extends QpidDestination implements Queue
{
    public QpidQueue()
    {
    }

    public QpidQueue(String str) throws JMSException
    {
        setDestinationString(str);
    }

    public QpidQueue(Address addr)
    {
        super(addr);
    }

    @Override
    public DestinationType getType()
    {
        return DestinationType.QUEUE;
    }

    @Override
    public String getQueueName()
    {
        return getAddress().getName();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if(obj.getClass() != getClass())
        {
            return false;
        }

        QpidQueue queue = (QpidQueue)obj;
        return getQueueName().equals(queue.getQueueName());

    }

    @Override
    public int hashCode()
    {
        String queue = getAddress() == null ? "" : getAddress().getName();
        int result = 17;
        result = 37*result + queue.hashCode();
        return result;

    }
}
