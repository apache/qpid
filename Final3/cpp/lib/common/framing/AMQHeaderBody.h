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
#include <amqp_types.h>
#include <AMQBody.h>
#include <Buffer.h>
#include <HeaderProperties.h>

#ifndef _AMQHeaderBody_
#define _AMQHeaderBody_

namespace qpid {
namespace framing {

class AMQHeaderBody : virtual public AMQBody
{
    HeaderProperties* properties;
    u_int16_t weight;
    u_int64_t contentSize;

    void createProperties(int classId);
public:
    typedef boost::shared_ptr<AMQHeaderBody> shared_ptr;

    AMQHeaderBody(int classId);
    AMQHeaderBody();
    inline u_int8_t type() const { return HEADER_BODY; }
    HeaderProperties* getProperties(){ return properties; }
    const HeaderProperties* getProperties() const { return properties; }
    inline u_int64_t getContentSize() const { return contentSize; }
    inline void setContentSize(u_int64_t _size) { contentSize = _size; }
    virtual ~AMQHeaderBody();
    virtual u_int32_t size() const;
    virtual void encode(Buffer& buffer) const;
    virtual void decode(Buffer& buffer, u_int32_t size);
    virtual void print(std::ostream& out) const;
};

}
}


#endif
