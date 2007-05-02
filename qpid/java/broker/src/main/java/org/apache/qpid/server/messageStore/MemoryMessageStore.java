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

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.txn.TransactionManager;
import org.apache.qpid.server.exception.*;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.commons.configuration.Configuration;

import javax.transaction.xa.Xid;
import java.util.Collection;

/**
 * Created by Arnaud Simon
 * Date: 26-Apr-2007
 * Time: 08:23:45
 */
public class MemoryMessageStore  implements MessageStore
{

    public void removeExchange(Exchange exchange)
            throws
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, StorableQueue queue, FieldTable args)
          throws
          InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void createExchange(Exchange exchange)
            throws
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, StorableQueue queue, FieldTable args)
            throws
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void configure(VirtualHost virtualHost, TransactionManager tm, String base, Configuration config)
            throws
            InternalErrorException,
            IllegalArgumentException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void close()
            throws
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void createQueue(StorableQueue queue)
            throws
            InternalErrorException,
            QueueAlreadyExistsException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void destroyQueue(StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void stage(StorableMessage m)
            throws
            InternalErrorException,
            MessageAlreadyStagedException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void appendContent(StorableMessage m, byte[] data, int offset, int size)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public byte[] loadContent(StorableMessage m, int offset, int size)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        return new byte[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void destroy(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void enqueue(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException,
            MessageDoesntExistException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void dequeue(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Collection<StorableQueue> getAllQueues()
            throws
            InternalErrorException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Collection<StorableMessage> getAllMessages(StorableQueue queue)
            throws
            InternalErrorException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getNewMessageId()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
