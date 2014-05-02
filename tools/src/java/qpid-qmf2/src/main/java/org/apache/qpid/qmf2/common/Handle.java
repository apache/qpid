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
package org.apache.qpid.qmf2.common;

// JMS Imports
import javax.jms.Destination; // Needed for replyTo
import javax.jms.JMSException;

/**
 * This class represents the reply Handle used for asynchronous operations
 * 
 * @author Fraser Adams
 */
public final class Handle
{
    private final String _correlationId;
    private final Destination _replyTo;

    /**
     * Construct a Handle containing only a correlationId 
     *
     * @param correlationId - a String used to tie together requests and responses
     */
    public Handle(final String correlationId)
    {
        _correlationId = correlationId;
        _replyTo = null;
    }

    /**
     * Construct a Handle containing a correlationId and a replyTo.
     *
     * @param correlationId - a String used to tie together requests and responses
     * @param replyTo - the JMS replyTo
     */
    public Handle(final String correlationId, final Destination replyTo)
    {
        _correlationId = correlationId;
        _replyTo = replyTo;
    }

    /**
     * Returns the correlationId String.
     * @return the correlationId String
     */
    public String getCorrelationId()
    {
        return _correlationId;
    }

    /**
     * Return the replyTo Destination.
     * @return the replyTo Destination
     */
    public Destination getReplyTo()
    {
        return _replyTo;
    }

    /**
     * Returns the Routing Key for the replyTo as a String
     * <p>
     * All things being equal it probably makes most logical sense to use the replyTo obtained from the JMS
     * Message when replying to a request however..... for Qpid up to version 0.12 at least there seems to be
     * a bug with the replyTo whereby invoking send() on the replyTo causes spurious exchangeDeclares to occur.
     * The exchangeDeclare is apparently to validate the destination however there is supposed to be a cache
     * that should prevent this from occurring if the replyTo Destination is reused, but that's broken.
     * <p>
     * As an alternative we get hold of the Routing Key of the replyTo which, is sneakily available from getTopicName()
     * the Routing Key can then be used as the subject of the returned message to enable delivery of the Message
     * to the appropriate address.
     * <p>
     * org.apache.qpid.client.AMQTopic.getTopicName() returns "getRoutingKey().asString()" so this seems an OK
     * way to get the Routing Key from the replyTo using the pure JMS API.
     *
     * @return the Routing Key for the replyTo
     */
    public String getRoutingKey()
    {
        try
        {
            return ((javax.jms.Topic)_replyTo).getTopicName();
        }
        catch (JMSException jmse)
        {
            return "";
        }
    }
}
