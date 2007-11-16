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
package org.apache.qpid.server.protocol;

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;

/**
 * The protocol provide's role is to encapsulate the initialisation of the protocol handler.
 *
 * The protocol handler (see AMQPFastProtocolHandler class) handles protocol events
 * such as connection closing or a frame being received. It can either do this directly
 * or pass off to the protocol session in the cases where state information is required to
 * deal with the event.
 *
 */
public class AMQPProtocolProvider
{
    /**
     * Handler for protocol events
     */
    private AMQPFastProtocolHandler _handler;

    public AMQPProtocolProvider()
    {
        IApplicationRegistry registry = ApplicationRegistry.getInstance();
        _handler = new AMQPFastProtocolHandler(registry);
    }

    public AMQPFastProtocolHandler getHandler()
    {
        return _handler;
    }
}
