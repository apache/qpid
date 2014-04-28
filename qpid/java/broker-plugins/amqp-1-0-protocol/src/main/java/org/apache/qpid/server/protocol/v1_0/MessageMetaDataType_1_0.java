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
package org.apache.qpid.server.protocol.v1_0;

import java.nio.ByteBuffer;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.StoredMessage;

@PluggableService
public class MessageMetaDataType_1_0 implements MessageMetaDataType<MessageMetaData_1_0>
{

    public static final int TYPE = 2;
    public static final String V1_0_0 = "v1_0_0";

    @Override
    public int ordinal()
    {
        return TYPE;
    }

    @Override
    public MessageMetaData_1_0 createMetaData(ByteBuffer buf)
    {
        return MessageMetaData_1_0.FACTORY.createMetaData(buf);
    }

    @Override
    public ServerMessage<MessageMetaData_1_0> createMessage(StoredMessage<MessageMetaData_1_0> msg)
    {
        return new Message_1_0(msg);
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
        return V1_0_0;
    }
}
