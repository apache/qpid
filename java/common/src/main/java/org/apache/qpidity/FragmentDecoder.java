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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import java.util.Iterator;

import static java.lang.Math.*;


/**
 * FragmentDecoder
 *
 * @author Rafael H. Schloming
 */

class FragmentDecoder extends AbstractDecoder
{

    private final Iterator<ByteBuffer> fragments;
    private ByteBuffer current;

    public FragmentDecoder(StructFactory factory, Iterator<ByteBuffer> fragments)
    {
        super(factory);
        this.fragments = fragments;
        this.current = null;
    }

    private void preRead()
    {
        if (current == null)
        {
            if (!fragments.hasNext())
            {
                throw new BufferUnderflowException();
            }

            current = fragments.next();
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
