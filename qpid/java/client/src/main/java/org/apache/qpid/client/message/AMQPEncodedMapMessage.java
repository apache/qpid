package org.apache.qpid.client.message;
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


import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;

public class AMQPEncodedMapMessage extends JMSMapMessage
{
    public static final String MIME_TYPE = "amqp/map";
    
    public AMQPEncodedMapMessage(AMQMessageDelegateFactory delegateFactory) throws JMSException
    {
        this(delegateFactory, null);
    }

    AMQPEncodedMapMessage(AMQMessageDelegateFactory delegateFactory, ByteBuffer data) throws JMSException
    {
        super(delegateFactory, data); 
    }

    AMQPEncodedMapMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException
    {
        super(delegate, data);
    }
    
    @ Override
    protected String getMimeType()
    {
        return MIME_TYPE;
    }
    
    @ Override
    public void setObject(String propName, Object value) throws JMSException
    {
        checkWritable();
        checkPropertyName(propName);
        if ((value instanceof Boolean) || (value instanceof Byte) || (value instanceof Short) || (value instanceof Integer)
                || (value instanceof Long) || (value instanceof Character) || (value instanceof Float)
                || (value instanceof Double) || (value instanceof String) || (value instanceof byte[])
                || (value instanceof List) || (value instanceof Map) || (value instanceof UUID) || (value == null))
        {
            _map.put(propName, value);
        }
        else
        {
            throw new MessageFormatException("Cannot set property " + propName + " to value " + value + "of type "
                + value.getClass().getName() + ".");
        }
    }

    // The super clas methods resets the buffer
    @ Override
    public ByteBuffer getData()
    {
        writeMapToData();
        return _data;
    }
    
    @ Override
    protected void populateMapFromData() throws JMSException
    {
        if (_data != null)
        {
            _data.rewind();
            BBDecoder decoder = new BBDecoder();
            decoder.init(_data.buf());
            _map = decoder.readMap();
        }
        else
        {
            _map.clear();
        }
    }

    @ Override
    protected void writeMapToData()
    {
        BBEncoder encoder = new BBEncoder(1024);
        encoder.writeMap(_map);
        _data = ByteBuffer.wrap(encoder.segment());
    }
    
    // for testing
    Map<String,Object> getMap()
    {
        return _map;
    }
    
    void setMap(Map<String,Object> map)
    {
        _map = map;
    }
}
