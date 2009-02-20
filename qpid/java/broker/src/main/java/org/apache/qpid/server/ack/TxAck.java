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
package org.apache.qpid.server.ack;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.TxnOp;
import org.apache.qpid.server.queue.QueueEntry;

/**
 * A TxnOp implementation for handling accumulated acks
 */
public class TxAck implements TxnOp
{
    private final UnacknowledgedMessageMap _map;
    private final Map<Long, QueueEntry> _unacked = new HashMap<Long,QueueEntry>();
    private List<Long> _individual;
    private long _deliveryTag;
    private boolean _multiple;

    public TxAck(UnacknowledgedMessageMap map)
    {
        _map = map;
    }

    public void update(long deliveryTag, boolean multiple)
    {
        _unacked.clear();
        if (!multiple)
        {
            if(_individual == null)
            {
                _individual = new ArrayList<Long>();
            }
            //have acked a single message that is not part of
            //the previously acked region so record
            //individually
            _individual.add(deliveryTag);//_multiple && !multiple
        }
        else if (deliveryTag > _deliveryTag)
        {
            //have simply moved the last acked message on a
            //bit
            _deliveryTag = deliveryTag;
            _multiple = true;
        }
    }

    public void consolidate()
    {
        if(_unacked.isEmpty())
        {
            //lookup all the unacked messages that have been acked in this transaction
            if (_multiple)
            {
                //get all the unacked messages for the accumulated
                //multiple acks
                _map.collect(_deliveryTag, true, _unacked);
            }
            if(_individual != null)
            {
                //get any unacked messages for individual acks outside the
                //range covered by multiple acks
                for (long tag : _individual)
                {
                    if(_deliveryTag < tag)
                    {
                        _map.collect(tag, false, _unacked);
                    }
                }
            }
        }
    }

    public boolean checkPersistent() throws AMQException
    {
        consolidate();
        //if any of the messages in unacked are persistent the txn
        //buffer must be marked as persistent:
        for (QueueEntry msg : _unacked.values())
        {
            if (msg.getMessage().isPersistent())
            {
                return true;
            }
        }
        return false;
    }

    public void prepare(StoreContext storeContext) throws AMQException
    {
        //make persistent changes, i.e. dequeue and decrementReference
        for (QueueEntry msg : _unacked.values())
        {
            //Message has been ack so discard it. This will dequeue and decrement the reference.
            msg.discard(storeContext);

        }
    }

    public void undoPrepare()
    {
        //decrementReference is annoyingly untransactional (due to
        //in memory counter) so if we failed in prepare for full
        //txn, this op will have to compensate by fixing the count
        //in memory (persistent changes will be rolled back by store)
        for (QueueEntry msg : _unacked.values())
        {
            msg.getMessage().incrementReference(1);
        }
    }

    public void commit(StoreContext storeContext)
    {
        //remove the unacked messages from the channels map
        _map.remove(_unacked);        
    }

    public void rollback(StoreContext storeContext)
    {
    }
}


