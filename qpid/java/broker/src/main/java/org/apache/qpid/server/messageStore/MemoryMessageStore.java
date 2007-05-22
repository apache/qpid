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
package org.apache.qpid.server.messageStore;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

import javax.transaction.xa.Xid;

import org.apache.commons.configuration.Configuration;

import org.apache.log4j.Logger;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.exception.*;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.txn.MemoryDequeueRecord;
import org.apache.qpid.server.txn.MemoryEnqueueRecord;
import org.apache.qpid.server.txn.TransactionManager;
import org.apache.qpid.server.txn.TransactionRecord;
import org.apache.qpid.server.virtualhost.VirtualHost;

/**
 * This a simple in-memory implementation of a message store i.e. nothing is persisted
 * <p/>
 * Created by Arnaud Simon
 * Date: 26-Apr-2007
 * Time: 08:23:45
 */
public class MemoryMessageStore implements MessageStore
{
    // ========================================================================
    // Static Constants
    // ========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(MemoryMessageStore.class);

    // The table of message with its corresponding stream containing the message body
    private Map<StorableMessage, ByteArrayOutputStream> _stagedMessages;
    // The queue/messages association
    protected Map<StorableQueue, List<StorableMessage>> _queueMap;
    // the message ID
    private long _messageID = 0;
    // The transaction manager
    private TransactionManager _txm;

    // ========================================================================
    // Interface MessageStore
    // ========================================================================

    public void removeExchange(Exchange exchange) throws InternalErrorException
    {
        // do nothing this is inmemory
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, StorableQueue queue, FieldTable args)
        throws InternalErrorException
    {
        // do nothing this is inmemory
    }

    public void createExchange(Exchange exchange) throws InternalErrorException
    {
        // do nothing this is inmemory
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, StorableQueue queue, FieldTable args)
        throws InternalErrorException
    {
        // do nothing this is inmemory
    }

    public void configure(VirtualHost virtualHost, TransactionManager tm, String base, Configuration config)
        throws InternalErrorException, IllegalArgumentException
    {
        _log.info("Configuring memory message store");
        // Initialise the maps
        _stagedMessages = new HashMap<StorableMessage, ByteArrayOutputStream>();
        _queueMap = new HashMap<StorableQueue, List<StorableMessage>>();
        _txm = tm;
        _txm.configure(this, "txn", config);
    }

    public void close() throws InternalErrorException
    {
        _log.info("Closing memory message store");
        _stagedMessages.clear();
        _queueMap.clear();
    }

    public void createQueue(StorableQueue queue) throws InternalErrorException, QueueAlreadyExistsException
    {
        if (_queueMap.containsKey(queue))
        {
            throw new QueueAlreadyExistsException("queue " + queue + " already exists");
        }
        // add this queue into the map
        _queueMap.put(queue, new LinkedList<StorableMessage>());
    }

    public void destroyQueue(StorableQueue queue) throws InternalErrorException, QueueDoesntExistException
    {
        if (!_queueMap.containsKey(queue))
        {
            throw new QueueDoesntExistException("queue " + queue + " does not exist");
        }
        // remove this queue from the map
        _queueMap.remove(queue);
    }

    public void stage(StorableMessage m) throws InternalErrorException, MessageAlreadyStagedException
    {
        if (_stagedMessages.containsKey(m))
        {
            throw new MessageAlreadyStagedException("message " + m + " already staged");
        }

        _stagedMessages.put(m, new ByteArrayOutputStream());
        m.staged();
    }

    public void appendContent(StorableMessage m, byte[] data, int offset, int size)
        throws InternalErrorException, MessageDoesntExistException
    {
        if (!_stagedMessages.containsKey(m))
        {
            throw new MessageDoesntExistException("message " + m + " has not been staged");
        }

        _stagedMessages.get(m).write(data, offset, size);
    }

    public byte[] loadContent(StorableMessage m, int offset, int size)
        throws InternalErrorException, MessageDoesntExistException
    {
        if (!_stagedMessages.containsKey(m))
        {
            throw new MessageDoesntExistException("message " + m + " has not been staged");
        }

        byte[] result = new byte[size];
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(_stagedMessages.get(m).toByteArray(), offset, size);
        buf.get(result);

        return result;
    }

    public void destroy(StorableMessage m) throws InternalErrorException, MessageDoesntExistException
    {
        if (!_stagedMessages.containsKey(m))
        {
            throw new MessageDoesntExistException("message " + m + " has not been staged");
        }

        _stagedMessages.remove(m);
    }

    public void enqueue(Xid xid, StorableMessage m, StorableQueue queue)
        throws InternalErrorException, QueueDoesntExistException, InvalidXidException, UnknownXidException,
            MessageDoesntExistException
    {
        if (xid != null)
        {
            // this is a tx operation
            TransactionRecord enqueueRecord = new MemoryEnqueueRecord(m, queue);
            _txm.getTransaction(xid).addRecord(enqueueRecord);
        }
        else
        {
            if (!_stagedMessages.containsKey(m))
            {
                try
                {
                    stage(m);
                }
                catch (MessageAlreadyStagedException e)
                {
                    throw new InternalErrorException(e.getMessage(), e);
                }

                appendContent(m, m.getData(), 0, m.getPayloadSize());
            }

            if (!_queueMap.containsKey(queue))
            {
                throw new QueueDoesntExistException("queue " + queue + " dos not exist");
            }

            _queueMap.get(queue).add(m);
            m.enqueue(queue);
        }
    }

    public void dequeue(Xid xid, StorableMessage m, StorableQueue queue)
        throws InternalErrorException, QueueDoesntExistException, InvalidXidException, UnknownXidException
    {
        if (xid != null)
        {
            // this is a tx operation
            TransactionRecord dequeueRecord = new MemoryDequeueRecord(m, queue);
            _txm.getTransaction(xid).addRecord(dequeueRecord);
        }
        else
        {
            if (!_queueMap.containsKey(queue))
            {
                throw new QueueDoesntExistException("queue " + queue + " dos not exist");
            }

            m.dequeue(queue);
            _queueMap.get(queue).remove(m);
            if (!m.isEnqueued())
            {
                // we can delete this message
                _stagedMessages.remove(m);
            }
        }
    }

    public Collection<StorableQueue> getAllQueues() throws InternalErrorException
    {
        return _queueMap.keySet();
    }

    public Collection<StorableMessage> getAllMessages(StorableQueue queue) throws InternalErrorException
    {
        return _queueMap.get(queue);
    }

    public long getNewMessageId()
    {
        return _messageID++;
    }


    public ContentHeaderBody getContentHeaderBody(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        // do nothing this is only used during recovery
        return null;
    }

    public MessagePublishInfo getMessagePublishInfo(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
       // do nothing this is only used during recovery
        return null;
    }
}
