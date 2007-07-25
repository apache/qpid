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
 * SizeEncoder
 *
 * @author Rafael H. Schloming
 */

class SizeEncoder implements Encoder
{

    private int size;

    public SizeEncoder() {
        this(0);
    }

    public SizeEncoder(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void writeBit(boolean b)
    {
        //throw new Error("TODO");
    }

    public void writeOctet(short b)
    {
        size += 1;
    }

    public void writeShort(int s)
    {
        size += 2;
    }

    public void writeLong(long i)
    {
        size += 4;
    }

    public void writeLonglong(long l)
    {
        size += 8;
    }


    public void writeTimestamp(long l)
    {
        size += 8;
    }


    public void writeShortstr(String s)
    {
        if (s.length() > 255) {
            throw new IllegalArgumentException(s);
        }
        writeOctet((byte) s.length());
        size += s.length();
    }

    public void writeLongstr(String s)
    {
        throw new Error("TODO");
    }


    public void writeTable(Map<String,?> table)
    {
        //throw new Error("TODO");
    }

    public void writeRfc1982LongSet(Range<Long>[] ranges)
    {
        throw new Error("TODO");
    }

    public void writeUuid(UUID uuid)
    {
        throw new Error("TODO");
    }

    public void writeContent(String c)
    {
        throw new Error("TODO");
    }

}
