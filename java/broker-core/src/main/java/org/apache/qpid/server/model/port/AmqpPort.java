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
package org.apache.qpid.server.model.port;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

@ManagedObject( category = false, type = "AMQP")
public interface AmqpPort<X extends AmqpPort<X>> extends Port<X>
{
    String DEFAULT_AMQP_SEND_BUFFER_SIZE = "262144";
    String DEFAULT_AMQP_RECEIVE_BUFFER_SIZE = "262144";
    String DEFAULT_AMQP_TCP_NO_DELAY = "true";

    String DEFAULT_AMQP_NEED_CLIENT_AUTH = "false";
    String DEFAULT_AMQP_WANT_CLIENT_AUTH = "false";

    @ManagedAttribute( automate = true , defaultValue = AmqpPort.DEFAULT_AMQP_TCP_NO_DELAY )
    boolean isTcpNoDelay();

    @ManagedAttribute( automate = true , defaultValue = AmqpPort.DEFAULT_AMQP_SEND_BUFFER_SIZE )
    int getSendBufferSize();

    @ManagedAttribute( automate = true , defaultValue = AmqpPort.DEFAULT_AMQP_RECEIVE_BUFFER_SIZE )
    int getReceiveBufferSize();


    @ManagedAttribute( automate = true, defaultValue = DEFAULT_AMQP_NEED_CLIENT_AUTH )
    boolean getNeedClientAuth();

    @ManagedAttribute( automate = true, defaultValue = DEFAULT_AMQP_WANT_CLIENT_AUTH )
    boolean getWantClientAuth();

    @ManagedAttribute( automate = true, mandatory = true )
    AuthenticationProvider getAuthenticationProvider();

    VirtualHostImpl getVirtualHost(String name);
}
