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
#ifndef _QPID_CONSOLE_OBJECT_H_
#define _QPID_CONSOLE_OBJECT_H_

#include "ObjectId.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/FieldTable.h"
#include <boost/shared_ptr.hpp>
#include <map>
#include <set>
#include <vector>

namespace qpid {
namespace framing {
    class Buffer;
}
namespace console {

    class Broker;
    class SchemaClass;
    class SchemaMethod;
    class ObjectId;
    class ClassKey;
    class Value;

    /**
     * \ingroup qmfconsoleapi
     */
    struct MethodResponse {
        uint32_t code;
        std::string text;
        std::map<std::string, boost::shared_ptr<Value> > arguments;
    };

    class Object {
    public:
        typedef std::vector<Object> Vector;
        struct AttributeMap : public std::map<std::string, boost::shared_ptr<Value> > {
            void addRef(const std::string& key, const ObjectId& val);
            void addUint(const std::string& key, uint32_t val);
            void addInt(const std::string& key, int32_t val);
            void addUint64(const std::string& key, uint64_t val);
            void addInt64(const std::string& key, int64_t val);
            void addString(const std::string& key, const std::string& val);
            void addBool(const std::string& key, bool val);
            void addFloat(const std::string& key, float val);
            void addDouble(const std::string& key, double val);
            void addUuid(const std::string& key, const framing::Uuid& val);
            void addMap(const std::string& key, const framing::FieldTable& val);
        };

        Object(Broker* broker, SchemaClass* schemaClass, framing::Buffer& buffer, bool prop, bool stat);
        ~Object();

        Broker* getBroker() const { return broker; }
        const ObjectId& getObjectId() const { return objectId; }
        const ClassKey& getClassKey() const;
        SchemaClass* getSchema() const { return schema; }
        uint64_t getCurrentTime() const { return currentTime; }
        uint64_t getCreateTime() const { return createTime; }
        uint64_t getDeleteTime() const { return deleteTime; }
        bool isDeleted() const { return deleteTime != 0; }
        std::string getIndex() const;
        void mergeUpdate(const Object& updated);
        const AttributeMap& getAttributes() const { return attributes; }
        void invokeMethod(const std::string name, const AttributeMap& args, MethodResponse& result);
        void handleMethodResp(framing::Buffer& buffer, uint32_t sequence);

        ObjectId attrRef(const std::string& key) const;
        uint32_t attrUint(const std::string& key) const;
        int32_t attrInt(const std::string& key) const;
        uint64_t attrUint64(const std::string& key) const;
        int64_t attrInt64(const std::string& key) const;
        std::string attrString(const std::string& key) const;
        bool attrBool(const std::string& key) const;
        float attrFloat(const std::string& key) const;
        double attrDouble(const std::string& key) const;
        framing::Uuid attrUuid(const std::string& key) const;
        framing::FieldTable attrMap(const std::string& key) const;

    private:
        Broker* broker;
        SchemaClass* schema;
        ObjectId objectId;
        uint64_t currentTime;
        uint64_t createTime;
        uint64_t deleteTime;
        AttributeMap attributes;
        SchemaMethod* pendingMethod;
        MethodResponse methodResponse;

        void parsePresenceMasks(framing::Buffer& buffer, std::set<std::string>& excludeList);
    };

    std::ostream& operator<<(std::ostream& o, const Object& object);
}
}


#endif
