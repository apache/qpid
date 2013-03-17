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
import java.util.List;

public class ListWriter implements ValueWriter<List>
{
    private static class NonEmptyListWriter extends AbstractListWriter<List>
    {
        private List _list;
        private int _position = 0;

        public NonEmptyListWriter(final Registry registry)
        {
            super(registry);
        }

        @Override
        protected void onSetValue(final List value)
        {
            _list = value;
            _position = 0;

        }

        @Override
        protected int getCount()
        {
            return _list.size();
        }

        @Override
        protected boolean hasNext()
        {
            return _position < getCount();
        }

        @Override
        protected Object next()
        {
            return _list.get(_position++);
        }

        @Override
        protected void clear()
        {
            _list = null;
            _position = 0;
        }

        @Override
        protected void reset()
        {
            _position = 0;
        }

    }

    private final NonEmptyListWriter _nonEmptyListWriter;
    private static final byte ZERO_BYTE_FORMAT_CODE = (byte) 0x45;

    private final ValueWriter<List> _emptyListWriter = new EmptyListValueWriter();


    private ValueWriter<List> _delegate;

    public ListWriter(final Registry registry)
    {
        _nonEmptyListWriter = new NonEmptyListWriter(registry);

    }


    public int writeToBuffer(ByteBuffer buffer)
    {
        return _delegate.writeToBuffer(buffer);
    }

    public void setValue(List frameBody)
    {
        if(frameBody.isEmpty())
        {
            _delegate = _emptyListWriter;
        }
        else
        {
            _delegate = _nonEmptyListWriter;
        }
        _delegate.setValue(frameBody);
    }

    public boolean isComplete()
    {
        return _delegate.isComplete();
    }

    public boolean isCacheable()
    {
        return false;
    }



    private static Factory<List> FACTORY = new Factory<List>()
                                            {

                                                public ValueWriter<List> newInstance(Registry registry)
                                                {
                                                    return new ListWriter(registry);
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(List.class, FACTORY);
    }

    public static class EmptyListValueWriter implements ValueWriter<List>
    {
        private boolean _complete;


        public int writeToBuffer(ByteBuffer buffer)
        {

            if(!_complete && buffer.hasRemaining())
            {
                buffer.put(ZERO_BYTE_FORMAT_CODE);
                _complete = true;
            }

            return 1;
        }

        public void setValue(List list)
        {
            _complete = false;
        }

        public boolean isCacheable()
        {
            return true;
        }

        public boolean isComplete()
        {
            return _complete;
        }

    }
}