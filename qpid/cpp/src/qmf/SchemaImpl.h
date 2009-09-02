#ifndef _QmfSchemaImpl_
#define _QmfSchemaImpl_

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

#include "qmf/Schema.h"
#include <boost/shared_ptr.hpp>
#include <string>
#include <vector>
#include <qpid/framing/Buffer.h>

namespace qmf {

    // TODO: Destructors for schema classes
    // TODO: Add "frozen" attribute for schema classes so they can't be modified after
    //       they've been registered.

    class SchemaHash {
        uint8_t hash[16];
    public:
        SchemaHash();
        void encode(qpid::framing::Buffer& buffer) const;
        void decode(qpid::framing::Buffer& buffer);
        void update(const char* data, uint32_t len);
        void update(uint8_t data);
        void update(const std::string& data) { update(data.c_str(), data.size()); }
        void update(Typecode t) { update((uint8_t) t); }
        void update(Direction d) { update((uint8_t) d); }
        void update(Access a) { update((uint8_t) a); }
        void update(bool b) { update((uint8_t) (b ? 1 : 0)); }
        const uint8_t* get() const { return hash; }
        bool operator==(const SchemaHash& other) const;
        bool operator<(const SchemaHash& other) const;
        bool operator>(const SchemaHash& other) const;
    };

    struct SchemaArgumentImpl {
        SchemaArgument* envelope;
        std::string name;
        Typecode typecode;
        Direction dir;
        std::string unit;
        std::string description;

        SchemaArgumentImpl(SchemaArgument* e, const char* n, Typecode t) :
            envelope(e), name(n), typecode(t), dir(DIR_IN) {}
        SchemaArgumentImpl(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void setDirection(Direction d) { dir = d; }
        void setUnit(const char* val) { unit = val; }
        void setDesc(const char* desc) { description = desc; }
        const std::string& getName() const { return name; }
        Typecode getType() const { return typecode; }
        Direction getDirection() const { return dir; }
        const std::string& getUnit() const { return unit; }
        const std::string& getDesc() const { return description; }
        void updateHash(SchemaHash& hash) const;
    };

    struct SchemaMethodImpl {
        SchemaMethod* envelope;
        std::string name;
        std::string description;
        std::vector<SchemaArgumentImpl*> arguments;

        SchemaMethodImpl(SchemaMethod* e, const char* n) : envelope(e), name(n) {}
        SchemaMethodImpl(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void addArgument(const SchemaArgument& argument);
        void setDesc(const char* desc) { description = desc; }
        const std::string& getName() const { return name; }
        const std::string& getDesc() const { return description; }
        int getArgumentCount() const { return arguments.size(); }
        const SchemaArgument* getArgument(int idx) const;
        void updateHash(SchemaHash& hash) const;
    };

    struct SchemaPropertyImpl {
        SchemaProperty* envelope;
        std::string name;
        Typecode typecode;
        Access access;
        bool index;
        bool optional;
        std::string unit;
        std::string description;

        SchemaPropertyImpl(SchemaProperty* e, const char* n, Typecode t) :
            envelope(e), name(n), typecode(t), access(ACCESS_READ_ONLY),
            index(false), optional(false) {}
        SchemaPropertyImpl(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void setAccess(Access a) { access = a; }
        void setIndex(bool val) { index = val; }
        void setOptional(bool val) { optional = val; }
        void setUnit(const char* val) { unit = val; }
        void setDesc(const char* desc) { description = desc; }
        const std::string& getName() const { return name; }
        Typecode getType() const { return typecode; }
        Access getAccess() const { return access; }
        bool isIndex() const { return index; }
        bool isOptional() const { return optional; }
        const std::string& getUnit() const { return unit; }
        const std::string& getDesc() const { return description; }
        void updateHash(SchemaHash& hash) const;
    };

    struct SchemaStatisticImpl {
        SchemaStatistic* envelope;
        std::string name;
        Typecode typecode;
        std::string unit;
        std::string description;

        SchemaStatisticImpl(SchemaStatistic* e, const char* n, Typecode t) :
            envelope(e), name(n), typecode(t) {}
        SchemaStatisticImpl(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void setUnit(const char* val) { unit = val; }
        void setDesc(const char* desc) { description = desc; }
        const std::string& getName() const { return name; }
        Typecode getType() const { return typecode; }
        const std::string& getUnit() const { return unit; }
        const std::string& getDesc() const { return description; }
        void updateHash(SchemaHash& hash) const;
    };

    struct SchemaClassKeyImpl {
        const SchemaClassKey* envelope;
        const std::string& package;
        const std::string& name;
        const SchemaHash& hash;

        SchemaClassKeyImpl(const std::string& package, const std::string& name, const SchemaHash& hash);

        const std::string& getPackageName() const { return package; }
        const std::string& getClassName() const { return name; }
        const uint8_t* getHash() const { return hash.get(); }

        void encode(qpid::framing::Buffer& buffer) const;
        bool operator==(const SchemaClassKeyImpl& other) const;
        bool operator<(const SchemaClassKeyImpl& other) const;
        std::string str() const;
    };

    struct SchemaObjectClassImpl {
        typedef boost::shared_ptr<SchemaObjectClassImpl> Ptr;
        SchemaObjectClass* envelope;
        std::string package;
        std::string name;
        mutable SchemaHash hash;
        mutable bool hasHash;
        SchemaClassKeyImpl classKey;
        std::vector<SchemaPropertyImpl*> properties;
        std::vector<SchemaStatisticImpl*> statistics;
        std::vector<SchemaMethodImpl*> methods;

        SchemaObjectClassImpl(SchemaObjectClass* e, const char* p, const char* n) :
        envelope(e), package(p), name(n), hasHash(false), classKey(package, name, hash) {}
        SchemaObjectClassImpl(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void addProperty(const SchemaProperty& property);
        void addStatistic(const SchemaStatistic& statistic);
        void addMethod(const SchemaMethod& method);

        const SchemaClassKey* getClassKey() const;
        int getPropertyCount() const { return properties.size(); }
        int getStatisticCount() const { return statistics.size(); }
        int getMethodCount() const { return methods.size(); }
        const SchemaProperty* getProperty(int idx) const;
        const SchemaStatistic* getStatistic(int idx) const;
        const SchemaMethod* getMethod(int idx) const;
    };

    struct SchemaEventClassImpl {
        typedef boost::shared_ptr<SchemaEventClassImpl> Ptr;
        SchemaEventClass* envelope;
        std::string package;
        std::string name;
        mutable SchemaHash hash;
        mutable bool hasHash;
        SchemaClassKeyImpl classKey;
        std::string description;
        std::vector<SchemaArgumentImpl*> arguments;

        SchemaEventClassImpl(SchemaEventClass* e, const char* p, const char* n) :
            envelope(e), package(p), name(n), hasHash(false), classKey(package, name, hash) {}
        SchemaEventClassImpl(qpid::framing::Buffer& buffer);
        void encode(qpid::framing::Buffer& buffer) const;
        void addArgument(const SchemaArgument& argument);
        void setDesc(const char* desc) { description = desc; }

        const SchemaClassKey* getClassKey() const;
        int getArgumentCount() const { return arguments.size(); }
        const SchemaArgument* getArgument(int idx) const;
    };
}

#endif

