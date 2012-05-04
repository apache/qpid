
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


package org.apache.qpid.amqp_1_0.type.transaction.codec;

import org.apache.qpid.amqp_1_0.codec.AbstractDescribedTypeWriter;
import org.apache.qpid.amqp_1_0.codec.AbstractListWriter;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;

import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;

public class TransactionalStateWriter extends AbstractDescribedTypeWriter<TransactionalState>
{
    private TransactionalState _value;
    private int _count = -1;

    public TransactionalStateWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final TransactionalState value)
    {
        _value = value;
        _count = calculateCount();
    }

    private int calculateCount()
    {


        if( _value.getOutcome() != null)
        {
            return 2;
        }

        if( _value.getTxnId() != null)
        {
            return 1;
        }

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
        return UnsignedLong.valueOf(0x0000000000000034L);
    }

    @Override
    protected ValueWriter createDescribedWriter()
    {
        final Writer writer = new Writer(getRegistry());
        writer.setValue(_value);
        return writer;
    }

    private class Writer extends AbstractListWriter<TransactionalState>
    {
        private int _field;

        public Writer(final Registry registry)
        {
            super(registry);
        }

        @Override
        protected void onSetValue(final TransactionalState value)
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

                case 0:
                    return _value.getTxnId();

                case 1:
                    return _value.getOutcome();

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

    private static Factory<TransactionalState> FACTORY = new Factory<TransactionalState>()
    {

        public ValueWriter<TransactionalState> newInstance(Registry registry)
        {
            return new TransactionalStateWriter(registry);
        }
    };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(TransactionalState.class, FACTORY);
    }

}
