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
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ack.TxAck;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.*;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

import java.util.List;
import java.util.ArrayList;

/** A transactional context that only supports local transactions. */
public class LocalTransactionalContext implements TransactionalContext
{
    private static final Logger _log = Logger.getLogger(LocalTransactionalContext.class);

    private final TxnBuffer _txnBuffer = new TxnBuffer();

    private final List<DeliveryAction> _postCommitDeliveryList = new ArrayList<DeliveryAction>();

    /**
     * We keep hold of the ack operation so that we can consolidate acks, i.e. multiple acks within a txn are
     * consolidated into a single operation
     */
    private TxAck _ackOp;

    private boolean _inTran = false;

    /** Are there messages to deliver. NOT Has the message been delivered */
    private boolean _messageDelivered = false;
    private final AMQChannel _channel;


    private abstract class DeliveryAction
    {

        abstract public void process() throws AMQException;

    }

    private class RequeueAction extends DeliveryAction
    {
        public QueueEntry entry;

        public RequeueAction(QueueEntry entry)
        {
            this.entry = entry;
        }

        public void process() throws AMQException
        {
            entry.requeue(getStoreContext());
        }
    }

    private class PublishAction extends DeliveryAction
    {
        private final AMQQueue _queue;
        private final AMQMessage _message;

        public PublishAction(final AMQQueue queue, final AMQMessage message)
        {
            _queue = queue;
            _message = message;
        }

        public void process() throws AMQException
        {

            _message.incrementReference();
            try
            {
                QueueEntry entry = _queue.enqueue(getStoreContext(),_message);

                if(entry.immediateAndNotDelivered())
                {
                    getReturnMessages().add(new NoConsumersException(_message));
                }
            }
            finally
            {
                _message.decrementReference(getStoreContext());
            }
        }
    }

    public LocalTransactionalContext(final AMQChannel channel)
    {
        _channel = channel;
    }

    public StoreContext getStoreContext()
    {
        return _channel.getStoreContext();
    }

    public List<RequiredDeliveryException> getReturnMessages()
    {
        return _channel.getReturnMessages();
    }

    public MessageStore getMessageStore()
    {
        return _channel.getMessageStore();
    }


    public void rollback() throws AMQException
    {
        _txnBuffer.rollback(getStoreContext());
        // Hack to deal with uncommitted non-transactional writes
        if (getMessageStore().inTran(getStoreContext()))
        {
            getMessageStore().abortTran(getStoreContext());
            _inTran = false;
        }

        _postCommitDeliveryList.clear();
    }

    public void deliver(final AMQQueue queue, AMQMessage message) throws AMQException
    {
        // A publication will result in the enlisting of several
        // TxnOps. The first is an op that will store the message.
        // Following that (and ordering is important), an op will
        // be added for every queue onto which the message is
        // enqueued.
        _postCommitDeliveryList.add(new PublishAction(queue, message));
        _messageDelivered = true;

    }

    public void requeue(QueueEntry entry) throws AMQException
    {
        _postCommitDeliveryList.add(new RequeueAction(entry));
        _messageDelivered = true;

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
        // check that the tag exists to give early failure
        if (!multiple || (deliveryTag > 0))
        {
            checkAck(deliveryTag, unacknowledgedMessageMap);
        }
        // we use a single txn op for all acks and update this op
        // as new acks come in. If this is the first ack in the txn
        // we will need to create and enlist the op.
        if (_ackOp == null)
        {            
            _ackOp = new TxAck(unacknowledgedMessageMap);
            _txnBuffer.enlist(_ackOp);
        }
        // update the op to include this ack request
        if (multiple && (deliveryTag == 0))
        {
            // if have signalled to ack all, that refers only
            // to all at this time
            _ackOp.update(lastDeliveryTag, multiple);
        }
        else
        {
            _ackOp.update(deliveryTag, multiple);
        }
        if(!_inTran && _ackOp.checkPersistent())
        {
            beginTranIfNecessary();
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
                _log.debug("Starting transaction on message store: " + this);
            }

            getMessageStore().beginTran(getStoreContext());
            _inTran = true;
        }
    }

    public void commit() throws AMQException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Committing transactional context: " + this);
        }

        if (_ackOp != null)
        {

            _messageDelivered = true;
            _ackOp.consolidate();
            // already enlisted, after commit will reset regardless of outcome
            _ackOp = null;
        }

        if (_messageDelivered && _inTran)
        {
            _txnBuffer.enlist(new StoreMessageOperation(getMessageStore()));
        }
        // fixme fail commit here ... QPID-440
        try
        {
            _txnBuffer.commit(getStoreContext());
        }
        finally
        {
            _messageDelivered = false;
            _inTran = getMessageStore().inTran(getStoreContext());
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
            for (DeliveryAction dd : _postCommitDeliveryList)
            {
                dd.process();
            }
        }
        finally
        {
            _postCommitDeliveryList.clear();
        }
    }
}
