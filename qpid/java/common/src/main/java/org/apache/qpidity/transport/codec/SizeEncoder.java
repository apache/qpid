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


/**
 * SizeEncoder
 *
 * @author Rafael H. Schloming
 */

public class SizeEncoder extends AbstractEncoder implements Sizer
{

    private int size;

    public SizeEncoder(byte major, byte minor) {
        this(major, minor, 0);
    }

    public SizeEncoder(byte major, byte minor, int size) {
        super(major, minor);
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
        flush();
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

}
