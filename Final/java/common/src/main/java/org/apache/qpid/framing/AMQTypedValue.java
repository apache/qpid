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
