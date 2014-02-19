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

import org.apache.qpid.server.protocol.AMQSessionModel;

import java.security.Principal;

public class SessionPrincipal implements Principal
{
    private final AMQSessionModel _session;

    public SessionPrincipal(final AMQSessionModel session)
    {
        _session = session;
    }

    public AMQSessionModel getSession()
    {
        return _session;
    }

    @Override
    public String getName()
    {
        return "session:"+_session.getId();
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

        final SessionPrincipal that = (SessionPrincipal) o;

        if (!_session.equals(that._session))
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return _session.hashCode();
    }
}
