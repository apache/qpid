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
 * Encoder
 *
 * @author Rafael H. Schloming
 */

public interface Encoder
{

    void flush();

    void writeBit(boolean b);
    void writeOctet(short b);
    void writeShort(int s);
    void writeLong(long i);
    void writeLonglong(long l);

    void writeTimestamp(long l);

    void writeShortstr(String s);
    void writeLongstr(String s);

    void writeRfc1982LongSet(RangeSet ranges);
    void writeUuid(UUID uuid);

    void writeContent(String c);

    void writeStruct(int type, Struct s);
    void writeLongStruct(Struct s);

    void writeTable(Map<String,Object> table);
    void writeSequence(List<Object> sequence);
    void writeArray(List<Object> array);

}
