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

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.StoredMessage;

@PluggableService
public class MessageMetaDataType_0_10 implements MessageMetaDataType<MessageMetaData_0_10>
{

    public static final int TYPE = 1;
    public static final String V0_10 = "v0_10";

    @Override
    public int ordinal()
    {
        return TYPE;
    }

    @Override
    public MessageMetaData_0_10 createMetaData(ByteBuffer buf)
    {
        return MessageMetaData_0_10.FACTORY.createMetaData(buf);
    }

    @Override
    public ServerMessage<MessageMetaData_0_10> createMessage(StoredMessage<MessageMetaData_0_10> msg)
    {
        return new MessageTransferMessage(msg, null);
    }

    public int hashCode()
    {
        return ordinal();
    }

    public boolean equals(Object o)
    {
        return o != null && o.getClass() == getClass();
    }

    @Override
    public String getType()
    {
        return V0_10;
    }
}
