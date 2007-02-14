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
package org.apache.qpid.client.message;

import javax.jms.JMSException;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.ContentHeaderProperties;

public class AMQMessage
{
    protected ContentHeaderProperties _contentHeaderProperties;

    /**
     * If the acknowledge mode is CLIENT_ACKNOWLEDGE the session is required
     */
    protected AMQSession _session;

    protected final long _deliveryTag;

    public AMQMessage(ContentHeaderProperties properties, long deliveryTag)
    {
        _contentHeaderProperties = properties;
        _deliveryTag = deliveryTag;
    }

    public AMQMessage(ContentHeaderProperties properties)
    {
        this(properties, -1);
    }

    /**
     * The session is set when CLIENT_ACKNOWLEDGE mode is used so that the CHANNEL ACK can be sent when the user
     * calls acknowledge()
     * @param s the AMQ session that delivered this message
     */
    public void setAMQSession(AMQSession s)
    {
        _session = s;
    }

    public AMQSession getAMQSession()
    {
        return _session;
    }

    /**
     * Get the AMQ message number assigned to this message
     * @return the message number
     */
    public long getDeliveryTag()
    {
        return _deliveryTag;
    }

    /**
     * Invoked prior to sending the message. Allows the message to be modified if necessary before
     * sending.
     */
    public void prepareForSending() throws JMSException
    {
    }
}
