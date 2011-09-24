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
package org.apache.qpid.server.store.berkeleydb;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;

public class BindingKey extends Object
{
    private final AMQShortString _exchangeName;
    private final AMQShortString _queueName;
    private final AMQShortString _routingKey;
    private final FieldTable _arguments;

    public BindingKey(AMQShortString exchangeName, AMQShortString queueName, AMQShortString routingKey, FieldTable arguments)
    {
        _exchangeName = exchangeName;
        _queueName = queueName;
        _routingKey = routingKey;
        _arguments = arguments;
    }


    public AMQShortString getExchangeName()
    {
        return _exchangeName;
    }

    public AMQShortString getQueueName()
    {
        return _queueName;
    }

    public AMQShortString getRoutingKey()
    {
        return _routingKey;
    }

    public FieldTable getArguments()
    {
        return _arguments;
    }

}
