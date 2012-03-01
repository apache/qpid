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

import java.util.List;
import org.apache.qpid.AMQException;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.txn.ServerTransaction;

public class ExchangeDestination implements ReceivingDestination, SendingDestination
{
    private static final Accepted ACCEPTED = new Accepted();
    private static final Outcome[] OUTCOMES = { ACCEPTED };

    private Exchange _exchange;

    public ExchangeDestination(Exchange exchange)
    {
        _exchange = exchange;
    }

    public Outcome[] getOutcomes()
    {
        return OUTCOMES;
    }

    public Outcome send(final Message_1_0 message, ServerTransaction txn)
    {
        final List<? extends BaseQueue> queues = _exchange.route(message);

        txn.enqueue(queues,message, new ServerTransaction.Action()
        {

            BaseQueue[] _queues = queues.toArray(new BaseQueue[queues.size()]);

            public void postCommit()
            {
                for(int i = 0; i < _queues.length; i++)
                {
                    try
                    {
                        _queues[i].enqueue(message);
                    }
                    catch (AMQException e)
                    {
                        // TODO
                        throw new RuntimeException(e);
                    }
                }
            }

            public void onRollback()
            {
                // NO-OP
            }
        }, System.currentTimeMillis());

        return ACCEPTED;
    }

    public int getCredit()
    {
        // TODO - fix
        return 20000;
    }

    public Exchange getExchange()
    {
        return _exchange;
    }
}
