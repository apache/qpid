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
package org.apache.qpid.server.txn;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.NoConsumersException;
import org.apache.qpid.server.store.StoreContext;

import java.util.List;

/**
 * @author Apache Software Foundation
 */
public class CleanupMessageOperation implements TxnOp
{
    private static final Logger _log = Logger.getLogger(CleanupMessageOperation.class);

    private final AMQMessage _msg;

    private final List<RequiredDeliveryException> _returns;

    public CleanupMessageOperation(AMQMessage msg, List<RequiredDeliveryException> returns)
    {
        _msg = msg;
        _returns = returns;
    }

    public void prepare(StoreContext context) throws AMQException
    { }

    public void undoPrepare()
    {
        // don't need to do anything here, if the store's txn failed
        // when processing prepare then the message was not stored
        // or enqueued on any queues and can be discarded
    }

    public void commit(StoreContext context)
    {
        // No-op can't be done here has this is before the message has been attempted to be delivered.
        /*try
        {
            _msg.checkDeliveredToConsumer();
        }
        catch (NoConsumersException e)
        {
            _returns.add(e);
        }*/
    }

    public void rollback(StoreContext context)
    {
        // NO OP
    }
}
