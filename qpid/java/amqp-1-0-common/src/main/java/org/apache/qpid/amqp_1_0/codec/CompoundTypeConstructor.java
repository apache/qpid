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

import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.transport.*;
import org.apache.qpid.amqp_1_0.type.transport.Error;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompoundTypeConstructor extends VariableWidthTypeConstructor
{
    private final CompoundTypeAssembler.Factory _assemblerFactory;

    public static final CompoundTypeAssembler.Factory LIST_ASSEMBLER_FACTORY =
            new CompoundTypeAssembler.Factory()
            {

                public CompoundTypeAssembler newInstance()
                {
                    return new ListAssembler();
                }
            };



    private static class ListAssembler implements CompoundTypeAssembler
    {
        private List _list;

        public void init(final int count) throws AmqpErrorException
        {
            _list = new ArrayList(count);
        }

        public void addItem(final Object obj) throws AmqpErrorException
        {
            _list.add(obj);
        }

        public Object complete() throws AmqpErrorException
        {
            return _list;
        }

        @Override
        public String toString()
        {
            return "ListAssembler{" +
                   "_list=" + _list +
                   '}';
        }
    }


    public static final CompoundTypeAssembler.Factory MAP_ASSEMBLER_FACTORY =
            new CompoundTypeAssembler.Factory()
            {

                public CompoundTypeAssembler newInstance()
                {
                    return new MapAssembler();
                }
            };

    private static class MapAssembler implements CompoundTypeAssembler
    {
        private Map _map;
        private Object _lastKey;
        private static final Object NOT_A_KEY = new Object();


        public void init(final int count) throws AmqpErrorException
        {
            // Can't have an odd number of elements in a map
            if((count & 0x1) == 1)
            {
                Error error = new Error();
                error.setCondition(AmqpError.DECODE_ERROR);
                Formatter formatter = new Formatter();
                formatter.format("map cannot have odd number of elements: %d", count);
                error.setDescription(formatter.toString());
                throw new AmqpErrorException(error);
            }
            _map = new HashMap(count);
            _lastKey = NOT_A_KEY;
        }

        public void addItem(final Object obj) throws AmqpErrorException
        {
            if(_lastKey != NOT_A_KEY)
            {
                if(_map.put(_lastKey, obj) != null)
                {
                    Error error = new Error();
                    error.setCondition(AmqpError.DECODE_ERROR);
                    Formatter formatter = new Formatter();
                    formatter.format("map cannot have duplicate keys: %s has values (%s, %s)", _lastKey, _map.get(_lastKey), obj);
                    error.setDescription(formatter.toString());

                    throw new AmqpErrorException(error);
                }
                _lastKey = NOT_A_KEY;
            }
            else
            {
                _lastKey = obj;
            }

        }

        public Object complete() throws AmqpErrorException
        {
            return _map;
        }
    }


    public static CompoundTypeConstructor getInstance(int i,
                                                      CompoundTypeAssembler.Factory assemblerFactory)
    {
        return new CompoundTypeConstructor(i, assemblerFactory);
    }


    private CompoundTypeConstructor(int size,
                                    final CompoundTypeAssembler.Factory assemblerFactory)
    {
        super(size);
        _assemblerFactory = assemblerFactory;
    }

    @Override
    public Object construct(final ByteBuffer in, boolean isCopy, ValueHandler delegate) throws AmqpErrorException
    {
        int size;
        int count;

        if(getSize() == 1)
        {
            size = in.get() & 0xFF;
            count = in.get() & 0xFF;
        }
        else
        {
            size = in.getInt();
            count = in.getInt();
        }

        ByteBuffer data;
        ByteBuffer inDup = in.slice();

        inDup.limit(size-getSize());

        CompoundTypeAssembler assembler = _assemblerFactory.newInstance();

        assembler.init(count);

        for(int i = 0; i < count; i++)
        {
            assembler.addItem(delegate.parse(in));
        }

        return assembler.complete();

    }


}