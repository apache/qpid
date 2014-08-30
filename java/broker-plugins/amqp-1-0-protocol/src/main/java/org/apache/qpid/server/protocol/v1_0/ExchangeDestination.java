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
package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.Rejected;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusExpiryPolicy;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.txn.ServerTransaction;

public class ExchangeDestination implements ReceivingDestination, SendingDestination
{
    private static final Accepted ACCEPTED = new Accepted();
    public static final Rejected REJECTED = new Rejected();
    private static final Outcome[] OUTCOMES = { ACCEPTED, REJECTED};

    private ExchangeImpl _exchange;
    private TerminusDurability _durability;
    private TerminusExpiryPolicy _expiryPolicy;
    private String _initialRoutingAddress;

    public ExchangeDestination(ExchangeImpl exchange, TerminusDurability durable, TerminusExpiryPolicy expiryPolicy)
    {
        _exchange = exchange;
        _durability = durable;
        _expiryPolicy = expiryPolicy;
    }

    public Outcome[] getOutcomes()
    {
        return OUTCOMES;
    }

    public Outcome send(final Message_1_0 message, ServerTransaction txn)
    {
        final InstanceProperties instanceProperties =
            new InstanceProperties()
            {

                @Override
                public Object getProperty(final Property prop)
                {
                    switch(prop)
                    {
                        case MANDATORY:
                            return false;
                        case REDELIVERED:
                            return false;
                        case PERSISTENT:
                            return message.isPersistent();
                        case IMMEDIATE:
                            return false;
                        case EXPIRATION:
                            return message.getExpiration();
                    }
                    return null;
                }};

        String routingAddress;
        MessageMetaData_1_0.MessageHeader_1_0 messageHeader = message.getMessageHeader();
        if(_initialRoutingAddress == null)
        {
            routingAddress = messageHeader.getSubject();
            if(routingAddress == null)
            {
                if (messageHeader.getHeader("routing-key") instanceof String)
                {
                    routingAddress = (String) messageHeader.getHeader("routing-key");
                }
                else if (messageHeader.getHeader("routing_key") instanceof String)
                {
                    routingAddress = (String) messageHeader.getHeader("routing_key");
                }
                else if (messageHeader.getTo() != null
                        && messageHeader.getTo().startsWith(_exchange.getName() + "/"))
                {
                    routingAddress = messageHeader.getTo().substring(1+_exchange.getName().length());
                }
                else
                {
                    routingAddress = "";
                }
            }
        }
        else
        {
            if (messageHeader.getTo() != null
                && messageHeader.getTo().startsWith(_exchange.getName() + "/" + _initialRoutingAddress + "/"))
            {
                routingAddress = messageHeader.getTo().substring(2+_exchange.getName().length()+_initialRoutingAddress.length());
            }
            else
            {
                routingAddress = _initialRoutingAddress;
            }
        }
        int enqueues = _exchange.send(message,
                                      routingAddress,
                                      instanceProperties,
                                      txn,
                                      null);


        return enqueues == 0 ? REJECTED : ACCEPTED;
    }

    TerminusDurability getDurability()
    {
        return _durability;
    }

    TerminusExpiryPolicy getExpiryPolicy()
    {
        return _expiryPolicy;
    }

    public int getCredit()
    {
        // TODO - fix
        return 20000;
    }

    public ExchangeImpl getExchange()
    {
        return _exchange;
    }

    public void setInitialRoutingAddress(final String initialRoutingAddress)
    {
        _initialRoutingAddress = initialRoutingAddress;
    }

    public String getInitialRoutingAddress()
    {
        return _initialRoutingAddress;
    }
}
