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

import org.apache.qpidity.transport.network.Frame;

import java.util.List;
import java.nio.ByteBuffer;


/**
 * Header
 *
 * @author Rafael H. Schloming
 */

public class Header implements ProtocolEvent {

    private final List<Struct> structs;
    private ByteBuffer _buf;
    private boolean _noPayload;
    private int channel;

    public Header(List<Struct> structs, boolean lastframe)
    {
        this.structs = structs;
        _noPayload= lastframe;
    }

    public List<Struct> getStructs()
    {
        return structs;
    }

    public void setBuf(ByteBuffer buf)
    {
        _buf = buf;
    }

    public ByteBuffer getBuf()
    {
        return _buf;
    }
    public <T> T get(Class<T> klass)
    {
        for (Struct st : structs)
        {
            if (klass.isInstance(st))
            {
                return klass.cast(st);
            }
        }

        return null;
    }

    public final int getChannel()
    {
        return channel;
    }

    public final void setChannel(int channel)
    {
        this.channel = channel;
    }

    public byte getEncodedTrack()
    {
        return Frame.L4;
    }

    public <C> void delegate(C context, ProtocolDelegate<C> delegate)
    {
        delegate.header(context, this);
    }

    public boolean hasNoPayload()
    {
        return _noPayload;
    }

    public String toString()
    {
        StringBuffer str = new StringBuffer();
        str.append("ch=");
        str.append(channel);
        str.append(" Header(");
        boolean first = true;
        for (Struct s : structs)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.append(", ");
            }
            str.append(s);
        }
        str.append(")");
        return str.toString();
    }

}
