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

import java.util.ArrayList;
import java.util.List;

public class SubscriptionTestHelper implements Subscription
{
    private final List<AMQMessage> messages;
    private final Object key;
    private boolean isSuspended;

    public SubscriptionTestHelper(Object key)
    {
        this(key, new ArrayList<AMQMessage>());
    }

    public SubscriptionTestHelper(final Object key, final boolean isSuspended)
    {
        this(key);
        setSuspended(isSuspended);
    }

    SubscriptionTestHelper(Object key, List<AMQMessage> messages)
    {
        this.key = key;
        this.messages = messages;
    }

    List<AMQMessage> getMessages()
    {
        return messages;
    }

    public void send(AMQMessage msg, AMQQueue queue)
    {
        messages.add(msg);
    }

    public void setSuspended(boolean suspended)
    {
        isSuspended = suspended;
    }

    public boolean isSuspended()
    {
        return isSuspended;
    }

    public void queueDeleted(AMQQueue queue)
    {
    }

    public int hashCode()
    {
        return key.hashCode();
    }

    public boolean equals(Object o)
    {
        return o instanceof SubscriptionTestHelper && ((SubscriptionTestHelper) o).key.equals(key);
    }

    public String toString()
    {
        return key.toString();
    }
}
