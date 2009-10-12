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
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ContentHeaderBodyAdapter;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class IncomingMessage implements Filterable, InboundMessage, EnqueableMessage, BodyContentHolder
{

    /** Used for debugging purposes. */
    private static final Logger _logger = Logger.getLogger(IncomingMessage.class);

    private static final boolean SYNCHED_CLOCKS =
            ApplicationRegistry.getInstance().getConfiguration().getSynchedClocks();

    private final MessagePublishInfo _messagePublishInfo;
    private ContentHeaderBody _contentHeaderBody;

    private AMQMessageHandle _messageHandle;
    private final Long _messageId;


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

    private long _expiration;

    private Exchange _exchange;
    private AMQMessageHeader _messageHeader;


    private int _receivedChunkCount = 0;
    private List<ContentChunk> _contentChunks = new ArrayList<ContentChunk>();


    public IncomingMessage(final Long messageId,
                           final MessagePublishInfo info,
                           final AMQProtocolSession publisher)
    {
        _messageId = messageId;
        _messagePublishInfo = info;
    }

    public void setContentHeaderBody(final ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
        _messageHeader = new ContentHeaderBodyAdapter(contentHeaderBody);
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

    public MessageMetaData routingComplete(final MessageStore store,
                                final MessageHandleFactory factory) throws AMQException
    {
        _messageHandle = factory.createMessageHandle(_messageId, store, isPersistent());
        return _messageHandle.setPublishAndContentHeaderBody(_messagePublishInfo, _contentHeaderBody);
    }


    public ArrayList<AMQQueue> getDestinationQueues()
    {
        return _destinationQueues;
    }


    public AMQMessageHandle getMessageHandle()
    {
        return _messageHandle;
    }


    public int addContentBodyFrame(final ContentChunk contentChunk)
            throws AMQException
    {

        _bodyLengthReceived += contentChunk.getSize();
        _contentChunks.add(contentChunk);
        _messageHandle.addContentBodyFrame(contentChunk, allContentReceived());
        return _receivedChunkCount++;
    }

    public boolean allContentReceived()
    {
        return (_bodyLengthReceived == getContentHeader().bodySize);
    }

    public AMQShortString getExchange()
    {
        return _messagePublishInfo.getExchange();
    }

    public String getRoutingKey()
    {
        return _messagePublishInfo.getRoutingKey() == null ? null : _messagePublishInfo.getRoutingKey().toString();
    }

    public String getBinding()
    {
        return _messagePublishInfo.getRoutingKey() == null ? null : _messagePublishInfo.getRoutingKey().toString();
    }


    public boolean isMandatory()
    {
        return _messagePublishInfo.isMandatory();
    }


    public boolean isImmediate()
    {
        return _messagePublishInfo.isImmediate();
    }

    public ContentHeaderBody getContentHeader()
    {
        return _contentHeaderBody;
    }


    public AMQMessageHeader getMessageHeader()
    {
        return _messageHeader;
    }

    public boolean isPersistent()
    {
        return getContentHeader().properties instanceof BasicContentHeaderProperties &&
             ((BasicContentHeaderProperties) getContentHeader().properties).getDeliveryMode() ==
                                                             BasicContentHeaderProperties.PERSISTENT;
    }

    public boolean isRedelivered()
    {
        return false;
    }

    public long getSize()
    {
        return getContentHeader().bodySize;
    }

    public Long getMessageNumber()
    {
        return _messageId;
    }

    public void setExchange(final Exchange e)
    {
        _exchange = e;
    }

    public void route()
    {
        enqueue(_exchange.route(this));

    }

    public void enqueue(final ArrayList<AMQQueue> queues)
    {
        _destinationQueues = queues;
    }

    public MessagePublishInfo getMessagePublishInfo()
    {
        return _messagePublishInfo;
    }

    public long getExpiration()
    {
        return _expiration;
    }

    public int getReceivedChunkCount()
    {
        return _receivedChunkCount;
    }


    public int getBodyCount() throws AMQException
    {
        return _contentChunks.size();
    }

    public ContentChunk getContentChunk(int index) throws IllegalArgumentException, AMQException
    {
        return _contentChunks.get(index);
    }
}
