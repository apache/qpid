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
 * BBEncoder
 *
 * @author Rafael H. Schloming
 */

public final class BBEncoder extends AbstractEncoder
{

    private final ByteBuffer out;

    public BBEncoder(ByteBuffer out) {
        this.out = out;
        this.out.order(ByteOrder.BIG_ENDIAN);
    }

    protected void doPut(byte b)
    {
        out.put(b);
    }

    protected void doPut(ByteBuffer src)
    {
        out.put(src);
    }

    public void writeUint8(short b)
    {
        assert b < 0x100;

        out.put((byte) b);
    }

    public void writeUint16(int s)
    {
        assert s < 0x10000;

        out.putShort((short) s);
    }

    public void writeUint32(long i)
    {
        assert i < 0x100000000L;

        out.putInt((int) i);
    }

    public void writeUint64(long l)
    {
        out.putLong(l);
    }

}
