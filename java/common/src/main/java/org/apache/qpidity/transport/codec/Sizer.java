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
        public void writeUint8(short b) {}
        public void writeUint16(int s) {}
        public void writeUint32(long i) {}
        public void writeUint64(long l) {}

        public void writeDatetime(long l) {}
        public void writeUuid(UUID uuid) {}

        public void writeSequenceNo(int s) {}
        public void writeSequenceSet(RangeSet ranges) {} // XXX
        public void writeByteRanges(RangeSet ranges) {} // XXX

        public void writeStr8(String s) {}
        public void writeStr16(String s) {}

        public void writeVbin8(byte[] bytes) {}
        public void writeVbin16(byte[] bytes) {}
        public void writeVbin32(byte[] bytes) {}

        public void writeStruct32(Struct s) {}
        public void writeMap(Map<String,Object> map) {}
        public void writeList(List<Object> list) {}
        public void writeArray(List<Object> array) {}

        public void writeStruct(int type, Struct s) {}

        public int getSize() { return 0; }

        public int size() { return 0; }
    };

    int getSize();

    int size();

}
