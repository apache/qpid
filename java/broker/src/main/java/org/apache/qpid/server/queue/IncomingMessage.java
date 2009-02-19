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
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.exchange.NoRouteException;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.ArrayList;

public class IncomingMessage implements Filterable<RuntimeException>
{

    /** Used for debugging purposes. */
    private static final Logger _logger = Logger.getLogger(IncomingMessage.class);

    private static final boolean SYNCHED_CLOCKS =
            ApplicationRegistry.getInstance().getConfiguration().getSynchedClocks();

    private final MessagePublishInfo _messagePublishInfo;
    private ContentHeaderBody _contentHeaderBody;
    private AMQMessage _message;
    private final TransactionalContext _txnContext;

    private static final boolean MSG_AUTH = 
        ApplicationRegistry.getInstance().getConfiguration().getMsgAuth();


    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * This is stored during routing, to know the queues to which this message should immediately be
     * delivered. It is <b>cleared after delivery has been attempted</b>. Any persistent record of destinations is done
     * by the message handle.
     */
    private ArrayList<AMQQueue> _destinationQueues;

    private AMQProtocolSession _publisher;
    private TransactionLog _messageStore;
    private long _expiration;
    
    private Exchange _exchange;
    private static MessageFactory MESSAGE_FACTORY = MessageFactory.getInstance();

    public IncomingMessage(final MessagePublishInfo info,
                           final TransactionalContext txnContext,
                           final AMQProtocolSession publisher,
                           TransactionLog messasgeStore)
    {
        _messagePublishInfo = info;
        _txnContext = txnContext;
        _publisher = publisher;
        _messageStore = messasgeStore;
    }

    public void setContentHeaderBody(final ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
        _message = MESSAGE_FACTORY.createMessage(_messageStore, isPersistent());
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

    public void routingComplete(final TransactionLog transactionLog) throws AMQException
    {

        if (isPersistent())
        {
            _txnContext.beginTranIfNecessary();
             // enqueuing the messages ensure that if required the destinations are recorded to a
            // persistent store

            if(_destinationQueues != null)
            {
                for (int i = 0; i < _destinationQueues.size(); i++)
                {
                    transactionLog.enqueueMessage(_txnContext.getStoreContext(),
                            _destinationQueues.get(i), getMessageId());
                }
            }
        }
    }

    public AMQMessage deliverToQueues()
            throws AMQException
    {

        // we get a reference to the destination queues now so that we can clear the
        // transient message data as quickly as possible
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Delivering message " + getMessageId() + " to " + _destinationQueues);
        }

        try
        {
            // first we allow the handle to know that the message has been fully received. This is useful if it is
            // maintaining any calculated values based on content chunks
            _message.setPublishAndContentHeaderBody(_txnContext.getStoreContext(), _messagePublishInfo, getContentHeaderBody());



            _message.setExpiration(_expiration);
            _message.setClientIdentifier(_publisher.getSessionIdentifier());

            // we then allow the transactional context to do something with the message content
            // now that it has all been received, before we attempt delivery
            _txnContext.messageFullyReceived(isPersistent());
            
            AMQShortString userID = getContentHeaderBody().properties instanceof BasicContentHeaderProperties ?
                     ((BasicContentHeaderProperties) getContentHeaderBody().properties).getUserId() : null; 
            
            if (MSG_AUTH && !_publisher.getAuthorizedID().getName().equals(userID == null? "" : userID.toString()))
            {
                throw new UnauthorizedAccessException("Acccess Refused", _message);
            }
            
            if ((_destinationQueues == null) || _destinationQueues.size() == 0)
            {

                if (isMandatory() || isImmediate())
                {
                    throw new NoRouteException("No Route for message", _message);

                }
                else
                {
                    _logger.warn("MESSAGE DISCARDED: No routes for message - " + _message);
                }
            }
            else
            {
                int offset;
                final int queueCount = _destinationQueues.size();
                _message.incrementReference(queueCount);
                if(queueCount == 1)
                {
                    offset = 0;
                }
                else
                {
                    offset = ((int)(_message.getMessageId().longValue())) % queueCount;
                    if(offset < 0)
                    {
                        offset = -offset;
                    }
                }
                for (int i = offset; i < queueCount; i++)
                {
                    // normal deliver so add this message at the end.
                    _txnContext.deliver(_destinationQueues.get(i), _message);
                }
                for (int i = 0; i < offset; i++)
                {
                    // normal deliver so add this message at the end.
                    _txnContext.deliver(_destinationQueues.get(i), _message);
                }
            }

            return _message;
        }
        finally
        {
            // Remove refence for routing process . Reference count should now == delivered queue count
            if(_message != null) _message.decrementReference(_txnContext.getStoreContext());
        }

    }

    public void addContentBodyFrame(final ContentChunk contentChunk)
            throws AMQException
    {

        _bodyLengthReceived += contentChunk.getSize();

        _message.addContentBodyFrame(_txnContext.getStoreContext(), contentChunk, allContentReceived());

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

    /**
     * The message ID will not be assigned until the ContentHeaderBody has arrived.
     * @return
     */
    public Long getMessageId()
    {
        return _message.getMessageId();
    }

    public void setExchange(final Exchange e)
    {
        _exchange = e;
    }

    public void route() throws AMQException
    {
        _exchange.route(this);
    }

    public void enqueue(final ArrayList<AMQQueue> queues)
    {
        _destinationQueues = queues;
    }
}
