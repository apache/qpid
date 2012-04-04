/*
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
 */

package org.apache.qpid.amqp_1_0.codec;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface Encoder
{

    public boolean writeNull();

    public boolean writeBoolean(boolean b);

    public boolean writeByte(byte b);

    public boolean writeUnsignedByte(ByteBuffer buf, UnsignedByte ub);

    public boolean writeShort(ByteBuffer buf, short s);

    public boolean writeUnsignedShort(UnsignedShort s);

    public boolean writeInt(int i);

    public boolean writeUnsignedInt(UnsignedInteger i);

    public boolean writeLong(long l);

    public boolean writeUnsignedLong(UnsignedLong l);

    public boolean writeFloat(float f);

    public boolean writeDouble(double d);

    public boolean writeChar(char c);

    public boolean writeTimestamp(Date d);

    public boolean writeSymbol(Symbol s);

    public boolean writeString(String s);

    public boolean writeBytes(byte[] bytes);

    public boolean writeBytes(Binary bin);

    public boolean writeList(List l);

    public boolean writeMap(Map m);

    public boolean writeEncodable(EncodableValue o);

    public boolean writeDescribedType(EncodableValue descriptor, EncodableValue described);

    interface EncodableValue
    {
        void encode(Encoder encoder);
    }
}
