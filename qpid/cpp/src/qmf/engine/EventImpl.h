#ifndef _QmfEngineEventImpl_
#define _QmfEngineEventImpl_

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

#include <qmf/engine/Event.h>
#include <qmf/engine/Schema.h>
#include <qpid/framing/Buffer.h>
#include <boost/shared_ptr.hpp>
#include <map>

namespace qmf {
namespace engine {

    struct EventImpl {
        typedef boost::shared_ptr<Value> ValuePtr;
        const SchemaEventClass* eventClass;
        mutable std::map<std::string, ValuePtr> properties;

        EventImpl(const SchemaEventClass* type);
        EventImpl(const SchemaEventClass* type, qpid::framing::Buffer& buffer);
        static Event* factory(const SchemaEventClass* type, qpid::framing::Buffer& buffer);

        const SchemaEventClass* getClass() const { return eventClass; }
        Value* getValue(const char* key) const;

        void encodeSchemaKey(qpid::framing::Buffer& buffer) const;
        void encode(qpid::framing::Buffer& buffer) const;
        std::string getRoutingKey(uint32_t brokerBank, uint32_t agentBank) const;
    };

}
}

#endif

