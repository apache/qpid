/*
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
 */

package org.apache.qpid.amqp_1_0.codec;

import org.apache.qpid.amqp_1_0.type.Symbol;

import java.nio.ByteBuffer;

public class SymbolArrayWriter extends VariableWidthWriter<Symbol[]>
{

    private int _length;
    private byte[] _encodedVal;

    @Override protected void clearValue()
    {
        _encodedVal = null;
    }

    @Override protected boolean hasValue()
    {
        return _encodedVal != null;
    }

    @Override protected byte getFourOctetEncodingCode()
    {
        return (byte) 0xf0;
    }

    @Override protected byte getSingleOctetEncodingCode()
    {
        return (byte) 0xe0;
    }

    @Override protected int getLength()
    {
        return _length;
    }

    @Override protected void writeBytes(final ByteBuffer buf, final int offset, final int length)
    {
        buf.put(_encodedVal,offset,length);
    }

    public boolean isCacheable()
    {
        return false;
    }

    public void setValue(final Symbol[] value)
    {

        boolean useSmallConstructor = useSmallConstructor(value);
        boolean isSmall = useSmallConstructor && canFitInSmall(value);
        if(isSmall)
        {
            _length = 2;
        }
        else
        {
            _length = 5;
        }
        for(Symbol symbol : value)
        {
            _length += symbol.length() ;
        }
        _length += value.length * (useSmallConstructor ? 1 : 4);


        _encodedVal = new byte[_length];

        ByteBuffer buf = ByteBuffer.wrap(_encodedVal);
        if(isSmall)
        {
            buf.put((byte)value.length);
            buf.put(SymbolWriter.SMALL_ENCODING_CODE);
        }
        else
        {
            buf.putInt(value.length);
            buf.put(SymbolWriter.LARGE_ENCODING_CODE);
        }

        for(Symbol symbol : value)
        {
                if(isSmall)
                {
                    buf.put((byte)symbol.length());
                }
                else
                {
                    buf.putInt(symbol.length());
                }


            for(int i = 0; i < symbol.length(); i++)
            {
                buf.put((byte)symbol.charAt(i));
            }
        }



        super.setValue(value);
    }

    private boolean useSmallConstructor(final Symbol[] value)
    {
        for(Symbol sym : value)
        {
            if(sym.length()>255)
            {
                return false;
            }
        }
        return true;
    }

    private boolean canFitInSmall(final Symbol[] value)
    {
        if(value.length>=127)
        {
            return false;
        }

        int remaining = 253 - value.length;
        for(Symbol symbol : value)
        {

            if((remaining -= symbol.length()) < 0)
            {
                return false;
            }
        }

        return true;
    }


    private static Factory<Symbol[]> FACTORY = new Factory<Symbol[]>()
                                            {

                                                public ValueWriter<Symbol[]> newInstance(Registry registry)
                                                {
                                                    return new SymbolArrayWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Symbol[].class, FACTORY);
    }
}
