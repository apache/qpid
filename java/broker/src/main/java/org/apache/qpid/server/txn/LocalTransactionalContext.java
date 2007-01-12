/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.txn;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.ack.TxAck;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

import java.util.LinkedList;
import java.util.List;

/**
 * A transactional context that only supports local transactions.
 */
public class LocalTransactionalContext implements TransactionalContext
{
    private static final Logger _log = Logger.getLogger(LocalTransactionalContext.class);

    private final TxnBuffer _txnBuffer = new TxnBuffer();

    private final List<DeliveryDetails> _postCommitDeliveryList = new LinkedList<DeliveryDetails>();

    /**
     * We keep hold of the ack operation so that we can consolidate acks, i.e. multiple acks within a txn are
     * consolidated into a single operation
     */
    private TxAck _ackOp;

    private List<RequiredDeliveryException> _returnMessages;

    private final MessageStore _messageStore;

    private final StoreContext _storeContext;

    private boolean _inTran = false;

    private boolean _messageDelivered = false;

    private static class DeliveryDetails
    {
        public AMQMessage message;
        public AMQQueue queue;


        public DeliveryDetails(AMQMessage message, AMQQueue queue)
        {
            this.message = message;
            this.queue = queue;
        }
    }

    public LocalTransactionalContext(MessageStore messageStore, StoreContext storeContext,
                                     List<RequiredDeliveryException> returnMessages)
    {
        _messageStore = messageStore;
        _storeContext = storeContext;
        _returnMessages = returnMessages;
        //_txnBuffer.enlist(new StoreMessageOperation(messageStore));
    }


    public StoreContext getStoreContext()
    {
        return _storeContext;
    }

    public void rollback() throws AMQException
    {
        _txnBuffer.rollback(_storeContext);
    }

    public void deliver(AMQMessage message, AMQQueue queue) throws AMQException
    {
        // A publication will result in the enlisting of several
        // TxnOps. The first is an op that will store the message.
        // Following that (and ordering is important), an op will
        // be added for every queue onto which the message is
        // enqueued. Finally a cleanup op will be added to decrement
        // the reference associated with the routing.
        message.incrementReference();
        _postCommitDeliveryList.add(new DeliveryDetails(message, queue));
        _messageDelivered = true;
        /*_txnBuffer.enlist(new DeliverMessageOperation(message, queue));
        if (_log.isDebugEnabled())
        {
            _log.debug("Incrementing ref count on message and enlisting cleanup operation - id " +
                       message.getMessageId());
        }
        message.incrementReference();
        _messageDelivered = true;
        _txnBuffer.enlist(new CleanupMessageOperation(message, _returnMessages));
        */
    }

    private void checkAck(long deliveryTag, UnacknowledgedMessageMap unacknowledgedMessageMap) throws AMQException
    {
        if (!unacknowledgedMessageMap.contains(deliveryTag))
        {
            throw new AMQException("Ack with delivery tag " + deliveryTag + " not known for channel");
        }
    }

    public void acknowledgeMessage(long deliveryTag, long lastDeliveryTag, boolean multiple,
                                   UnacknowledgedMessageMap unacknowledgedMessageMap) throws AMQException
    {
        //check that the tag exists to give early failure
        if (!multiple || deliveryTag > 0)
        {
            checkAck(deliveryTag, unacknowledgedMessageMap);
        }
        //we use a single txn op for all acks and update this op
        //as new acks come in. If this is the first ack in the txn
        //we will need to create and enlist the op.
        if (_ackOp == null)
        {
            beginTranIfNecessary();
            _ackOp = new TxAck(unacknowledgedMessageMap);
            _txnBuffer.enlist(_ackOp);
        }
        // update the op to include this ack request
        if (multiple && deliveryTag == 0)
        {
            // if have signalled to ack all, that refers only
            // to all at this time
            _ackOp.update(lastDeliveryTag, multiple);
        }
        else
        {
            _ackOp.update(deliveryTag, multiple);
        }
    }

    public void messageFullyReceived(boolean persistent) throws AMQException
    {
        // Not required in this transactional context
    }

    public void messageProcessed(AMQProtocolSession protocolSession) throws AMQException
    {
        // Not required in this transactional context
    }

    public void beginTranIfNecessary() throws AMQException
    {
        if (!_inTran)
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Starting transaction on message store");
            }
            _messageStore.beginTran(_storeContext);
            _inTran = true;
        }
    }

    public void commit() throws AMQException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Committing transactional context");
        }
        if (_ackOp != null)
        {

            _messageDelivered = true;
            _ackOp.consolidate();
            //already enlisted, after commit will reset regardless of outcome
            _ackOp = null;
        }

        if (_messageDelivered && _inTran)
        {
            _txnBuffer.enlist(new StoreMessageOperation(_messageStore));
        }
        try
        {
            _txnBuffer.commit(_storeContext);
        }
        finally
        {
            _messageDelivered = false;
            _inTran = _messageStore.inTran(_storeContext);
        }

        try
        {
            postCommitDelivery();
        }
        catch (AMQException e)
        {
            // OK so what do we do now...?
            _log.error("Failed to deliver messages following txn commit: " + e, e);
        }
    }

    private void postCommitDelivery() throws AMQException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Performing post commit delivery");
        }
        try
        {
            for (DeliveryDetails dd : _postCommitDeliveryList)
            {
                dd.queue.process(_storeContext, dd.message);
            }
        }
        finally
        {
            _postCommitDeliveryList.clear();
        }

    }
}
