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

import org.apache.mina.common.ByteBuffer;

import java.math.BigDecimal;
import java.math.BigInteger;

public enum AMQType
{
    //AMQP FieldTable Wire Types

    LONG_STRING('S')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedLongStringLength((String) value);
        }


        public String toNativeValue(Object value)
        {
            if (value != null)
            {
                return value.toString();
            }
            else
            {
                throw new NullPointerException("Cannot convert: null to String.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeLongStringBytes(buffer, (String) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readLongString(buffer);
        }

    },

    INTEGER('i')
    {

        public int getEncodingSize(Object value)
        {
            return EncodingUtils.unsignedIntegerLength();
        }

        public Long toNativeValue(Object value)
        {
            if (value instanceof Long)
            {
                return (Long) value;
            }
            else if (value instanceof Integer)
            {
                return ((Integer) value).longValue();
            }
            else if (value instanceof Short)
            {
                return ((Short) value).longValue();
            }
            else if (value instanceof Byte)
            {
                return ((Byte) value).longValue();
            }
            else if ((value instanceof String) || (value == null))
            {
                return Long.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to int.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeUnsignedInteger(buffer, (Long) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readUnsignedInteger(buffer);
        }
    },

    DECIMAL('D')
    {

        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedByteLength()+ EncodingUtils.encodedIntegerLength();
        }

        public Object toNativeValue(Object value)
        {
            if(value instanceof BigDecimal)
            {
                return (BigDecimal) value;
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to BigDecimal.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            BigDecimal bd = (BigDecimal) value;

            byte places = new Integer(bd.scale()).byteValue();

            int unscaled = bd.intValue();

            EncodingUtils.writeByte(buffer, places);

            EncodingUtils.writeInteger(buffer, unscaled);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            byte places = EncodingUtils.readByte(buffer);

            int unscaled = EncodingUtils.readInteger(buffer);

            BigDecimal bd = new BigDecimal(unscaled);
            return bd.setScale(places);            
        }
    },

    TIMESTAMP('T')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedLongLength();
        }

        public Object toNativeValue(Object value)
        {
            if(value instanceof Long)
            {
                return (Long) value;
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to timestamp.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeLong(buffer, (Long) value);
        }


        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readLong(buffer);
        }
    },

    FIELD_TABLE('F')
    {
        public int getEncodingSize(Object value)
        {
            // TODO : fixme
            throw new UnsupportedOperationException();
        }

        public Object toNativeValue(Object value)
        {
            // TODO : fixme
            throw new UnsupportedOperationException();
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            // TODO : fixme
            throw new UnsupportedOperationException();
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            // TODO : fixme
            throw new UnsupportedOperationException();
        }
    },

    VOID('V')
    {
        public int getEncodingSize(Object value)
        {
            return 0;
        }


        public Object toNativeValue(Object value)
        {
            if (value == null)
            {
                return null;
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to null String.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return null;
        }
    },

    // Extended types

    BINARY('x')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedLongstrLength((byte[]) value);
        }


        public Object toNativeValue(Object value)
        {
            if((value instanceof byte[]) || (value == null))
            {
                return value;
            }
            else
            {
                throw new IllegalArgumentException("Value: " + value + " (" + value.getClass().getName() +
                                                    ") cannot be converted to byte[]");
            }
        }


        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeLongstr(buffer, (byte[]) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readLongstr(buffer);
        }

    },

    ASCII_STRING('c')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedLongStringLength((String) value);
        }


        public String toNativeValue(Object value)
        {
            if (value != null)
            {
                return value.toString();
            }
            else
            {
                throw new NullPointerException("Cannot convert: null to String.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeLongStringBytes(buffer, (String) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readLongString(buffer);
        }

    },

    WIDE_STRING('C')
    {
        public int getEncodingSize(Object value)
        {
            // FIXME: use proper charset encoder
            return EncodingUtils.encodedLongStringLength((String) value);
        }


        public String toNativeValue(Object value)
        {
            if (value != null)
            {
                return value.toString();
            }
            else
            {
                throw new NullPointerException("Cannot convert: null to String.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeLongStringBytes(buffer, (String) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readLongString(buffer);
        }
    },

    BOOLEAN('t')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedBooleanLength();
        }


        public Object toNativeValue(Object value)
        {
            if (value instanceof Boolean)
            {
                return (Boolean) value;
            }
            else if ((value instanceof String) || (value == null))
            {
                return Boolean.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to boolean.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeBoolean(buffer, (Boolean) value);
        }


        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readBoolean(buffer);
        }
    },

    ASCII_CHARACTER('k')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedCharLength();
        }


        public Character toNativeValue(Object value)
        {
            if (value instanceof Character)
            {
                return (Character) value;
            }
            else if (value == null)
            {
                throw new NullPointerException("Cannot convert null into char");
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to char.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeChar(buffer, (Character) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readChar(buffer);
        }

    },

    BYTE('b')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedByteLength();
        }


        public Byte toNativeValue(Object value)
        {
            if (value instanceof Byte)
            {
                return (Byte) value;
            }
            else if ((value instanceof String) || (value == null))
            {
                return Byte.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to byte.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeByte(buffer, (Byte) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readByte(buffer);
        }
    },

    SHORT('s')
    {

        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedShortLength();
        }


        public Short toNativeValue(Object value)
        {
            if (value instanceof Short)
            {
                return (Short) value;
            }
            else if (value instanceof Byte)
            {
                return ((Byte) value).shortValue();
            }
            else if ((value instanceof String) || (value == null))
            {
                return Short.valueOf((String)value);
            }

            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to short.");
            }


        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeShort(buffer, (Short) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readShort(buffer);
        }
    },

    INT('I')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedIntegerLength();
        }

        public Integer toNativeValue(Object value)
        {
            if (value instanceof Integer)
            {
                return (Integer) value;
            }
            else if (value instanceof Short)
            {
                return ((Short) value).intValue();
            }
            else if (value instanceof Byte)
            {
                return ((Byte) value).intValue();
            }
            else if ((value instanceof String) || (value == null))
            {
                return Integer.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to int.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeInteger(buffer, (Integer) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readInteger(buffer);
        }
    },

    LONG('l')
    {

        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedLongLength();
        }

        public Object toNativeValue(Object value)
        {
            if(value instanceof Long)
            {
                return (Long) value;
            }
            else if (value instanceof Integer)
            {
                return ((Integer) value).longValue();
            }
            else if (value instanceof Short)
            {
                return ((Short) value).longValue();
            }
            else if (value instanceof Byte)
            {
                return ((Byte) value).longValue();
            }
            else if ((value instanceof String) || (value == null))
            {
                return Long.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to long.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeLong(buffer, (Long) value);
        }


        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readLong(buffer);
        }
    },

    FLOAT('f')
    {
        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedFloatLength();
        }


        public Float toNativeValue(Object value)
        {
            if (value instanceof Float)
            {
                return (Float) value;
            }
            else if ((value instanceof String) || (value == null))
            {
                return Float.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to float.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeFloat(buffer, (Float) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readFloat(buffer);
        }
    },

    DOUBLE('d')
    {

        public int getEncodingSize(Object value)
        {
            return EncodingUtils.encodedDoubleLength();
        }


        public Double toNativeValue(Object value)
        {
            if (value instanceof Double)
            {
                return (Double) value;
            }
            else if (value instanceof Float)
            {
                return ((Float) value).doubleValue();
            }
            else if ((value instanceof String) || (value == null))
            {
                return Double.valueOf((String)value);
            }
            else
            {
                throw new NumberFormatException("Cannot convert: " + value + "(" +
                                                value.getClass().getName() + ") to double.");
            }
        }

        public void writeValueImpl(Object value, ByteBuffer buffer)
        {
            EncodingUtils.writeDouble(buffer, (Double) value);
        }

        public Object readValueFromBuffer(ByteBuffer buffer)
        {
            return EncodingUtils.readDouble(buffer);
        }
    };

    private final byte _identifier;

    AMQType(char identifier)
    {
        _identifier = (byte) identifier;
    }

    public final byte identifier()
    {
        return _identifier;
    }


    public abstract int getEncodingSize(Object value);

    public abstract Object toNativeValue(Object value);

    public AMQTypedValue asTypedValue(Object value)
    {
        return new AMQTypedValue(this, toNativeValue(value));
    }

    public void writeToBuffer(Object value, ByteBuffer buffer)
    {
        buffer.put((byte)identifier());
        writeValueImpl(value, buffer);
    }

    abstract void writeValueImpl(Object value, ByteBuffer buffer);

    abstract Object readValueFromBuffer(ByteBuffer buffer);
}
