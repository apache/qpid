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

import java.nio.ByteBuffer;

import java.util.Collections;

import static org.apache.qpidity.transport.util.Functions.*;


/**
 * Data
 *
 */

public class Data implements ProtocolEvent
{

    private final Iterable<ByteBuffer> fragments;
    private final boolean first;
    private final boolean last;

    public Data(Iterable<ByteBuffer> fragments, boolean first, boolean last)
    {
        this.fragments = fragments;
        this.first = first;
        this.last = last;
    }

    public Data(ByteBuffer buf, boolean first, boolean last)
    {
        this(Collections.singletonList(buf), first, last);
    }

    public Iterable<ByteBuffer> getFragments()
    {
        return fragments;
    }

    public boolean isFirst()
    {
        return first;
    }

    public boolean isLast()
    {
        return last;
    }

    public byte getEncodedTrack()
    {
        return Frame.L4;
    }

    public <C> void delegate(C context, ProtocolDelegate<C> delegate)
    {
        delegate.data(context, this);
    }

    public String toString()
    {
        StringBuffer str = new StringBuffer();
        str.append("Data(");
        boolean first = true;
        int left = 64;
        for (ByteBuffer buf : getFragments())
        {
            if (first)
            {
                first = false;
            }
            else
            {
                str.append(" | ");
            }
            str.append(str(buf, left));
            left -= buf.remaining();
            if (left < 0)
            {
                break;
            }
        }
        str.append(")");
        return str.toString();
    }

}
