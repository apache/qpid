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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQQueue;

import org.apache.qpid.server.txn.ServerTransaction;

import java.util.Arrays;

public class QueueDestination implements SendingDestination, ReceivingDestination
{
    private static final Accepted ACCEPTED = new Accepted();
    private static final Outcome[] OUTCOMES = new Outcome[] { ACCEPTED };


    private AMQQueue _queue;

    public QueueDestination(AMQQueue queue)
    {
        _queue = queue;
    }

    public Outcome[] getOutcomes()
    {
        return OUTCOMES;
    }

    public Outcome send(final Message_1_0 message, ServerTransaction txn)
    {

        try
        {
            txn.enqueue(_queue,message, new ServerTransaction.Action()
            {


                public void postCommit()
                {
                    try
                    {

                        _queue.enqueue(message);
                    }
                    catch (Exception e)
                    {
                        // TODO
                        throw new RuntimeException(e);
                    }

                }

                public void onRollback()
                {
                    // NO-OP
                }
            });
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return ACCEPTED;
    }

    public int getCredit()
    {
        // TODO - fix
        return 100;
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

}
