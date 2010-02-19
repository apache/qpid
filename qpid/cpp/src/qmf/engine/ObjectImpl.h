#ifndef _QmfEngineObjectImpl_
#define _QmfEngineObjectImpl_

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

#include <qmf/engine/Object.h>
#include <qpid/sys/Mutex.h>
#include <qpid/messaging/Variant.h>
#include <map>
#include <set>
#include <string>
#include <boost/shared_ptr.hpp>

namespace qmf {
namespace engine {

    class SchemaObjectClass;

    typedef boost::shared_ptr<Object> ObjectPtr;

    struct ObjectImpl {
        /**
         * Content of the object's data
         */
        qpid::messaging::Variant::Map values;

        /**
         * Schema reference if this object is "described"
         */
        SchemaObjectClass* objectClass;

        /**
         * Address and lifecycle information if this object is "managed"
         * The object is considered "managed" if the key is non-empty.
         */
        std::string key;
        uint64_t createTime;
        uint64_t destroyTime;
        uint64_t lastUpdatedTime;

        ObjectImpl();
        ObjectImpl(SchemaObjectClass* type);
        ~ObjectImpl() {}

        const qpid::messaging::Variant::Map& getValues() const { return values; }
        qpid::messaging::Variant::Map& getValues() { return values; }

        const SchemaObjectClass* getSchema() const { return objectClass; }
        void setSchema(SchemaObjectClass* schema) { objectClass = schema; }

        const char* getKey() const { return key.c_str(); }
        void setKey(const char* _key) { key = _key; }

        void touch();
        void destroy();
    };
}
}

#endif

