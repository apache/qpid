
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


package org.apache.qpid.amqp_1_0.type.messaging.codec;

import org.apache.qpid.amqp_1_0.codec.AbstractDescribedTypeWriter;
import org.apache.qpid.amqp_1_0.codec.AbstractListWriter;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;

import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.messaging.DeleteOnNoMessages;

public class DeleteOnNoMessagesWriter extends AbstractDescribedTypeWriter<DeleteOnNoMessages>
{
    private DeleteOnNoMessages _value;
    private int _count = -1;

    public DeleteOnNoMessagesWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final DeleteOnNoMessages value)
    {
        _value = value;
        _count = calculateCount();
    }

    private int calculateCount()
    {


        return 0;
    }

    @Override
    protected void clear()
    {
        _value = null;
        _count = -1;
    }


    protected Object getDescriptor()
    {
        return UnsignedLong.valueOf(0x000000000000002dL);
    }

    @Override
    protected ValueWriter createDescribedWriter()
    {
        final Writer writer = new Writer(getRegistry());
        writer.setValue(_value);
        return writer;
    }

    private class Writer extends AbstractListWriter<DeleteOnNoMessages>
    {
        private int _field;

        public Writer(final Registry registry)
        {
            super(registry);
        }

        @Override
        protected void onSetValue(final DeleteOnNoMessages value)
        {
            reset();
        }

        @Override
        protected int getCount()
        {
            return _count;
        }

        @Override
        protected boolean hasNext()
        {
            return _field < _count;
        }

        @Override
        protected Object next()
        {
            switch(_field++)
            {

                default:
                    return null;
            }
        }

        @Override
        protected void clear()
        {
        }

        @Override
        protected void reset()
        {
            _field = 0;
        }
    }

    private static Factory<DeleteOnNoMessages> FACTORY = new Factory<DeleteOnNoMessages>()
    {

        public ValueWriter<DeleteOnNoMessages> newInstance(Registry registry)
        {
            return new DeleteOnNoMessagesWriter(registry);
        }
    };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(DeleteOnNoMessages.class, FACTORY);
    }

}
