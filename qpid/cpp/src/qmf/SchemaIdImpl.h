#ifndef _QMF_SCHEMA_ID_IMPL_H_
#define _QMF_SCHEMA_ID_IMPL_H_
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
#include "qmf/SchemaId.h"
#include "qpid/types/Variant.h"
#include "qpid/types/Uuid.h"
#include <string>

namespace qmf {
    class SchemaIdImpl : public virtual qpid::RefCounted {
    public:
        //
        // Public impl-only methods
        //
        SchemaIdImpl(const qpid::types::Variant::Map&);
        qpid::types::Variant::Map asMap() const;

        //
        // Methods from API handle
        //
        SchemaIdImpl(int t, const std::string& p, const std::string& n) : sType(t), package(p), name(n) {}
        void setHash(const qpid::types::Uuid& h) { hash = h; }
        int getType() const { return sType; }
        const std::string& getPackageName() const { return package; }
        const std::string& getName() const { return name; }
        const qpid::types::Uuid& getHash() const { return hash; }

    private:
        int sType;
        std::string package;
        std::string name;
        qpid::types::Uuid hash;
    };

    struct SchemaIdImplAccess
    {
        static SchemaIdImpl& get(SchemaId&);
        static const SchemaIdImpl& get(const SchemaId&);
    };

    struct SchemaIdCompare {
        bool operator() (const SchemaId& lhs, const SchemaId& rhs) const
        {
            if (lhs.getName() != rhs.getName())
                return lhs.getName() < rhs.getName();
            if (lhs.getPackageName() != rhs.getPackageName())
                return lhs.getPackageName() < rhs.getPackageName();
            return lhs.getHash() < rhs.getHash();
        }
    };

    struct SchemaIdCompareNoHash {
        bool operator() (const SchemaId& lhs, const SchemaId& rhs) const
        {
            if (lhs.getName() != rhs.getName())
                return lhs.getName() < rhs.getName();
            return lhs.getPackageName() < rhs.getPackageName();
        }
    };
}

#endif
