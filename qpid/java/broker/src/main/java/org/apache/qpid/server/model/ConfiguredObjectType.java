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

/**
 * This enum declares the types of all possible configured objects.
 * At the moment, we have got fixed number of types.
 * <p>
 * In future, we might need to convert this enum into abstract class and
 * implement the subclasses for each type of configured object, which in turn
 * will register themselves in the static map.
 * <p>
 * That would allow us to implement plugins more effectively instead of relying on {@link ConfiguredObjectType#PLUGIN}.
 */
public enum ConfiguredObjectType
{
    BROKER(Broker.class),
    VIRTUAL_HOST(VirtualHost.class),
    PORT(Port.class),
    AUTHENTICATION_PROVIDER(AuthenticationProvider.class),
    PASSWORD_CREDENTIAL_MANAGING_AUTHENTICATION_PROVIDER(PasswordCredentialManagingAuthenticationProvider.class),
    AUTHENTICATION_METHOD(AuthenticationMethod.class),
    EXCHANGE(Exchange.class),
    USER(User.class),
    BINDING(Binding.class),
    VIRTUAL_HOST_ALIAS(VirtualHostAlias.class),
    CONSUMER(Consumer.class),
    GROUP(Group.class),
    GROUP_MEMBER(GroupMember.class),
    SESSION(Session.class),
    PUBLISHER(Publisher.class),
    QUEUE(Queue.class),
    CONNECTION(Connection.class),
    GROUP_PROVIDER(GroupProvider.class),
    PLUGIN(ConfiguredObject.class);

    private final Class<? extends ConfiguredObject> _type;

    private ConfiguredObjectType(Class<? extends ConfiguredObject> classObject)
    {
        _type = classObject;
    }

    public Class<? extends ConfiguredObject> getType()
    {
        return _type;
    }
}
