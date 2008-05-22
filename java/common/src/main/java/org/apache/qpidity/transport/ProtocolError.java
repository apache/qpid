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
package org.apache.qpidity.transport;

import org.apache.qpidity.transport.network.NetworkDelegate;
import org.apache.qpidity.transport.network.NetworkEvent;


/**
 * ProtocolError
 *
 * @author Rafael H. Schloming
 */

public class ProtocolError implements NetworkEvent, ProtocolEvent
{

    private final byte track;
    private final String format;
    private final Object[] args;

    public ProtocolError(byte track, String format, Object ... args)
    {
        this.track = track;
        this.format = format;
        this.args = args;
    }

    public byte getEncodedTrack()
    {
        return track;
    }

    public String getMessage()
    {
        return String.format(format, args);
    }

    public <C> void delegate(C context, ProtocolDelegate<C> delegate)
    {
        delegate.error(context, this);
    }

    public void delegate(NetworkDelegate delegate)
    {
        delegate.error(this);
    }

    public String toString()
    {
        return String.format("protocol error: %s", getMessage());
    }

}
