#ifndef _QmfObjectIdImpl_
#define _QmfObjectIdImpl_

/*
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
 */

#include <qmf/ObjectId.h>
#include <qpid/framing/Buffer.h>

namespace qmf {

    struct AgentAttachment {
        uint64_t first;

        AgentAttachment() : first(0) {}
        void setBanks(uint32_t broker, uint32_t bank);
        uint64_t getFirst() const { return first; }
    };

    struct ObjectIdImpl {
        AgentAttachment* agent;
        uint64_t first;
        uint64_t second;

        ObjectIdImpl() : agent(0), first(0), second(0) {}
        ObjectIdImpl(qpid::framing::Buffer& buffer);
        ObjectIdImpl(AgentAttachment* agent, uint8_t flags, uint16_t seq, uint64_t object);

        static ObjectId* factory(qpid::framing::Buffer& buffer);
        static ObjectId* factory(AgentAttachment* agent, uint8_t flags, uint16_t seq, uint64_t object);

        void decode(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void fromString(const std::string& repr);
        std::string asString() const;
        uint8_t getFlags() const { return (first & 0xF000000000000000LL) >> 60; }
        uint16_t getSequence() const { return (first & 0x0FFF000000000000LL) >> 48; }
        uint32_t getBrokerBank() const { return (first & 0x0000FFFFF0000000LL) >> 28; }
        uint32_t getAgentBank() const { return first & 0x000000000FFFFFFFLL; }
        uint64_t getObjectNum() const { return second; }
        uint32_t getObjectNumHi() const { return (uint32_t) (second >> 32); }
        uint32_t getObjectNumLo() const { return (uint32_t) (second & 0x00000000FFFFFFFFLL); }
        bool isDurable() const { return getSequence() == 0; }
        void setValue(uint64_t f, uint64_t s) { first = f; second = s; }

        bool operator==(const ObjectIdImpl& other) const;
        bool operator<(const ObjectIdImpl& other) const;
        bool operator>(const ObjectIdImpl& other) const;
    };
}

#endif

