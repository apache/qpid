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


/**
 * ProtocolHeader
 *
 * @author Rafael H. Schloming
 */

class ProtocolHeader
{

    private static final byte[] AMQP = {'A', 'M', 'Q', 'P' };
    private static final byte CLASS = 1;

    final private byte instance;
    final private byte major;
    final private byte minor;

    public ProtocolHeader(byte instance, byte major, byte minor)
    {
        this.instance = instance;
        this.major = major;
        this.minor = minor;
    }

    public byte getInstance()
    {
        return instance;
    }

    public byte getMajor()
    {
        return major;
    }

    public byte getMinor()
    {
        return minor;
    }

    public ByteBuffer toByteBuffer()
    {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put(AMQP);
        buf.put(CLASS);
        buf.put(instance);
        buf.put(major);
        buf.put(minor);
        buf.flip();
        return buf;
    }

    public String toString()
    {
        return String.format("AMQP.%d %d-%d", instance, major, minor);
    }

}
