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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.NoConsumersException;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

/** @author Apache Software Foundation */
public class NonTransactionalContext implements TransactionalContext
{
    private static final Logger _log = Logger.getLogger(NonTransactionalContext.class);

    /** Channel is useful for logging */
    private final AMQChannel _channel;

    /** Where to put undeliverable messages */
    private final List<RequiredDeliveryException> _returnMessages;

    private final Set<Long> _browsedAcks;

    private final MessageStore _messageStore;

    private final StoreContext _storeContext;

    /** Whether we are in a transaction */
    private boolean _inTran;

    public NonTransactionalContext(MessageStore messageStore, StoreContext storeContext, AMQChannel channel,
                                   List<RequiredDeliveryException> returnMessages, Set<Long> browsedAcks)
    {
        _channel = channel;
        _storeContext = storeContext;
        _returnMessages = returnMessages;
        _messageStore = messageStore;
        _browsedAcks = browsedAcks;
    }


    public StoreContext getStoreContext()
    {
        return _storeContext;
    }

    public void beginTranIfNecessary() throws AMQException
    {
        if (!_inTran)
        {
            _messageStore.beginTran(_storeContext);
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

    public void deliver(QueueEntry entry, boolean deliverFirst) throws AMQException
    {
        try
        {
            entry.process(_storeContext, deliverFirst);
            //following check implements the functionality
            //required by the 'immediate' flag:
            entry.checkDeliveredToConsumer();
        }
        catch (NoConsumersException e)
        {
            _returnMessages.add(e);
        }
    }

    public void acknowledgeMessage(final long deliveryTag, long lastDeliveryTag,
                                   boolean multiple, final UnacknowledgedMessageMap unacknowledgedMessageMap)
            throws AMQException
    {
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
                    public boolean callback(UnacknowledgedMessage message) throws AMQException
                    {
                        if (!_browsedAcks.contains(deliveryTag))
                        {
                            if (_log.isDebugEnabled())
                            {
                                _log.debug("Discarding message: " + message.getMessage().getMessageId());
                            }

                            //Message has been ack so discard it. This will dequeue and decrement the reference.
                            message.discard(_storeContext);
                        }
                        else
                        {
                            _browsedAcks.remove(deliveryTag);
                        }
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
                    throw new AMQException(null, "Multiple ack on delivery tag " + deliveryTag + " not known for channel", null);
                }

                LinkedList<UnacknowledgedMessage> acked = new LinkedList<UnacknowledgedMessage>();
                unacknowledgedMessageMap.drainTo(acked, deliveryTag);
                for (UnacknowledgedMessage msg : acked)
                {
                    if (!_browsedAcks.contains(deliveryTag))
                    {
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Discarding message: " + msg.getMessage().getMessageId());
                        }

                        //Message has been ack so discard it. This will dequeue and decrement the reference.
                        msg.discard(_storeContext);
                    }
                    else
                    {
                        _browsedAcks.remove(deliveryTag);
                    }
                }
            }
        }
        else
        {
            UnacknowledgedMessage msg;
            msg = unacknowledgedMessageMap.remove(deliveryTag);

            if (msg == null)
            {
                _log.info("Single ack on delivery tag " + deliveryTag + " not known for channel:" +
                          _channel.getChannelId());
                throw new AMQException(null, "Single ack on delivery tag " + deliveryTag + " not known for channel:" +
                                       _channel.getChannelId(), null);
            }

            if (!_browsedAcks.contains(deliveryTag))
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug("Discarding message: " + msg.getMessage().getMessageId());
                }

                //Message has been ack so discard it. This will dequeue and decrement the reference.
                msg.discard(_storeContext);
            }
            else
            {
                _browsedAcks.remove(deliveryTag);
            }

            if (_log.isDebugEnabled())
            {
                _log.debug("Received non-multiple ack for messaging with delivery tag " + deliveryTag + " msg id " +
                           msg.getMessage().getMessageId());
            }
        }
    }

    public void messageFullyReceived(boolean persistent) throws AMQException
    {
        if (persistent)
        {
            //DTX removed this option.
            _messageStore.commitTran(_storeContext);
            _inTran = false;
        }
    }

    public void messageProcessed(AMQProtocolSession protocolSession) throws AMQException
    {
        _channel.processReturns(protocolSession);
    }
}
