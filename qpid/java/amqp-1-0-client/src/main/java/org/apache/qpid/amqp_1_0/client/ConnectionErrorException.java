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
package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.ErrorCondition;
import org.apache.qpid.amqp_1_0.type.transport.Error;

public class ConnectionErrorException extends ConnectionException
{
    protected final Error _remoteError;

    public ConnectionErrorException(ErrorCondition condition,final String description)
    {
        this(new Error(condition, description));
    }

    public ConnectionErrorException(Error remoteError)
    {
        super(remoteError.getDescription() == null ? remoteError.toString() : remoteError.getDescription());
        _remoteError = remoteError;
    }

    public org.apache.qpid.amqp_1_0.type.transport.Error getRemoteError()
    {
        return _remoteError;
    }
}
