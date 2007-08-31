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

#include <stdlib.h> // For alloca

namespace qpid {
namespace framing {

class StructHelper
{
public:

    template <class T> void encode(const T t, std::string& data) {
        uint32_t size = t.size() + 2/*type*/;
        char* bytes = static_cast<char*>(::alloca(size));
        Buffer wbuffer(bytes, size);
        wbuffer.putShort(T::TYPE);
        t.encode(wbuffer);
        
        Buffer rbuffer(bytes, size);
        rbuffer.getRawData(data, size);        
    }

    template <class T> void decode(T t, std::string& data) {
        char* bytes = static_cast<char*>(::alloca(data.length()));
        Buffer wbuffer(bytes, data.length());
        wbuffer.putRawData(data);        

        Buffer rbuffer(bytes, data.length());
        uint16_t type = rbuffer.getShort();
        if (type == T::TYPE) {
            t.decode(rbuffer);
        } else {
            throw Exception("Type code does not match");
        }
    }
};

}}
#endif  
