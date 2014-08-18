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
package org.apache.qpid.amqp_1_0.codec;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Symbol;

public class SymbolTypeConstructor extends VariableWidthTypeConstructor
{
    private static final Charset ASCII = Charset.forName("US-ASCII");

    private static final ConcurrentMap<BinaryString, Symbol> SYMBOL_MAP =
            new ConcurrentHashMap<BinaryString, Symbol>(2048);

    public static SymbolTypeConstructor getInstance(int i)
    {
        return new SymbolTypeConstructor(i);
    }


    private SymbolTypeConstructor(int size)
    {
        super(size);
    }

    @Override
    public Object construct(final ByteBuffer in, boolean isCopy, ValueHandler handler) throws AmqpErrorException
    {
        int size;

        if(getSize() == 1)
        {
            size = in.get() & 0xFF;
        }
        else
        {
            size = in.getInt();
        }

        BinaryString binaryStr = new BinaryString(in.array(), in.arrayOffset()+in.position(), size);

        Symbol symbolVal = SYMBOL_MAP.get(binaryStr);
        if(symbolVal == null)
        {
            ByteBuffer dup = in.duplicate();
            dup.limit(in.position()+size);
            CharBuffer charBuf = ASCII.decode(dup);


            symbolVal = Symbol.getSymbol(charBuf.toString());




            byte[] data = new byte[size];
            in.get(data);
            binaryStr = new BinaryString(data, 0, size);
            SYMBOL_MAP.putIfAbsent(binaryStr, symbolVal);
        }
        else
        {
            in.position(in.position()+size);
        }

        return symbolVal;

    }

}
