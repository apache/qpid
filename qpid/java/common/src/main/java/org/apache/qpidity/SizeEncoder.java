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


/**
 * SizeEncoder
 *
 * @author Rafael H. Schloming
 */

class SizeEncoder extends AbstractEncoder
{

    private int size;

    public SizeEncoder(byte major, byte minor) {
        this(major, minor, 0);
    }

    public SizeEncoder(byte major, byte minor, int size) {
        super(major, minor);
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override protected void put(byte b)
    {
        size += 1;
    }

    @Override protected void put(ByteBuffer src)
    {
        size += src.remaining();
    }

    @Override public void writeShortstr(String s)
    {
        if (s == null) { s = ""; }
        if (s.length() > 255) {
            throw new IllegalArgumentException(s);
        }
        writeOctet((byte) s.length());
        size += s.length();
    }

    @Override public void writeLongstr(String s)
    {
        if (s == null) { s = ""; }
        writeLong(s.length());
        size += s.length();
    }

}
