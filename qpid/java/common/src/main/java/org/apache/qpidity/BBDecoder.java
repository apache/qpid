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
package org.apache.qpidity;

import java.nio.ByteBuffer;

import java.util.Map;
import java.util.UUID;

/**
 * BBDecoder
 *
 * @author Rafael H. Schloming
 */

class BBDecoder implements Decoder
{

    private final ByteBuffer in;

    public BBDecoder(ByteBuffer in) {
        this.in = in;
    }

    public boolean readBit()
    {
        //throw new Error("TODO");
        return false;
    }

    public byte readOctet()
    {
        throw new Error("TODO");
    }

    public short readShort()
    {
        return in.getShort();
    }

    public int readLong()
    {
        return in.getInt();
    }

    public long readLonglong()
    {
        throw new Error("TODO");
    }

    public long readTimestamp()
    {
        throw new Error("TODO");
    }


    public String readShortstr()
    {
        byte size = in.get();
        byte[] bytes = new byte[size];
        in.get(bytes);
        return new String(bytes);
    }

    public String readLongstr()
    {
        throw new Error("TODO");
    }

    public Map<String,?> readTable()
    {
        //throw new Error("TODO");
        return null;
    }

    public Range<Integer>[] readRfc1982LongSet()
    {
        throw new Error("TODO");
    }

    public UUID readUuid()
    {
        throw new Error("TODO");
    }

    public String readContent()
    {
        throw new Error("TODO");
    }

}
