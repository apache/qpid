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
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoreContext;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class DeliverMessageOperation implements TxnOp
{
    private static final Logger _logger = Logger.getLogger(DeliverMessageOperation.class);

    private final AMQMessage _msg;

    private final AMQQueue _queue;

    public DeliverMessageOperation(AMQMessage msg, AMQQueue queue)
    {
        _msg = msg;
        _queue = queue;
        _msg.incrementReference();
    }

    public void prepare(StoreContext context) throws AMQException
    {
    }

    public void undoPrepare()
    {
    }

    public void commit(StoreContext context) throws AMQException
    {
        //do the memeory part of the record()
        _msg.incrementReference();
        //then process the message
        try
        {
            _queue.process(context, _msg);
        }
        catch (AMQException e)
        {
            //TODO: is there anything else we can do here? I think not...
            _logger.error("Error during commit of a queue delivery: " + e, e);
        }
    }

    public void rollback(StoreContext storeContext)
    {
    }
}
