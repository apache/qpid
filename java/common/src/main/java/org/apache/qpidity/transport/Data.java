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

    private final ByteBuffer data;
    private final boolean first;
    private final boolean last;

    public Data(ByteBuffer data, boolean first, boolean last)
    {
        this.data = data;
        this.first = first;
        this.last = last;
    }

    public ByteBuffer getData()
    {
        return data.slice();
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
        str.append(str(data, 64));
        str.append(")");
        return str.toString();
    }

}
