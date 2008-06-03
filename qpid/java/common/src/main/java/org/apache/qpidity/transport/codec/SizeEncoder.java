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

import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.RangeSet;


/**
 * SizeEncoder
 *
 * @author Rafael H. Schloming
 */

public class SizeEncoder extends AbstractEncoder implements Sizer
{

    private int size;

    public SizeEncoder() {
        this(0);
    }

    public SizeEncoder(int size) {
        this.size = size;
    }

    protected Sizer sizer()
    {
        return Sizer.NULL;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int size()
    {
        return getSize();
    }

    protected void doPut(byte b)
    {
        size += 1;
    }

    protected void doPut(ByteBuffer src)
    {
        size += src.remaining();
    }

    public void writeUint8(short b)
    {
        size += 1;
    }

    public void writeUint16(int s)
    {
        size += 2;
    }

    public void writeUint32(long i)
    {
        size += 4;
    }

    public void writeUint64(long l)
    {
        size += 8;
    }

    public void writeDatetime(long l)
    {
        size += 8;
    }

    public void writeUuid(UUID uuid)
    {
        size += 16;
    }

    public void writeSequenceNo(int s)
    {
        size += 4;
    }

    public void writeSequenceSet(RangeSet ranges)
    {
        size += 2 + 8*ranges.size();
    }

    //void writeByteRanges(RangeSet ranges); // XXX 

    //void writeStr8(String s);
    //void writeStr16(String s);

    //void writeVbin8(byte[] bytes);
    //void writeVbin16(byte[] bytes);
    //void writeVbin32(byte[] bytes);

    //void writeStruct32(Struct s);
    //void writeMap(Map<String,Object> map);
    //void writeList(List<Object> list);
    //void writeArray(List<Object> array);

    //void writeStruct(int type, Struct s);

}
