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
package org.apache.qpid.framing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * AMQTypedValue combines together a native Java Object value, and an {@link AMQType}, as a fully typed AMQP parameter
 * value. It provides the ability to read and write fully typed parameters to and from byte buffers. It also provides
 * the ability to create such parameters from Java native value and a type tag or to extract the native value and type
 * from one.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Create a fully typed AMQP value from a native type and a type tag. <td> {@link AMQType}
 * <tr><td> Create a fully typed AMQP value from a binary representation in a byte buffer. <td> {@link AMQType}
 * <tr><td> Write a fully typed AMQP value to a binary representation in a byte buffer. <td> {@link AMQType}
 * <tr><td> Extract the type from a fully typed AMQP value.
 * <tr><td> Extract the value from a fully typed AMQP value.
 * </table>
 */
public abstract class AMQTypedValue
{

    public abstract AMQType getType();

    public abstract Object getValue();

    public abstract void writeToBuffer(DataOutput buffer) throws IOException;

    public abstract int getEncodingSize();


    private static final class GenericTypedValue extends AMQTypedValue
    {
        /** The type of the value. */
        private final AMQType _type;

        /** The Java native representation of the AMQP typed value. */
        private final Object _value;

        private GenericTypedValue(AMQType type, Object value)
        {
            if (type == null)
            {
                throw new NullPointerException("Cannot create a typed value with null type");
            }

            _type = type;
            _value = type.toNativeValue(value);
        }

        private GenericTypedValue(AMQType type, DataInput buffer) throws IOException
        {
            _type = type;
            _value = type.readValueFromBuffer(buffer);
        }


        public AMQType getType()
        {
            return _type;
        }

        public Object getValue()
        {
            return _value;
        }

        public void writeToBuffer(DataOutput buffer) throws IOException
        {
            _type.writeToBuffer(_value, buffer);
        }

        public int getEncodingSize()
        {
            return _type.getEncodingSize(_value);
        }


        public String toString()
        {
            return "[" + getType() + ": " + getValue() + "]";
        }


        public boolean equals(Object o)
        {
            if(o instanceof GenericTypedValue)
            {
                GenericTypedValue other = (GenericTypedValue) o;
                return _type == other._type && (_value == null ? other._value == null : _value.equals(other._value));
            }
            else
            {
                return false;
            }
        }

        public int hashCode()
        {
            return _type.hashCode() ^ (_value == null ? 0 : _value.hashCode());
        }

    }

    private static final class LongTypedValue extends AMQTypedValue
    {

        private final long _value;

        private LongTypedValue(long value)
        {
            _value = value;
        }

        public LongTypedValue(DataInput buffer) throws IOException
        {
            _value = EncodingUtils.readLong(buffer);
        }

        public AMQType getType()
        {
            return AMQType.LONG;
        }


        public Object getValue()
        {
            return _value;
        }

        public void writeToBuffer(DataOutput buffer) throws IOException
        {
            EncodingUtils.writeByte(buffer,AMQType.LONG.identifier());
            EncodingUtils.writeLong(buffer,_value);
        }


        public int getEncodingSize()
        {
            return EncodingUtils.encodedLongLength();
        }
    }

    private static final class IntTypedValue extends AMQTypedValue
    {
        @Override
        public String toString()
        {
            return "[INT: " + String.valueOf(_value) + "]";
        }

        private final int _value;

        public IntTypedValue(int value)
        {
            _value = value;
        }

        public AMQType getType()
        {
            return AMQType.INT;
        }


        public Object getValue()
        {
            return _value;
        }

        public void writeToBuffer(DataOutput buffer) throws IOException
        {
            EncodingUtils.writeByte(buffer,AMQType.INT.identifier());
            EncodingUtils.writeInteger(buffer, _value);
        }


        public int getEncodingSize()
        {
            return EncodingUtils.encodedIntegerLength();
        }
    }


    public static AMQTypedValue readFromBuffer(DataInput buffer) throws IOException
    {
        AMQType type = AMQTypeMap.getType(buffer.readByte());

        switch(type)
        {
            case LONG:
                return new LongTypedValue(buffer);

            case INT:
                int value = EncodingUtils.readInteger(buffer);
                return createAMQTypedValue(value);

            default:
                return new GenericTypedValue(type, buffer);
        }

    }

    private static final AMQTypedValue[] INT_VALUES = new AMQTypedValue[16];
    static
    {
        for(int i = 0 ; i < 16; i ++)
        {
            INT_VALUES[i] = new IntTypedValue(i);
        }
    }

    public static AMQTypedValue createAMQTypedValue(int i)
    {
        return (i & 0x0f) == i ? INT_VALUES[i] : new IntTypedValue(i);
    }


    public static AMQTypedValue createAMQTypedValue(long value)
    {
        return new LongTypedValue(value);
    }

    public static AMQTypedValue createAMQTypedValue(AMQType type, Object value)
    {
        switch(type)
        {
            case LONG:
                return new LongTypedValue((Long) AMQType.LONG.toNativeValue(value));
            case INT:
                return new IntTypedValue((Integer) AMQType.INT.toNativeValue(value));

            default:
                return new GenericTypedValue(type, value);
        }
    }



    public static AMQTypedValue toTypedValue(Object val)
    {
        if(val == null)
        {
            return AMQType.VOID.asTypedValue(null);
        }

        Class klass = val.getClass();
        if(klass == String.class)
        {
            return AMQType.LONG_STRING.asTypedValue(val);
        }
        else if(klass == Character.class)
        {
            return AMQType.ASCII_CHARACTER.asTypedValue(val);
        }
        else if(klass == Integer.class)
        {
            return AMQType.INT.asTypedValue(val);
        }
        else if(klass == Long.class)
        {
            return AMQType.LONG.asTypedValue(val);
        }
        else if(klass == Float.class)
        {
            return AMQType.FLOAT.asTypedValue(val);
        }
        else if(klass == Double.class)
        {
            return AMQType.DOUBLE.asTypedValue(val);
        }
        else if(klass == Date.class)
        {
            return AMQType.TIMESTAMP.asTypedValue(val);
        }
        else if(klass == Byte.class)
        {
            return AMQType.BYTE.asTypedValue(val);
        }
        else if(klass == Boolean.class)
        {
            return AMQType.BOOLEAN.asTypedValue(val);
        }
        else if(klass == byte[].class)
        {
            return AMQType.BINARY.asTypedValue(val);
        }
        else if(klass == BigDecimal.class)
        {
            return AMQType.DECIMAL.asTypedValue(val);
        }
        else if(val instanceof Map)
        {
            return AMQType.FIELD_TABLE.asTypedValue(FieldTable.convertToFieldTable((Map)val));
        }
        else if(klass == FieldTable.class)
        {
            return AMQType.FIELD_TABLE.asTypedValue(val);
        }
        else if(val instanceof Collection)
        {
            return AMQType.FIELD_ARRAY.asTypedValue(val);
        }
        throw new IllegalArgumentException("Cannot convert an object of class " + val.getClass().getName() + " to an AMQP typed value");
    }
}
