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
#include <string>
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FrameHandler.h"

#ifndef _TypeFilter_
#define _TypeFilter_

namespace qpid {
namespace framing {

/**
 * Predicate that selects frames by type
 */
class TypeFilter
{
    std::vector<uint8_t> types;
public:
    TypeFilter(uint8_t type) { add(type); }
    TypeFilter(uint8_t type1, uint8_t type2) { add(type1); add(type2); }
    void add(uint8_t type) { types.push_back(type); }
    bool operator()(const AMQFrame& f) const 
    { 
        return find(types.begin(), types.end(), f.getBody()->type()) != types.end(); 
    } 
};

}
}


#endif
