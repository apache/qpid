#ifndef _QmfEngineDataImpl_
#define _QmfEngineDataImpl_

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

#include <qmf/engine/Data.h>
#include <qpid/sys/Mutex.h>
#include <qpid/messaging/Variant.h>
#include <map>
#include <set>
#include <string>
#include <boost/shared_ptr.hpp>

namespace qmf {
namespace engine {

    class SchemaClass;

    typedef boost::shared_ptr<Data> DataPtr;

    class DataManager {
    public:
        virtual ~DataManager() {}
        virtual void modifyStart(DataPtr data) = 0;
        virtual void modifyDone(DataPtr data) = 0;
        virtual void destroy(DataPtr data) = 0;
        
    };

    struct DataImpl {
        qpid::sys::Mutex lock;

        /**
         * Content of the object's data
         */
        qpid::messaging::Variant::Map values;
        qpid::messaging::Variant::Map subtypes;

        /**
         * Schema reference if this object is "described"
         */
        SchemaClass* objectClass;

        /**
         * Address and lifecycle information if this object is "managed"
         * The object is considered "managed" if the key is non-empty.
         */
        std::string key;
        uint64_t createTime;
        uint64_t destroyTime;
        uint64_t lastUpdatedTime;

        DataManager* manager;
        DataPtr parent;

        DataImpl();
        DataImpl(SchemaClass* type, const qpid::messaging::Variant::Map&);
        DataImpl(const DataImpl& from) :
            values(from.values), subtypes(from.subtypes), objectClass(from.objectClass),
            key(from.key), createTime(from.createTime), destroyTime(from.destroyTime),
            lastUpdatedTime(from.lastUpdatedTime), manager(0) {}
        ~DataImpl() {}

        const qpid::messaging::Variant::Map& getValues() const { return values; }
        qpid::messaging::Variant::Map& getValues() { return values; }

        const qpid::messaging::Variant::Map& getSubtypes() const { return subtypes; }
        qpid::messaging::Variant::Map& getSubtypes() { return subtypes; }

        const SchemaClass* getSchema() const { return objectClass; }

        const char* getKey() const { return key.c_str(); }
        void setKey(const char* _key) { key = _key; }

        void modifyStart();
        void modifyDone();
        void destroy();

        qpid::messaging::Variant::Map asMap() const;
        qpid::messaging::Variant::Map asMapDelta(Data& base) const;
        void registerManager(DataManager* manager, DataPtr data);
    };
}
}

#endif

