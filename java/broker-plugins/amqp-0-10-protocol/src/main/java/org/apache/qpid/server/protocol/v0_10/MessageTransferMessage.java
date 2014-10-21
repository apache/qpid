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
package org.apache.qpid.server.protocol.v0_10;

import java.nio.ByteBuffer;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.AbstractServerMessageImpl;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.transport.Header;


public class MessageTransferMessage extends AbstractServerMessageImpl<MessageTransferMessage, MessageMetaData_0_10>
{

    public MessageTransferMessage(StoredMessage<MessageMetaData_0_10> storeMessage, Object connectionRef)
    {
        super(storeMessage, connectionRef);
    }

    private MessageMetaData_0_10 getMetaData()
    {
        return getStoredMessage().getMetaData();
    }

    public String getInitialRoutingAddress()
    {
        return getMetaData().getRoutingKey();
    }

    public AMQMessageHeader getMessageHeader()
    {
        return getMetaData().getMessageHeader();
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

    public long getArrivalTime()
    {
        return getMetaData().getArrivalTime();
    }

    public Header getHeader()
    {
        return getMetaData().getHeader();
    }

    public ByteBuffer getBody()
    {
        return  getContent(0, (int)getSize());
    }
}
