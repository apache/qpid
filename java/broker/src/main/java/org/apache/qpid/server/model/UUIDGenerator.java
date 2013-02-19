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
package org.apache.qpid.server.model;

import java.util.UUID;

public class UUIDGenerator
{
    //Generates a random UUID. Used primarily by tests.
    public static UUID generateRandomUUID()
    {
        return UUID.randomUUID();
    }

    private static UUID createUUID(String objectType, String... names)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(objectType);

        for(String name : names)
        {
            sb.append("/").append(name);
        }

        return UUID.nameUUIDFromBytes(sb.toString().getBytes());
    }

    public static UUID generateExchangeUUID(String exchangeName, String virtualHostName)
    {
        return createUUID(Exchange.class.getName(), virtualHostName, exchangeName);
    }

    public static UUID generateQueueUUID(String queueName, String virtualHostName)
    {
        return createUUID(Queue.class.getName(), virtualHostName, queueName);
    }

    public static UUID generateBindingUUID(String exchangeName, String queueName, String bindingKey, String virtualHostName)
    {
        return createUUID(Binding.class.getName(), virtualHostName, exchangeName, queueName, bindingKey);
    }

    public static UUID generateUserUUID(String authenticationProviderName, String userName)
    {
        return createUUID(User.class.getName(), authenticationProviderName, userName);
    }

    public static UUID generateGroupUUID(String groupProviderName, String groupName)
    {
        return createUUID(Group.class.getName(), groupProviderName, groupName);
    }

    public static UUID generateVhostUUID(String virtualHostName)
    {
        return createUUID(VirtualHost.class.getName(), virtualHostName);
    }

    public static UUID generateVhostAliasUUID(String virtualHostName, String portName)
    {
        return createUUID(VirtualHostAlias.class.getName(), virtualHostName, portName);
    }

    public static UUID generateConsumerUUID(String virtualHostName, String queueName, String connectionRemoteAddress, String channelNumber, String consumerName)
    {
        return createUUID(Consumer.class.getName(), virtualHostName, queueName, connectionRemoteAddress, channelNumber, consumerName);
    }

    public static UUID generateGroupMemberUUID(String groupProviderName, String groupName, String groupMemberName)
    {
        return createUUID(GroupMember.class.getName(), groupProviderName, groupName, groupMemberName);
    }

    public static UUID generateBrokerChildUUID(String type, String childName)
    {
        return createUUID(type, childName);
    }
}
