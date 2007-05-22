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
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.messageStore.StorableMessage;
import org.apache.qpid.server.messageStore.JDBCStore;
import org.apache.qpid.server.exception.InternalErrorException;
import org.apache.qpid.server.exception.MessageDoesntExistException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.log4j.Logger;

import javax.transaction.xa.Xid;
import java.util.List;
import java.util.LinkedList;
import java.nio.ByteBuffer;

/**
 * Created by Arnaud Simon
 * Date: 25-Apr-2007
 * Time: 14:26:34
 */
public class StorableMessageHandle implements AMQMessageHandle
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(StorableMessageHandle.class);

    //========================================================================
    // Instance Fields
    //========================================================================
    // the message store
    final private MessageStore _messageStore;
    // A reference on the message itself
    final private StorableMessage _message;
    // the message payload
    private byte[] _payload;
    // a buffer to write the payload
    ByteBuffer _buffer;
    // the ContentHeaderBody
    private ContentHeaderBody _contentHeaderBody;
    // the arrival time
    private long _arrivalTime;
    // Specify if this messag is redelivered
    private boolean _redelivered;
    // MessagePublishInfo
    private MessagePublishInfo _messagePublishInfo;
    // list of chunks
    private List<ContentChunk> _chunks;

    //========================================================================
    // Constructors
    //========================================================================

    public StorableMessageHandle(MessageStore messageStore, StorableMessage message)
    {
        _messageStore = messageStore;
        _message = message;
    }

    //========================================================================
    // Interface AMQMessageHandle
    //========================================================================
    public ContentHeaderBody getContentHeaderBody(StoreContext context, Long messageId)
            throws
            AMQException
    {
        if (_contentHeaderBody == null)
        {
            // load it from the store
            try
            {
                _contentHeaderBody = _messageStore.getContentHeaderBody(_message);
            } catch (Exception e)
            {
                throw new AMQException(AMQConstant.INTERNAL_ERROR, e.getMessage(), e);
            }
        }
        return _contentHeaderBody;
    }

    public int getBodyCount(StoreContext context, Long messageId)
            throws
            AMQException
    {
       if (_chunks == null )
       {
           if(_message.isStaged() )
           {
              loadChunks();
           }
           else
           {
               return 0;
           }
      }
        return _chunks.size();
    }

    public long getBodySize(StoreContext context, Long messageId)
            throws
            AMQException
    {
        return _payload.length;
    }

    public ContentChunk getContentChunk(StoreContext context, Long messageId, int index)
            throws
            IllegalArgumentException,
            AMQException
    {
        if (_chunks == null)
        {
            loadChunks();
        }
        return _chunks.get(index);
    }

    private void loadChunks()
            throws
            AMQException
    {
        try
            {
                _chunks = new LinkedList<ContentChunk>();
            byte[] underlying = _messageStore.loadContent(_message, 1, 0);
            final int size = underlying.length;
            final org.apache.mina.common.ByteBuffer data =
                    org.apache.mina.common.ByteBuffer.wrap(underlying);
            ContentChunk cb = new ContentChunk()
            {

                public int getSize()
                {
                    return size;
                }

                public org.apache.mina.common.ByteBuffer getData()
                {
                    return data;
                }

                public void reduceToFit()
                {

                }
            };
            _chunks.add(cb);
        } catch (Exception e)
        {
            throw new AMQException(AMQConstant.INTERNAL_ERROR, e.getMessage(), e);
        }
    }

    public void addContentBodyFrame(StoreContext storeContext, Long messageId, ContentChunk contentBody, boolean isLastContentBody)
            throws
            AMQException
    {
        if (_chunks == null)
        {
            _chunks = new LinkedList<ContentChunk>();
        }
        _chunks.add(contentBody);
        // if rquired this message can be added to the store
        //_messageStore.appendContent(_message, _payload, 0, 10);

    }

    public MessagePublishInfo getMessagePublishInfo(StoreContext context, Long messageId)
            throws
            AMQException
    {
        if (_messagePublishInfo == null)
        {
            // read it from the store
            try
            {
                _messagePublishInfo = _messageStore.getMessagePublishInfo(_message);
            } catch (Exception e)
            {
                throw new AMQException(AMQConstant.INTERNAL_ERROR, e.getMessage(), e);
            }
        }
        return _messagePublishInfo;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }

    public void setRedelivered(boolean redelivered)
    {
        _redelivered = redelivered;
    }

    public boolean isPersistent(StoreContext context, Long messageId)
            throws
            AMQException
    {
        return _contentHeaderBody.properties instanceof BasicContentHeaderProperties &&
                ((BasicContentHeaderProperties) _contentHeaderBody.properties).getDeliveryMode() == 2;
    }

    public void setPublishAndContentHeaderBody(StoreContext storeContext, Long messageId,
                                               MessagePublishInfo messagePublishInfo,
                                               ContentHeaderBody contentHeaderBody)
            throws
            AMQException
    {
        _contentHeaderBody = contentHeaderBody;
        _arrivalTime = System.currentTimeMillis();
        _messagePublishInfo = messagePublishInfo;
    }

    public void removeMessage(StoreContext storeContext, Long messageId)
            throws
            AMQException
    {
        //  This is already handled by the store but we can possibly do:
        // _messageStore.destroy(_message);
    }

    public void enqueue(StoreContext storeContext, Long messageId, AMQQueue queue)
            throws
            AMQException
    {
        try
        {
            if (queue.isDurable())
            {
                _messageStore.enqueue((Xid) storeContext.getPayload(), _message, queue);
            }
        } catch (Exception e)
        {
            throw new AMQException(null, "PRoblem during message enqueue", e);
        }
    }

    public void dequeue(StoreContext storeContext, Long messageId, AMQQueue queue)
            throws
            AMQException
    {
        try
        {
            if (queue.isDurable())
            {
                _messageStore.dequeue((Xid) storeContext.getPayload(), _message, queue);
            }
        } catch (Exception e)
        {
            throw new AMQException(null, "PRoblem during message dequeue", e);
        }
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

    public byte[] getMessagePayload()
    {
        if (_payload == null)
        {
            int bodySize = (int) _contentHeaderBody.bodySize;
            _payload = new byte[bodySize];
            _buffer = ByteBuffer.wrap(_payload);
            for (ContentChunk contentBody : _chunks)
            {
                int chunkSize = contentBody.getSize();
                byte[] chunk = new byte[chunkSize];
                contentBody.getData().get(chunk);
                _buffer.put(chunk);
            }
        }
        return _payload;
    }
}
