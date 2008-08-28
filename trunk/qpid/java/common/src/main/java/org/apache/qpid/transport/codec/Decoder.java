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
package org.apache.qpid.transport.codec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.Struct;


/**
 * Decoder
 *
 * @author Rafael H. Schloming
 */

public interface Decoder
{

    boolean hasRemaining();

    short readUint8();
    int readUint16();
    long readUint32();
    long readUint64();

    long readDatetime();
    UUID readUuid();

    int readSequenceNo();
    RangeSet readSequenceSet(); // XXX
    RangeSet readByteRanges(); // XXX

    String readStr8();
    String readStr16();

    byte[] readVbin8();
    byte[] readVbin16();
    byte[] readVbin32();

    Struct readStruct32();
    Map<String,Object> readMap();
    List<Object> readList();
    List<Object> readArray();

    Struct readStruct(int type);

}
