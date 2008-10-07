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
#include "Buffer.h"
#include "AMQDataBlock.h"
#include "ProtocolVersion.h"

#ifndef _ProtocolInitiation_
#define _ProtocolInitiation_

namespace qpid {
namespace framing {

class ProtocolInitiation : public AMQDataBlock
{
private:
    ProtocolVersion version;
        
public:
    ProtocolInitiation();
    ProtocolInitiation(uint8_t major, uint8_t minor);
    ProtocolInitiation(ProtocolVersion p);
    virtual ~ProtocolInitiation();
    virtual void encode(Buffer& buffer) const; 
    virtual bool decode(Buffer& buffer); 
    inline virtual uint32_t encodedSize() const { return 8; }
    inline uint8_t getMajor() const { return version.getMajor(); }
    inline uint8_t getMinor() const { return version.getMinor(); }
    inline ProtocolVersion getVersion() const { return version; }
    bool operator==(ProtocolVersion v) const { return v == getVersion(); }
};

std::ostream& operator<<(std::ostream& o, const framing::ProtocolInitiation& pi);


}
}


#endif
