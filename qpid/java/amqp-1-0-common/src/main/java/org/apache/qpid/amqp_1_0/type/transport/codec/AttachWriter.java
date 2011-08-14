
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
import org.apache.qpid.amqp_1_0.type.transport.Attach;

public class AttachWriter extends AbstractDescribedTypeWriter<Attach>
{
    private Attach _value;
    private int _count = -1;

    public AttachWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final Attach value)
    {
        _value = value;
        _count = calculateCount();
    }

    private int calculateCount()
    {


        if( _value.getProperties() != null)
        {
            return 14;
        }

        if( _value.getDesiredCapabilities() != null)
        {
            return 13;
        }

        if( _value.getOfferedCapabilities() != null)
        {
            return 12;
        }

        if( _value.getMaxMessageSize() != null)
        {
            return 11;
        }

        if( _value.getInitialDeliveryCount() != null)
        {
            return 10;
        }

        if( _value.getIncompleteUnsettled() != null)
        {
            return 9;
        }

        if( _value.getUnsettled() != null)
        {
            return 8;
        }

        if( _value.getTarget() != null)
        {
            return 7;
        }

        if( _value.getSource() != null)
        {
            return 6;
        }

        if( _value.getRcvSettleMode() != null)
        {
            return 5;
        }

        if( _value.getSndSettleMode() != null)
        {
            return 4;
        }

        if( _value.getRole() != null)
        {
            return 3;
        }

        if( _value.getHandle() != null)
        {
            return 2;
        }

        if( _value.getName() != null)
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
        return UnsignedLong.valueOf(0x0000000000000012L);
    }

    @Override
    protected ValueWriter createDescribedWriter()
    {
        final Writer writer = new Writer(getRegistry());
        writer.setValue(_value);
        return writer;
    }

    private class Writer extends AbstractListWriter<Attach>
    {
        private int _field;

        public Writer(final Registry registry)
        {
            super(registry);
        }

        @Override
        protected void onSetValue(final Attach value)
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
                    return _value.getName();

                case 1:
                    return _value.getHandle();

                case 2:
                    return _value.getRole();

                case 3:
                    return _value.getSndSettleMode();

                case 4:
                    return _value.getRcvSettleMode();

                case 5:
                    return _value.getSource();

                case 6:
                    return _value.getTarget();

                case 7:
                    return _value.getUnsettled();

                case 8:
                    return _value.getIncompleteUnsettled();

                case 9:
                    return _value.getInitialDeliveryCount();

                case 10:
                    return _value.getMaxMessageSize();

                case 11:
                    return _value.getOfferedCapabilities();

                case 12:
                    return _value.getDesiredCapabilities();

                case 13:
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

    private static Factory<Attach> FACTORY = new Factory<Attach>()
    {

        public ValueWriter<Attach> newInstance(Registry registry)
        {
            return new AttachWriter(registry);
        }
    };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Attach.class, FACTORY);
    }

}
