/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.log4j.Logger;

import javax.transaction.xa.Xid;
import java.util.List;
import java.util.LinkedList;

/**
 * Created by Arnaud Simon
 * Date: 25-Apr-2007
 * Time: 15:58:07
 */
public class DistributedTransactionalContext implements TransactionalContext
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(DistributedTransactionalContext.class);
    private static final Object _lockXID = new Object();
    private static int _count = 0;
    //========================================================================
    // Instance Fields
    //========================================================================
    // the message store
    final private MessageStore _messageStore;
    // The transaction manager
    final private TransactionManager _transactionManager;
    // the store context
    final private StoreContext _storeContext;
    // the returned messages
    final private List<RequiredDeliveryException> _returnMessages;
    // for generating xids
    private byte[] _txId = ("txid").getBytes();

    public DistributedTransactionalContext(TransactionManager transactionManager, MessageStore messageStore, StoreContext storeContext,
                                           List<RequiredDeliveryException> returnMessages)
    {
        _messageStore = messageStore;
        _storeContext = storeContext;
        _returnMessages = returnMessages;
        _transactionManager = transactionManager;
    }

    public void beginTranIfNecessary()
            throws
            AMQException
    {
        if (_storeContext.getPayload() == null)
        {
            synchronized (_lockXID)
            {
                // begin the transaction and pass the XID through the context
                Xid xid = new XidImpl(("branch" + _count++).getBytes(), 1, _txId);
                try
                {
                    _transactionManager.begin(xid);
                    _storeContext.setPayload(xid);
                } catch (Exception e)
                {
                    throw new AMQException(null, "Problem during transaction begin", e);
                }
            }
        }
    }

    public void commit()
            throws
            AMQException
    {
        try
        {
            if (_storeContext.getPayload() != null)
            {
                _transactionManager.commit_one_phase((Xid) _storeContext.getPayload());
            }
        } catch (Exception e)
        {
            throw new AMQException(null, "Problem during transaction commit", e);
        }
        finally
        {
            _storeContext.setPayload(null);
        }
    }

    public void rollback()
            throws
            AMQException
    {
        try
        {
            if (_storeContext.getPayload() != null)
            {
                _transactionManager.rollback((Xid) _storeContext.getPayload());
            }
        } catch (Exception e)
        {
            throw new AMQException(null, "Problem during transaction rollback", e);
        }
        finally
        {
            _storeContext.setPayload(null);
        }
    }

    public void messageFullyReceived(boolean persistent)
            throws
            AMQException
    {
        // The message is now fully received, we can stage it before enqueued if necessary
    }

    public void deliver(QueueEntry entry, boolean deliverFirst)
            throws
            AMQException
    {
        try
        {
            // add a record in the transaction
            _transactionManager.getTransaction((Xid) _storeContext.getPayload()).addRecord(new EnqueueRecord(_storeContext, entry, deliverFirst));
        } catch (Exception e)
        {
            throw new AMQException(null, "Problem during transaction rollback", e);
        }
    }

    public void acknowledgeMessage(long deliveryTag, long lastDeliveryTag,
                                   boolean multiple,
                                   final UnacknowledgedMessageMap unacknowledgedMessageMap)
            throws
            AMQException
    {
        beginTranIfNecessary();
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
                    public boolean callback(UnacknowledgedMessage message)
                            throws
                            AMQException
                    {
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Discarding message: " + message.entry.debugIdentity());
                        }

                        //Message has been ack so discard it. This will dequeue and decrement the reference.
                        dequeue(message);
                        return false;
                    }

                    public void visitComplete()
                    {
                        unacknowledgedMessageMap.clear();
                    }
                });
            } else
            {
                if (!unacknowledgedMessageMap.contains(deliveryTag))
                {
                    throw new AMQException(null, "Multiple ack on delivery tag " + deliveryTag + " not known for channel", null);
                }

                LinkedList<UnacknowledgedMessage> acked = new LinkedList<UnacknowledgedMessage>();
                unacknowledgedMessageMap.drainTo(acked, deliveryTag);
                for (UnacknowledgedMessage msg : acked)
                {
                    if (_log.isDebugEnabled())
                    {
                        _log.debug("Discarding message: " + msg.entry.debugIdentity());
                    }
                    //Message has been ack so discard it. This will dequeue and decrement the reference.
                    dequeue(msg);
                }
            }
        } else
        {
            UnacknowledgedMessage msg;
            msg = unacknowledgedMessageMap.remove(deliveryTag);

            if (msg == null)
            {
                _log.info("Single ack on delivery tag " + deliveryTag);
                throw new AMQException(null, "Single ack on delivery tag " + deliveryTag, null);
            }

            if (_log.isDebugEnabled())
            {
                _log.debug("Discarding message: " + msg.entry.debugIdentity());
            }

            //Message has been ack so discard it. This will dequeue and decrement the reference.
            dequeue(msg);

            if (_log.isDebugEnabled())
            {
                _log.debug("Received non-multiple ack for messaging with delivery tag " + deliveryTag + " msg id " +
                        msg.entry.debugIdentity());
            }
        }
    }

    private void dequeue(UnacknowledgedMessage message)
            throws
            AMQException
    {
        // Dequeue the message from the strore
        message.discard(_storeContext);
        // Add a record
        try
        {
            _transactionManager.getTransaction((Xid) _storeContext.getPayload()).addRecord(new DequeueRecord());
        } catch (Exception e)
        {
            throw new AMQException(null, "Problem during message dequeue", e);
        }
    }


    public void messageProcessed(AMQProtocolSession protocolSession)
            throws
            AMQException
    {
        // The message has been sent
    }

    public StoreContext getStoreContext()
    {
        return _storeContext;
    }
}
