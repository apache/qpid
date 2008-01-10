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
#include "amqp_types.h"
#include "qpid/Exception.h"

#ifndef _Buffer_
#define _Buffer_

namespace qpid {
namespace framing {

struct OutOfBounds : qpid::Exception {};

class Content;
class FieldTable;

class Buffer
{
    uint32_t size;
    char* data;
    uint32_t position;
    uint32_t r_position;

    void checkAvailable(uint32_t count) { if (position + count > size) throw OutOfBounds(); }

public:

    Buffer(char* data=0, uint32_t size=0);

    void record();
    void restore(bool reRecord = false);
    void reset();

    uint32_t available() { return size - position; }
    uint32_t getSize() { return size; }
    uint32_t getPosition() { return position; }
        
    void putOctet(uint8_t i);
    void putShort(uint16_t i);
    void putLong(uint32_t i);
    void putLongLong(uint64_t i);

    uint8_t getOctet();
    uint16_t getShort(); 
    uint32_t getLong();
    uint64_t getLongLong();
    
    template <int n>
    uint64_t getUInt();

    template <int n>
    void putUInt(uint64_t);

    void putShortString(const string& s);
    void putLongString(const string& s);
    void getShortString(string& s);
    void getLongString(string& s);

    void putRawData(const string& s);
    void getRawData(string& s, uint32_t size);

    void putRawData(const uint8_t* data, size_t size);
    void getRawData(uint8_t* data, size_t size);

    template <class T> void put(const T& data) { data.encode(*this); }
    template <class T> void get(T& data) { data.decode(*this); }
};

}} // namespace qpid::framing


#endif
