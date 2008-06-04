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
package org.apache.qpidity.transport.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * BBDecoder
 *
 * @author Rafael H. Schloming
 */

public final class BBDecoder extends AbstractDecoder
{

    private final ByteBuffer in;

    public BBDecoder(ByteBuffer in)
    {
        this.in = in;
        this.in.order(ByteOrder.BIG_ENDIAN);
    }

    protected byte doGet()
    {
        return in.get();
    }

    protected void doGet(byte[] bytes)
    {
        in.get(bytes);
    }

    public boolean hasRemaining()
    {
        return in.hasRemaining();
    }

    public short readUint8()
    {
        return (short) (0xFF & in.get());
    }

    public int readUint16()
    {
        return 0xFFFF & in.getShort();
    }

    public long readUint32()
    {
        return 0xFFFFFFFFL & in.getInt();
    }

    public long readUint64()
    {
        return in.getLong();
    }

}
