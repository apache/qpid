#ifndef _QMF_DATA_ADDR_IMPL_H_
#define _QMF_DATA_ADDR_IMPL_H_
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

#include "qpid/RefCounted.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/types/Variant.h"
#include "qmf/DataAddr.h"

namespace qmf {
    class DataAddrImpl : public virtual qpid::RefCounted {
    public:
        //
        // Impl-only methods
        //
        void setName(const std::string& n) { name = n; }
        void setAgent(const std::string& n, uint32_t e=0) { agentName = n; agentEpoch = e; }

        //
        // Methods from API handle
        //
        bool operator==(const DataAddrImpl&) const;
        bool operator<(const DataAddrImpl&) const;
        DataAddrImpl(const qpid::types::Variant::Map&);
        DataAddrImpl(const std::string& _name, const std::string& _agentName, uint32_t _agentEpoch=0) :
            agentName(_agentName), name(_name), agentEpoch(_agentEpoch) {}
        const std::string& getName() const { return name; }
        const std::string& getAgentName() const { return agentName; }
        uint32_t getAgentEpoch() const { return agentEpoch; }
        qpid::types::Variant::Map asMap() const;

    private:
        std::string agentName;
        std::string name;
        uint32_t agentEpoch;
    };

    struct DataAddrImplAccess
    {
        static DataAddrImpl& get(DataAddr&);
        static const DataAddrImpl& get(const DataAddr&);
    };

    struct DataAddrCompare {
        bool operator() (const DataAddr& lhs, const DataAddr& rhs) const
        {
            if (lhs.getName() != rhs.getName())
                return lhs.getName() < rhs.getName();
            return lhs.getAgentName() < rhs.getAgentName();
        }
    };
}

#endif
