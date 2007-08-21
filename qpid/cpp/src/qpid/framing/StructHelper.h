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
#ifndef _StructHelper_
#define _StructHelper_

#include "qpid/Exception.h"
#include "Buffer.h"

namespace qpid {
namespace framing {

class StructHelper
{
public:

    template <class T> void encode(const T t, std::string& data) {
        uint32_t size = t.size() + 2/*type*/;
        Buffer buffer(size);
        buffer.putShort(T::TYPE);
        t.encode(buffer);
        buffer.flip();
        buffer.getRawData(data, size);        
    }

    template <class T> void decode(T t, std::string& data) {
        Buffer buffer(data.length());
        buffer.putRawData(data);        
        buffer.flip();
        uint16_t type = buffer.getShort();
        if (type == T::TYPE) {
            t.decode(buffer);
        } else {
            throw Exception("Type code does not match");
        }
    }
};

}}
#endif  
