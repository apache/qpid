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
 * Functions
 *
 * @author Rafael H. Schloming
 */

class Functions
{

    public static final short unsigned(byte b)
    {
        return (short) ((0x100 + b) & 0xFF);
    }

    public static final int unsigned(short s)
    {
        return (0x10000 + s) & 0xFFFF;
    }

    public static final long unsigned(int i)
    {
        return (0x1000000000L + i) & 0xFFFFFFFFL;
    }

    public static final byte lsb(int i)
    {
        return (byte) (0xFF & i);
    }

    public static final byte lsb(long l)
    {
        return (byte) (0xFF & l);
    }

    public static final String str(ByteBuffer buf)
    {
        return str(buf, buf.limit());
    }

    public static final String str(ByteBuffer buf, int limit)
    {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < limit; i++)
        {
            if (i > 0 && i % 2 == 0)
            {
                str.append(" ");
            }
            str.append(String.format("%02x", buf.get(i)));
        }

        return str.toString();
    }

}
