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
package org.apache.qpid.server.connection;

import org.apache.qpid.server.protocol.AMQConnectionModel;

import java.security.Principal;

public class ConnectionPrincipal implements Principal
{
    private final AMQConnectionModel _connection;

    public ConnectionPrincipal(final AMQConnectionModel connection)
    {
        _connection = connection;
    }

    @Override
    public String getName()
    {
        return _connection.getRemoteAddressString();
    }

    public AMQConnectionModel getConnection()
    {
        return _connection;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final ConnectionPrincipal that = (ConnectionPrincipal) o;

        if (!_connection.equals(that._connection))
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return _connection.hashCode();
    }
}
