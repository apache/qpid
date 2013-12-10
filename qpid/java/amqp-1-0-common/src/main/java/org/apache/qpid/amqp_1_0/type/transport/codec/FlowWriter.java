
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


package org.apache.qpid.amqp_1_0.type.transport.codec;

import org.apache.qpid.amqp_1_0.codec.AbstractDescribedTypeWriter;
import org.apache.qpid.amqp_1_0.codec.AbstractListWriter;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;

import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.transport.Flow;

public class FlowWriter extends AbstractDescribedTypeWriter<Flow>
{
    private Flow _value;
    private int _count = -1;

    public FlowWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final Flow value)
    {
        _value = value;
        _count = calculateCount();
    }

    private int calculateCount()
    {


        if( _value.getProperties() != null)
        {
            return 11;
        }

        if( _value.getEcho() != null)
        {
            return 10;
        }

        if( _value.getDrain() != null)
        {
            return 9;
        }

        if( _value.getAvailable() != null)
        {
            return 8;
        }

        if( _value.getLinkCredit() != null)
        {
            return 7;
        }

        if( _value.getDeliveryCount() != null)
        {
            return 6;
        }

        if( _value.getHandle() != null)
        {
            return 5;
        }

        if( _value.getOutgoingWindow() != null)
        {
            return 4;
        }

        if( _value.getNextOutgoingId() != null)
        {
            return 3;
        }

        if( _value.getIncomingWindow() != null)
        {
            return 2;
        }

        if( _value.getNextIncomingId() != null)
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
        return UnsignedLong.valueOf(0x0000000000000013L);
    }

    @Override
    protected ValueWriter createDescribedWriter()
    {
        final Writer writer = new Writer(getRegistry());
        writer.setValue(_value);
        return writer;
    }

    private class Writer extends AbstractListWriter<Flow>
    {
        private int _field;

        public Writer(final Registry registry)
        {
            super(registry);
        }

        @Override
        protected void onSetValue(final Flow value)
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
                    return _value.getNextIncomingId();

                case 1:
                    return _value.getIncomingWindow();

                case 2:
                    return _value.getNextOutgoingId();

                case 3:
                    return _value.getOutgoingWindow();

                case 4:
                    return _value.getHandle();

                case 5:
                    return _value.getDeliveryCount();

                case 6:
                    return _value.getLinkCredit();

                case 7:
                    return _value.getAvailable();

                case 8:
                    return _value.getDrain();

                case 9:
                    return _value.getEcho();

                case 10:
                    return _value.getProperties();

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

    private static Factory<Flow> FACTORY = new Factory<Flow>()
    {

        public ValueWriter<Flow> newInstance(Registry registry)
        {
            return new FlowWriter(registry);
        }
    };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Flow.class, FACTORY);
    }

}
