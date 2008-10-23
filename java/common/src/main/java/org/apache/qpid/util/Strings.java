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
package org.apache.qpid.util;

import java.io.UnsupportedEncodingException;


/**
 * Strings
 *
 */

public final class Strings
{

    private static final byte[] EMPTY = new byte[0];

    private static final ThreadLocal<char[]> charbuf = new ThreadLocal()
    {
        public char[] initialValue()
        {
            return new char[4096];
        }
    };

    public static final byte[] toUTF8(String str)
    {
        if (str == null)
        {
            return EMPTY;
        }
        else
        {
            final int size = str.length();
            char[] chars = charbuf.get();
            if (size > chars.length)
            {
                chars = new char[Math.max(size, 2*chars.length)];
                charbuf.set(chars);
            }

            str.getChars(0, size, chars, 0);
            final byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++)
            {
                if (chars[i] > 127)
                {
                    try
                    {
                        return str.getBytes("UTF-8");
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                bytes[i] = (byte) chars[i];
            }
            return bytes;
        }
    }

    public static final String fromUTF8(byte[] bytes)
    {
        try
        {
            return new String(bytes, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

}
