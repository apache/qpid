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

import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class MessageConverter_v1_0_to_Internal implements MessageConverter<Message_1_0, InternalMessage>
{

    static final AMQPDescribedTypeRegistry TYPE_REGISTRY = AMQPDescribedTypeRegistry.newInstance();
    static
    {
        TYPE_REGISTRY.registerTransportLayer();
        TYPE_REGISTRY.registerMessagingLayer();
        TYPE_REGISTRY.registerTransactionLayer();
        TYPE_REGISTRY.registerSecurityLayer();
    }

    @Override
    public Class<Message_1_0> getInputClass()
    {
        return Message_1_0.class;
    }

    @Override
    public Class<InternalMessage> getOutputClass()
    {
        return InternalMessage.class;
    }

    @Override
    public InternalMessage convert(Message_1_0 serverMessage, VirtualHostImpl vhost)
    {
        Object bodyObject = MessageConverter_from_1_0.convertBodyToObject(serverMessage);

        return InternalMessage.convert(serverMessage.getMessageNumber(), serverMessage.isPersistent(), serverMessage.getMessageHeader(), bodyObject);
    }

    @Override
    public String getType()
    {
        return "v1-0 to Internal";
    }
}
