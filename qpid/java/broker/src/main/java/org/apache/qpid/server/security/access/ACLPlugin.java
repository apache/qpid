/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.security.access;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

public interface ACLPlugin
{
    String getPluginName();

    void setConfiguaration(Configuration config);

    // These return true if the plugin thinks the action should be allowed, and false if not. 
    
    boolean authoriseBind(AMQProtocolSession session, Exchange exch, AMQQueue queue, AMQShortString routingKey);

    boolean authoriseCreateExchange(AMQProtocolSession session, boolean autoDelete, boolean durable,
            AMQShortString exchangeName, boolean internal, boolean nowait, boolean passive, AMQShortString exchangeType);

    boolean authoriseCreateQueue(AMQProtocolSession session, boolean autoDelete, boolean durable, boolean exclusive,
            boolean nowait, boolean passive, AMQShortString queue);

    boolean authoriseConnect(AMQProtocolSession session, VirtualHost virtualHost);

    boolean authoriseConsume(AMQProtocolSession session, boolean noAck, AMQQueue queue);

    boolean authoriseConsume(AMQProtocolSession session, boolean exclusive, boolean noAck, boolean noLocal,
            boolean nowait, AMQQueue queue);

    boolean authoriseDelete(AMQProtocolSession session, AMQQueue queue);

    boolean authoriseDelete(AMQProtocolSession session, Exchange exchange);

    boolean authorisePublish(AMQProtocolSession session, boolean immediate, boolean mandatory,
            AMQShortString routingKey, Exchange e);

    boolean authorisePurge(AMQProtocolSession session, AMQQueue queue);

    boolean authoriseUnbind(AMQProtocolSession session, Exchange exch, AMQShortString routingKey, AMQQueue queue);

}
