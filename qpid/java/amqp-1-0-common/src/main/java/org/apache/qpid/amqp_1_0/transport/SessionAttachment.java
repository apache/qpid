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

package org.apache.qpid.amqp_1_0.transport;

public class SessionAttachment
{
    private final SessionEndpoint _sessionEndpoint;
    private final ConnectionEndpoint _connectionEndpoint;
    private final short _channel;

    public SessionAttachment(SessionEndpoint sessionEndpoint, ConnectionEndpoint connectionEndpoint, short channel)
    {
        _sessionEndpoint = sessionEndpoint;
        _connectionEndpoint = connectionEndpoint;
        _channel = channel;
    }

    public SessionEndpoint getSessionEndpoint()
    {
        return _sessionEndpoint;
    }

    public ConnectionEndpoint getConnectionEndpoint()
    {
        return _connectionEndpoint;
    }

    public short getChannel()
    {
        return _channel;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        SessionAttachment that = (SessionAttachment) o;

        if (_channel != that._channel)
        {
            return false;
        }
        if (_connectionEndpoint != null
            ? !_connectionEndpoint.equals(that._connectionEndpoint)
            : that._connectionEndpoint != null)
        {
            return false;
        }
        if (_sessionEndpoint != null ? !_sessionEndpoint.equals(that._sessionEndpoint) : that._sessionEndpoint != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _sessionEndpoint != null ? _sessionEndpoint.hashCode() : 0;
        result = 31 * result + (_connectionEndpoint != null ? _connectionEndpoint.hashCode() : 0);
        result = 31 * result + (int) _channel;
        return result;
    }
}
