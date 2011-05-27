#ifndef _QMF_SCHEMA_METHOD_IMPL_H_
#define _QMF_SCHEMA_METHOD_IMPL_H_
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
#include "qmf/SchemaTypes.h"
#include "qmf/SchemaMethod.h"
#include "qmf/SchemaPropertyImpl.h"
#include "qpid/management/Buffer.h"
#include <list>
#include <string>

namespace qpid {
namespace management {
    class Buffer;
}}

namespace qmf {
    class Hash;
    class SchemaMethodImpl : public virtual qpid::RefCounted {
    public:
        //
        // Public impl-only methods
        //
        SchemaMethodImpl(const qpid::types::Variant::Map& m);
        SchemaMethodImpl(qpid::management::Buffer& v1Buffer);
        qpid::types::Variant::Map asMap() const;
        void updateHash(Hash&) const;
        void encodeV1(qpid::management::Buffer&) const;

        //
        // Methods from API handle
        //
        SchemaMethodImpl(const std::string& n, const std::string& options);

        void setDesc(const std::string& d) { desc = d; }
        void addArgument(const SchemaProperty& p) { arguments.push_back(p); }
        const std::string& getName() const { return name; }
        const std::string& getDesc() const { return desc; }
        uint32_t getArgumentCount() const { return arguments.size(); }
        SchemaProperty getArgument(uint32_t i) const;

    private:
        std::string name;
        std::string desc;
        std::list<SchemaProperty> arguments;
    };

    struct SchemaMethodImplAccess
    {
        static SchemaMethodImpl& get(SchemaMethod&);
        static const SchemaMethodImpl& get(const SchemaMethod&);
    };
}

#endif
