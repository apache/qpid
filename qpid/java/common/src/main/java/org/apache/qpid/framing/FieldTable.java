/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

import java.util.Map;
import java.util.Enumeration;

public interface FieldTable extends Map
{
    void writeToBuffer(ByteBuffer buffer);

    void setFromBuffer(ByteBuffer buffer, long length) throws AMQFrameDecodingException;    

    byte[] getDataAsBytes();

    public long getEncodedSize();

    Object put(Object key, Object value);

    Object remove(Object key);


    public Enumeration getPropertyNames();

    public boolean propertyExists(String propertyName);

    //Getters

    public Boolean getBoolean(String string);

    public Byte getByte(String string);

    public Short getShort(String string);

    public Integer getInteger(String string);

    public Long getLong(String string);

    public Float getFloat(String string);

    public Double getDouble(String string);

    public String getString(String string);

    public Character getCharacter(String string);

    public byte[] getBytes(String string);

    public Object getObject(String string);

    // Setters
    public Object setBoolean(String string, boolean b);

    public Object setByte(String string, byte b);

    public Object setShort(String string, short i);

    public Object setInteger(String string, int i);

    public Object setLong(String string, long l);

    public Object setFloat(String string, float v);

    public Object setDouble(String string, double v);

    public Object setString(String string, String string1);

    public Object setChar(String string, char c);

    public Object setBytes(String string, byte[] bytes);

    public Object setBytes(String string, byte[] bytes, int start, int length);

    public Object setObject(String string, Object object);

}
