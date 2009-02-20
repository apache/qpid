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

import java.util.List;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.*;
import org.apache.qpid.server.store.StoreContext;

/** @author Apache Software Foundation */
public class NonTransactionalContext implements TransactionalContext
{
    private static final Logger _log = Logger.getLogger(NonTransactionalContext.class);

    /** Channel is useful for logging */
    private final AMQChannel _channel;

    /** Where to put undeliverable messages */
    private final List<RequiredDeliveryException> _returnMessages;



    private final TransactionLog _transactionLog;

    private final StoreContext _storeContext;

    /** Whether we are in a transaction */
    private boolean _inTran;

    public NonTransactionalContext(TransactionLog transactionLog, StoreContext storeContext, AMQChannel channel,
                                   List<RequiredDeliveryException> returnMessages)
    {
        _channel = channel;
        _storeContext = storeContext;
        _returnMessages = returnMessages;
        _transactionLog = transactionLog;

    }


    public StoreContext getStoreContext()
    {
        return _storeContext;
    }

    public void beginTranIfNecessary() throws AMQException
    {
        if (!_inTran)
        {
            _transactionLog.beginTran(_storeContext);
            _inTran = true;
        }
    }

    public void commit() throws AMQException
    {
        // Does not apply to this context
    }

    public void rollback() throws AMQException
    {
        // Does not apply to this context
    }

    public void deliver(final AMQQueue queue, AMQMessage message) throws AMQException
    {
        QueueEntry entry = queue.enqueue(_storeContext, message);
        
        //following check implements the functionality
        //required by the 'immediate' flag:
        if(entry.immediateAndNotDelivered())
        {
            _returnMessages.add(new NoConsumersException(entry.getMessage()));
        }

    }

    public void requeue(QueueEntry entry) throws AMQException
    {
        entry.requeue(_storeContext);
    }

    public void acknowledgeMessage(final long deliveryTag, long lastDeliveryTag,
                                   boolean multiple, final UnacknowledgedMessageMap unacknowledgedMessageMap)
            throws AMQException
    {

        final boolean debug = _log.isDebugEnabled();
        ;
        if (multiple)
        {
            if (deliveryTag == 0)
            {

                //Spec 2.1.6.11 ... If the multiple field is 1, and the delivery tag is zero,
                // tells the server to acknowledge all outstanding mesages.
                _log.info("Multiple ack on delivery tag 0. ACKing all messages. Current count:" +
                          unacknowledgedMessageMap.size());
                unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
                {
                    public boolean callback(final long deliveryTag, QueueEntry queueEntry) throws AMQException
                    {
                        if (debug)
                        {
                            _log.debug("Discarding message: " + queueEntry.getMessage().getMessageId());
                        }
                        if(queueEntry.getMessage().isPersistent())
                        {
                            beginTranIfNecessary();
                        }
                        //Message has been ack so dequeueAndDelete it.
                        queueEntry.dequeueAndDelete(_storeContext);

                        return false;
                    }

                    public void visitComplete()
                    {
                        unacknowledgedMessageMap.clear();
                    }
                });
            }
            else
            {
                if (!unacknowledgedMessageMap.contains(deliveryTag))
                {
                    throw new AMQException("Multiple ack on delivery tag " + deliveryTag + " not known for channel");
                }

                unacknowledgedMessageMap.drainTo(deliveryTag, _storeContext);
            }
        }
        else
        {
            QueueEntry queueEntry;
            queueEntry = unacknowledgedMessageMap.get(deliveryTag);

            if (debug)
            {
                _log.debug("Received non-multiple ack for messaging with delivery tag " + deliveryTag);
            }

            if (queueEntry == null)
            {
                _log.info("Single ack on delivery tag " + deliveryTag + " not known for channel:" +
                          _channel.getChannelId());
                throw new AMQException("Single ack on delivery tag " + deliveryTag + " not known for channel:" +
                                       _channel.getChannelId());
            }

            if (debug)
            {
                _log.debug("Discarding message: " + queueEntry.getMessage().getMessageId());
            }
            if(queueEntry.getMessage().isPersistent())
            {
                beginTranIfNecessary();
            }

            //Message has been ack so dequeueAndDelete it.
            // If the message is persistent and this is the last QueueEntry that uses it then the data will be removed
            // from the transaciton log
            queueEntry.dequeueAndDelete(_storeContext);

            unacknowledgedMessageMap.remove(deliveryTag);


        }
        if(_inTran)
        {
            _transactionLog.commitTran(_storeContext);
            _inTran = false;
        }
    }

    public void messageFullyReceived(boolean persistent) throws AMQException
    {
        if (persistent)
        {
            _transactionLog.commitTran(_storeContext);
            _inTran = false;
        }
    }

    public void messageProcessed(AMQProtocolSession protocolSession) throws AMQException
    {
        _channel.processReturns();
    }
}
