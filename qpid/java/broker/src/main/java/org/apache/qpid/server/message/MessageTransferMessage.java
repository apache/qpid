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
package org.apache.qpid.server.message;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.transport.Header;

import java.nio.ByteBuffer;


public class MessageTransferMessage extends AbstractServerMessageImpl<MessageMetaData_0_10> implements InboundMessage
{

    private Object _connectionRef;

    public MessageTransferMessage(StoredMessage<MessageMetaData_0_10> storeMessage, Object connectionRef)
    {
        super(storeMessage);
        _connectionRef = connectionRef;
    }

    private MessageMetaData_0_10 getMetaData()
    {
        return getStoredMessage().getMetaData();
    }

    public String getRoutingKey()
    {
        return getMetaData().getRoutingKey();
    }

    public AMQShortString getRoutingKeyShortString()
    {
        return AMQShortString.valueOf(getRoutingKey());
    }

    public AMQMessageHeader getMessageHeader()
    {
        return getMetaData().getMessageHeader();
    }

    public boolean isPersistent()
    {
        return getMetaData().isPersistent();
    }


    public boolean isRedelivered()
    {
        // The *Message* is never redelivered, only queue entries are... this is here so that filters
        // can run against the message on entry to an exchange
        return false;
    }

    public long getSize()
    {

        return getMetaData().getSize();
    }

    public boolean isImmediate()
    {
        return getMetaData().isImmediate();
    }

    public long getExpiration()
    {
        return getMetaData().getExpiration();
    }

    public MessageReference<MessageTransferMessage> newReference()
    {
        return new TransferMessageReference(this);
    }

    public long getMessageNumber()
    {
        return getStoredMessage().getMessageNumber();
    }

    public long getArrivalTime()
    {
        return getMetaData().getArrivalTime();
    }

    public int getContent(ByteBuffer buf, int offset)
    {
        return getStoredMessage().getContent(offset, buf);
    }


    public ByteBuffer getContent(int offset, int size)
    {
        return getStoredMessage().getContent(offset,size);
    }

    public Header getHeader()
    {
        return getMetaData().getHeader();
    }

    public ByteBuffer getBody()
    {

        return  getContent(0, (int)getSize());
    }

    public Object getConnectionReference()
    {
        return _connectionRef;
    }

}
