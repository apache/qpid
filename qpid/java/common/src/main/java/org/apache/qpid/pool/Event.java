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
package org.apache.qpid.pool;

import org.apache.log4j.Logger;
import org.apache.mina.common.IoFilter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IdleStatus;

/**
 * Represents an operation on IoFilter.
 */
enum EventType
{
    OPENED, CLOSED, READ, WRITE, WRITTEN, RECEIVED, SENT, IDLE, EXCEPTION
}

class Event
{
    private static final Logger _log = Logger.getLogger(Event.class);

    private final EventType type;
    private final IoFilter.NextFilter nextFilter;
    private final Object data;

    public Event(IoFilter.NextFilter nextFilter, EventType type, Object data)
    {
        this.type = type;
        this.nextFilter = nextFilter;
        this.data = data;
        if (type == EventType.EXCEPTION)
        {
            _log.error("Exception event constructed: " + data, (Throwable) data);
        }
    }

    public Object getData()
    {
        return data;
    }


    public IoFilter.NextFilter getNextFilter()
    {
        return nextFilter;
    }


    public EventType getType()
    {
        return type;
    }

    void process(IoSession session)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Processing " + this);
        }
        if (type == EventType.RECEIVED)
        {
            nextFilter.messageReceived(session, data);
            //ByteBufferUtil.releaseIfPossible( data );
        }
        else if (type == EventType.SENT)
        {
            nextFilter.messageSent(session, data);
            //ByteBufferUtil.releaseIfPossible( data );
        }
        else if (type == EventType.EXCEPTION)
        {
            nextFilter.exceptionCaught(session, (Throwable) data);
        }
        else if (type == EventType.IDLE)
        {
            nextFilter.sessionIdle(session, (IdleStatus) data);
        }
        else if (type == EventType.OPENED)
        {
            nextFilter.sessionOpened(session);
        }
        else if (type == EventType.WRITE)
        {
            nextFilter.filterWrite(session, (IoFilter.WriteRequest) data);
        }
        else if (type == EventType.CLOSED)
        {
            nextFilter.sessionClosed(session);
        }
    }

    public String toString()
    {
        return "Event: type " + type + ", data: " + data;
    }
}
