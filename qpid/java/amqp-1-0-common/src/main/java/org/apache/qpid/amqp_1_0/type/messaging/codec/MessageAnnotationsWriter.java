
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
import org.apache.qpid.amqp_1_0.codec.ValueWriter;

import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;

public class MessageAnnotationsWriter extends AbstractDescribedTypeWriter<MessageAnnotations>
{
    private MessageAnnotations _value;



    public MessageAnnotationsWriter(final Registry registry)
    {
        super(registry);
    }

    @Override
    protected void onSetValue(final MessageAnnotations value)
    {
        _value = value;
    }

    @Override
    protected void clear()
    {
        _value = null;
    }

    protected Object getDescriptor()
    {
        return UnsignedLong.valueOf(0x0000000000000072L);
    }

    @Override
    protected ValueWriter createDescribedWriter()
    {
        return getRegistry().getValueWriter(_value.getValue());
    }

    private static Factory<MessageAnnotations> FACTORY = new Factory<MessageAnnotations>()
    {

        public ValueWriter<MessageAnnotations> newInstance(Registry registry)
        {
            return new MessageAnnotationsWriter(registry);
        }
    };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(MessageAnnotations.class, FACTORY);
    }

}
