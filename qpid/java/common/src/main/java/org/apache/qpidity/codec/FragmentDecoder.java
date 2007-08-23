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
package org.apache.qpidity.codec;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import java.util.Iterator;

import static java.lang.Math.*;


/**
 * FragmentDecoder
 *
 * @author Rafael H. Schloming
 */

public class FragmentDecoder extends AbstractDecoder
{

    private final Iterator<ByteBuffer> fragments;
    private ByteBuffer current;

    public FragmentDecoder(byte major, byte minor, Iterator<ByteBuffer> fragments)
    {
        super(major, minor);
        this.fragments = fragments;
        this.current = null;
    }

    public boolean hasRemaining()
    {
        advance();
        return current != null || fragments.hasNext();
    }

    private void advance()
    {
        while (current == null && fragments.hasNext())
        {
            current = fragments.next();
            if (current.hasRemaining())
            {
                break;
            }
            else
            {
                current = null;
            }
        }
    }

    private void preRead()
    {
        advance();

        if (current == null)
        {
            throw new BufferUnderflowException();
        }
    }

    private void postRead()
    {
        if (current.remaining() == 0)
        {
            current = null;
        }
    }

    @Override protected byte get()
    {
        preRead();
        byte b = current.get();
        postRead();
        return b;
    }

    @Override protected void get(byte[] bytes)
    {
        int remaining = bytes.length;
        while (remaining > 0)
        {
            preRead();
            int size = min(remaining, current.remaining());
            current.get(bytes, 0, size);
            remaining -= size;
            postRead();
        }
    }

}
