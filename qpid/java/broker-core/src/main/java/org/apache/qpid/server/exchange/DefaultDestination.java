/*
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
 */
package org.apache.qpid.server.exchange;

import org.apache.log4j.Logger;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class DefaultDestination implements MessageDestination
{

    private VirtualHostImpl _virtualHost;
    private static final Logger _logger = Logger.getLogger(DefaultDestination.class);

    public DefaultDestination(VirtualHostImpl virtualHost)
    {
        _virtualHost =  virtualHost;
    }

    @Override
    public String getName()
    {
        return ExchangeDefaults.DEFAULT_EXCHANGE_NAME;
    }


    public final  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                                                                                        String routingAddress,
                                                                                        final InstanceProperties instanceProperties,
                                                                                        final ServerTransaction txn,
                                                                                        final Action<? super MessageInstance> postEnqueueAction)
    {
        if(routingAddress == null)
        {
            routingAddress = "";
        }
        final AMQQueue q = _virtualHost.getQueue(routingAddress);
        if(q == null)
        {
            routingAddress = _virtualHost.getLocalAddress(routingAddress);
            if(routingAddress.contains("/") && !routingAddress.startsWith("/"))
            {
                String[] parts = routingAddress.split("/",2);
                ExchangeImpl exchange = _virtualHost.getExchange(parts[0]);
                if(exchange != null)
                {
                    return exchange.send(message, parts[1], instanceProperties, txn, postEnqueueAction);
                }
            }
            else if(!routingAddress.contains("/"))
            {
                ExchangeImpl exchange = _virtualHost.getExchange(routingAddress);
                if(exchange != null)
                {
                    return exchange.send(message, "", instanceProperties, txn, postEnqueueAction);
                }
            }
            return 0;
        }
        else
        {
            txn.enqueue(q,message, new ServerTransaction.Action()
            {
                MessageReference _reference = message.newReference();

                public void postCommit()
                {
                    try
                    {
                        q.enqueue(message, postEnqueueAction);
                    }
                    finally
                    {
                        _reference.release();
                    }
                }

                public void onRollback()
                {
                    _reference.release();
                }
            });
            return 1;
        }
    }

}
