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
package org.apache.qpidity.transport.codec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.RangeSet;
import org.apache.qpidity.transport.Struct;


/**
 * Sizer
 *
 */

public interface Sizer extends Encoder
{

    public static final Sizer NULL = new Sizer()
    {
        public void flush() {};

        public void writeBit(boolean b) {};
        public void writeOctet(short b) {};
        public void writeShort(int s) {};
        public void writeLong(long i) {};
        public void writeLonglong(long l) {};

        public void writeTimestamp(long l) {};

        public void writeShortstr(String s) {};
        public void writeLongstr(String s) {};

        public void writeRfc1982LongSet(RangeSet ranges) {};
        public void writeUuid(UUID uuid) {};

        public void writeContent(String c) {};

        public void writeStruct(int type, Struct s) {};
        public void writeLongStruct(Struct s) {};

        public void writeTable(Map<String,Object> table) {};
        public void writeSequence(List<Object> sequence) {};
        public void writeArray(List<Object> array) {};

        public int getSize() { return 0; }

        public int size() { return 0; }
    };

    int getSize();

    int size();

}
