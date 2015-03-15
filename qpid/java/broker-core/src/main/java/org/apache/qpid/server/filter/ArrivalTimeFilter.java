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
package org.apache.qpid.server.filter;

import org.apache.qpid.common.AMQPFilterTypes;

public final class ArrivalTimeFilter implements MessageFilter
{
    private final long _startingFrom;
    private final boolean _startAtTail;

    public ArrivalTimeFilter(final long startingFrom, final boolean startAtTail)
    {
        _startingFrom = startingFrom;
        _startAtTail = startAtTail;
    }

    @Override
    public String getName()
    {
        return AMQPFilterTypes.REPLAY_PERIOD.toString();
    }

    @Override
    public boolean startAtTail()
    {
        return _startAtTail;
    }

    @Override
    public boolean matches(final Filterable message)
    {
        return message.getArrivalTime() >=  _startingFrom;
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

        final ArrivalTimeFilter that = (ArrivalTimeFilter) o;

        return _startingFrom == that._startingFrom;

    }

    @Override
    public int hashCode()
    {
        return (int) (_startingFrom ^ (_startingFrom >>> 32));
    }
}
