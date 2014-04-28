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
package org.apache.qpid.server.protocol.v0_8;

import java.nio.ByteBuffer;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.StoredMessage;

@PluggableService
public class MessageMetaDataType_0_8 implements MessageMetaDataType<MessageMetaData>
{

    public static final int TYPE = 0;
    public static final String V0_8 = "v0_8";

    @Override
    public int ordinal()
    {
        return TYPE;
    }

    @Override
    public MessageMetaData createMetaData(ByteBuffer buf)
    {
        return MessageMetaData.FACTORY.createMetaData(buf);
    }

    @Override
    public ServerMessage<MessageMetaData> createMessage(StoredMessage<MessageMetaData> msg)
    {
        return new AMQMessage(msg);
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
        return V0_8;
    }
}
