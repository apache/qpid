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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.store.StoredMessage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class IncomingMessage implements Filterable, InboundMessage, EnqueableMessage, MessageContentSource
{

    /** Used for debugging purposes. */
    private static final Logger _logger = Logger.getLogger(IncomingMessage.class);

    private final MessagePublishInfo _messagePublishInfo;
    private ContentHeaderBody _contentHeaderBody;


    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * This is stored during routing, to know the queues to which this message should immediately be
     * delivered. It is <b>cleared after delivery has been attempted</b>. Any persistent record of destinations is done
     * by the message handle.
     */
    private List<? extends BaseQueue> _destinationQueues;

    private long _expiration;

    private Exchange _exchange;

    private List<ContentChunk> _contentChunks = new ArrayList<ContentChunk>();

    // we keep both the original meta data object and the store reference to it just in case the
    // store would otherwise flow it to disk

    private MessageMetaData _messageMetaData;

    private StoredMessage<MessageMetaData> _storedMessageHandle;
    private Object _connectionReference;


    public IncomingMessage(
            final MessagePublishInfo info
    )
    {
        this(info, null);
    }

    public IncomingMessage(MessagePublishInfo info, Object reference)
    {
        _messagePublishInfo = info;
        _connectionReference = reference;
    }

    public void setContentHeaderBody(final ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public void setExpiration()
    {
        _expiration = ((BasicContentHeaderProperties) _contentHeaderBody.getProperties()).getExpiration();
    }

    public MessageMetaData headersReceived(long currentTime)
    {
        _messageMetaData = new MessageMetaData(_messagePublishInfo, _contentHeaderBody, 0, currentTime);
        return _messageMetaData;
    }


    public List<? extends BaseQueue> getDestinationQueues()
    {
        return _destinationQueues;
    }

    public void addContentBodyFrame(final ContentChunk contentChunk) throws AMQException
    {
        _bodyLengthReceived += contentChunk.getSize();
        _contentChunks.add(contentChunk);
    }

    public boolean allContentReceived()
    {
        return (_bodyLengthReceived == getContentHeader().getBodySize());
    }

    public AMQShortString getExchange()
    {
        return _messagePublishInfo.getExchange();
    }

    public AMQShortString getRoutingKeyShortString()
    {
        return _messagePublishInfo.getRoutingKey();
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
        return _messageMetaData.getMessageHeader();
    }

    public boolean isPersistent()
    {
        return getContentHeader().getProperties() instanceof BasicContentHeaderProperties &&
             ((BasicContentHeaderProperties) getContentHeader().getProperties()).getDeliveryMode() ==
                                                             BasicContentHeaderProperties.PERSISTENT;
    }

    public boolean isRedelivered()
    {
        return false;
    }


    public long getSize()
    {
        return getContentHeader().getBodySize();
    }

    public long getMessageNumber()
    {
        return _storedMessageHandle.getMessageNumber();
    }

    public void setExchange(final Exchange e)
    {
        _exchange = e;
    }

    public void route()
    {
        enqueue(_exchange.route(this));

    }

    public void enqueue(final List<? extends BaseQueue> queues)
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

    public int getBodyCount() throws AMQException
    {
        return _contentChunks.size();
    }

    public ContentChunk getContentChunk(int index)
    {
        return _contentChunks.get(index);
    }


    public int getContent(ByteBuffer buf, int offset)
    {
        int pos = 0;
        int written = 0;
        for(ContentChunk cb : _contentChunks)
        {
            ByteBuffer data = ByteBuffer.wrap(cb.getData());
            if(offset+written >= pos && offset < pos + data.limit())
            {
                ByteBuffer src = data.duplicate();
                src.position(offset+written - pos);
                src = src.slice();

                if(buf.remaining() < src.limit())
                {
                    src.limit(buf.remaining());
                }
                int count = src.limit();
                buf.put(src);
                written += count;
                if(buf.remaining() == 0)
                {
                    break;
                }
            }
            pos+=data.limit();
        }
        return written;

    }


    public ByteBuffer getContent(int offset, int size)
    {
        ByteBuffer buf = ByteBuffer.allocate(size);
        getContent(buf,offset);
        buf.flip();
        return buf;
    }

    public void setStoredMessage(StoredMessage<MessageMetaData> storedMessageHandle)
    {
        _storedMessageHandle = storedMessageHandle;
    }

    public StoredMessage<MessageMetaData> getStoredMessage()
    {
        return _storedMessageHandle;
    }

    public Object getConnectionReference()
    {
        return _connectionReference;
    }

    public MessageMetaData getMessageMetaData()
    {
        return _messageMetaData;
    }
}
