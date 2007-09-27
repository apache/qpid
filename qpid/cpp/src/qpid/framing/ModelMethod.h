#ifndef _ModelMethod_
#define _ModelMethod_

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
#include "AMQMethodBody.h"
#include "qpid/framing/ExecutionHeader.h"

namespace qpid {
namespace framing {


class ModelMethod : public AMQMethodBody 
{
    ExecutionHeader header;
public:    
    virtual ~ModelMethod() {}
    virtual void encode(Buffer& buffer) const { header.encode(buffer); }
    virtual void decode(Buffer& buffer, uint32_t size=0) { header.decode(buffer, size); }
    virtual uint32_t size() const { return header.size(); } 

    ExecutionHeader& getHeader() { return header; } 
    const ExecutionHeader& getHeader()  const { return header; } 
};


}} // namespace qpid::framing


#endif
