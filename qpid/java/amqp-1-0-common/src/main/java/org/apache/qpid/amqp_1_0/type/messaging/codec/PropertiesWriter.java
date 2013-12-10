
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
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class PropertiesWriter extends AbstractDescribedTypeWriter<Properties>
{
    private Properties _value;
    private int _count = -1;

    public PropertiesWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final Properties value)
    {
        _value = value;
        _count = calculateCount();
    }

    private int calculateCount()
    {


        if( _value.getReplyToGroupId() != null)
        {
            return 13;
        }

        if( _value.getGroupSequence() != null)
        {
            return 12;
        }

        if( _value.getGroupId() != null)
        {
            return 11;
        }

        if( _value.getCreationTime() != null)
        {
            return 10;
        }

        if( _value.getAbsoluteExpiryTime() != null)
        {
            return 9;
        }

        if( _value.getContentEncoding() != null)
        {
            return 8;
        }

        if( _value.getContentType() != null)
        {
            return 7;
        }

        if( _value.getCorrelationId() != null)
        {
            return 6;
        }

        if( _value.getReplyTo() != null)
        {
            return 5;
        }

        if( _value.getSubject() != null)
        {
            return 4;
        }

        if( _value.getTo() != null)
        {
            return 3;
        }

        if( _value.getUserId() != null)
        {
            return 2;
        }

        if( _value.getMessageId() != null)
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
        return UnsignedLong.valueOf(0x0000000000000073L);
    }

    @Override
    protected ValueWriter createDescribedWriter()
    {
        final Writer writer = new Writer(getRegistry());
        writer.setValue(_value);
        return writer;
    }

    private class Writer extends AbstractListWriter<Properties>
    {
        private int _field;

        public Writer(final Registry registry)
        {
            super(registry);
        }

        @Override
        protected void onSetValue(final Properties value)
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
                    return _value.getMessageId();

                case 1:
                    return _value.getUserId();

                case 2:
                    return _value.getTo();

                case 3:
                    return _value.getSubject();

                case 4:
                    return _value.getReplyTo();

                case 5:
                    return _value.getCorrelationId();

                case 6:
                    return _value.getContentType();

                case 7:
                    return _value.getContentEncoding();

                case 8:
                    return _value.getAbsoluteExpiryTime();

                case 9:
                    return _value.getCreationTime();

                case 10:
                    return _value.getGroupId();

                case 11:
                    return _value.getGroupSequence();

                case 12:
                    return _value.getReplyToGroupId();

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

    private static Factory<Properties> FACTORY = new Factory<Properties>()
    {

        public ValueWriter<Properties> newInstance(Registry registry)
        {
            return new PropertiesWriter(registry);
        }
    };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Properties.class, FACTORY);
    }

}
