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
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.exchange.NoRouteException;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.ClientProperties;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;

public class IncomingMessage implements Filterable<RuntimeException>
{

    /** Used for debugging purposes. */
    private static final Logger _logger = Logger.getLogger(IncomingMessage.class);

    private static final boolean SYNCHED_CLOCKS =
            ApplicationRegistry.getInstance().getConfiguration().getBoolean("advanced.synced-clocks", false);

    private final MessagePublishInfo _messagePublishInfo;
    private ContentHeaderBody _contentHeaderBody;
    private AMQMessageHandle _messageHandle;
    private final Long _messageId;
    private final TransactionalContext _txnContext;



    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * This is stored during routing, to know the queues to which this message should immediately be
     * delivered. It is <b>cleared after delivery has been attempted</b>. Any persistent record of destinations is done
     * by the message handle.
     */
    private Collection<AMQQueue> _destinationQueues;

    private AMQProtocolSession _publisher;
    private MessageStore _messageStore;
    private long _expiration;

    private Exchange _exchange;


    public IncomingMessage(final Long messageId,
                           final MessagePublishInfo info,
                           final TransactionalContext txnContext,
                           final AMQProtocolSession publisher)
    {
        _messageId = messageId;
        _messagePublishInfo = info;
        _txnContext = txnContext;
        _publisher = publisher;

    }

    public void setContentHeaderBody(final ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public void setExpiration()
    {
            long expiration =
                    ((BasicContentHeaderProperties) _contentHeaderBody.properties).getExpiration();
            long timestamp =
                    ((BasicContentHeaderProperties) _contentHeaderBody.properties).getTimestamp();

            if (SYNCHED_CLOCKS)
            {
                _expiration = expiration;
            }
            else
            {
                // Update TTL to be in broker time.
                if (expiration != 0L)
                {
                    if (timestamp != 0L)
                    {
                        // todo perhaps use arrival time
                        long diff = (System.currentTimeMillis() - timestamp);

                        if ((diff > 1000L) || (diff < 1000L))
                        {
                            _expiration = expiration + diff;
                        }
                    }
                }
            }

    }

    public void routingComplete(final MessageStore store,
                                final MessageHandleFactory factory) throws AMQException
    {

        final boolean persistent = isPersistent();
        _messageHandle = factory.createMessageHandle(_messageId, store, persistent);
        if (persistent)
        {
            _txnContext.beginTranIfNecessary();
             // enqueuing the messages ensure that if required the destinations are recorded to a
            // persistent store

            if(_destinationQueues != null)
            {
                for (AMQQueue q : _destinationQueues)
                {
                    if(q.isDurable())
                    {

                        _messageStore.enqueueMessage(_txnContext.getStoreContext(), q, _messageId);
                    }
                }
            }

        }




    }

    public AMQMessage deliverToQueues()
            throws AMQException
    {

        // we get a reference to the destination queues now so that we can clear the
        // transient message data as quickly as possible
        Collection<AMQQueue> destinationQueues = _destinationQueues;
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Delivering message " + _messageId + " to " + destinationQueues);
        }

        AMQMessage message = null;

        try
        {
            // first we allow the handle to know that the message has been fully received. This is useful if it is
            // maintaining any calculated values based on content chunks
            _messageHandle.setPublishAndContentHeaderBody(_txnContext.getStoreContext(),
                                                          _messagePublishInfo, getContentHeaderBody());


            message = new AMQMessage(_messageHandle,_txnContext.getStoreContext(), _messagePublishInfo);

            message.setExpiration(_expiration);
            message.setClientIdentifier(_publisher.getSessionIdentifier());




            if ((destinationQueues == null) || destinationQueues.isEmpty())
            {

                if (isMandatory() || isImmediate())
                {
                    throw new NoRouteException("No Route for message", message);

                }
                else
                {
                    _logger.warn("MESSAGE DISCARDED: No routes for message - " + message);
                }
            }
            else
            {
                for (AMQQueue q : destinationQueues)
                {
                    // Increment the references to this message for each queue delivery.
                    message.incrementReference();
                    // normal deliver so add this message at the end.
                    _txnContext.deliver(q, message);
                }
            }

            // we then allow the transactional context to do something with the message content
            // now that it has all been received, before we attempt delivery
            _txnContext.messageFullyReceived(isPersistent());

            return message;
        }
        finally
        {
            // Remove refence for routing process . Reference count should now == delivered queue count
            if(message != null) message.decrementReference(_txnContext.getStoreContext());
        }

    }

    public void addContentBodyFrame(final ContentChunk contentChunk)
            throws AMQException
    {

        _bodyLengthReceived += contentChunk.getSize();

        _messageHandle.addContentBodyFrame(_txnContext.getStoreContext(), contentChunk, allContentReceived());

    }

    public boolean allContentReceived()
    {
        return (_bodyLengthReceived == getContentHeaderBody().bodySize);
    }

    public AMQShortString getExchange() throws AMQException
    {
        return _messagePublishInfo.getExchange();
    }

    public AMQShortString getRoutingKey() throws AMQException
    {
        return _messagePublishInfo.getRoutingKey();
    }

    public boolean isMandatory() throws AMQException
    {
        return _messagePublishInfo.isMandatory();
    }


    public boolean isImmediate() throws AMQException
    {
        return _messagePublishInfo.isImmediate();
    }

    
    public void enqueue(final AMQQueue q) throws AMQException
    {
        if(_destinationQueues == null)
        {
            _destinationQueues = new ArrayList<AMQQueue>();
        }
        _destinationQueues.add(q);
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }


    public boolean isPersistent()
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        return getContentHeaderBody().properties instanceof BasicContentHeaderProperties &&
             ((BasicContentHeaderProperties) getContentHeaderBody().properties).getDeliveryMode() == 2;
    }

    public boolean isRedelivered()
    {
        return false;
    }

    public void setMessageStore(final MessageStore messageStore)
    {
        _messageStore = messageStore;
    }

    public Long getMessageId()
    {
        return _messageId;
    }

    public void setExchange(final Exchange e)
    {
        _exchange = e;
    }

    public void route() throws AMQException
    {
        _exchange.route(this);
    }

    public void enqueue(final Collection<AMQQueue> queues)
    {
        _destinationQueues = queues;
    }
}
