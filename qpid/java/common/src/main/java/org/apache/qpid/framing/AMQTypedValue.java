package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

public class AMQTypedValue
{
    private final AMQType _type;
    private final Object _value;


    public AMQTypedValue(AMQType type, Object value)
    {
        if(type == null)
        {
            throw new NullPointerException("Cannot create a typed value with null type");
        }
        _type = type;
        _value = type.toNativeValue(value);
    }

    private AMQTypedValue(AMQType type, ByteBuffer buffer)
    {
        _type = type;
        _value = type.readValueFromBuffer( buffer );
    }


    public AMQType getType()
    {
        return _type;
    }

    public Object getValue()
    {
        return _value;
    }


    public void writeToBuffer(ByteBuffer buffer)
    {
        _type.writeToBuffer(_value,buffer);
    }

    public int getEncodingSize()
    {
        return _type.getEncodingSize(_value);
    }

    public static AMQTypedValue readFromBuffer(ByteBuffer buffer)
    {
        AMQType type = AMQTypeMap.getType(buffer.get());
        return new AMQTypedValue(type, buffer);
    }

    public String toString()
    {
        return "["+getType()+": "+getValue()+"]";
    }
}
