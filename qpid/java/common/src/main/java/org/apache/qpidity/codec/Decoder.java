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
package org.apache.qpidity.codec;

import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.RangeSet;
import org.apache.qpidity.Struct;


/**
 * Decoder
 *
 * @author Rafael H. Schloming
 */

public interface Decoder
{

    boolean readBit();
    short readOctet();
    int readShort();
    long readLong();
    long readLonglong();

    long readTimestamp();

    String readShortstr();
    String readLongstr();

    Map<String,?> readTable();
    RangeSet readRfc1982LongSet();
    UUID readUuid();

    String readContent();

    Struct readLongStruct();

}
