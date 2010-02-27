#ifndef _QmfEngineSchemaImpl_
#define _QmfEngineSchemaImpl_

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

#include "qmf/engine/Schema.h"
#include <string.h>
#include <string>
#include <vector>
#include <exception>
#include <memory>

namespace qmf {
namespace engine {

    // TODO: Destructors for schema classes
    // TODO: Add "frozen" attribute for schema classes so they can't be modified after
    //       they've been registered.

    typedef qpid::messaging::VariantType Typecode;

    class SchemaException : public std::exception {
    public:
        SchemaException(const std::string& context, const std::string& expected) {
            text = context + ": Expected item with key " + expected;
        }
        virtual ~SchemaException() throw();
        virtual const char* what() const throw() { return text.c_str(); }

    private:
        std::string text;
    };

    class SchemaHash {
        uint8_t hash[16];
    public:
        SchemaHash();
        void update(const char* data, uint32_t len);
        void update(uint8_t data);
        void update(const std::string& data) { update(data.c_str(), data.size()); }
        void update(Direction d) { update((uint8_t) d); }
        void update(Access a) { update((uint8_t) a); }
        void update(Typecode a) { update((uint8_t) a); }
        void update(bool b) { update((uint8_t) (b ? 1 : 0)); }
        const uint8_t* get() const { return hash; }
        void set(const uint8_t* val) { ::memcpy(hash, val, 16); }
        bool operator==(const SchemaHash& other) const;
        bool operator<(const SchemaHash& other) const;
        bool operator>(const SchemaHash& other) const;
    };

    struct SchemaPropertyImpl {
        std::string name;
        Typecode typecode;
        Access access;
        bool index;
        bool optional;
        Direction dir;
        std::string unit;
        std::string description;

        SchemaPropertyImpl(const char* n, Typecode t) :
            name(n), typecode(t), access(ACCESS_READ_ONLY), index(false), optional(false), dir(DIR_IN) {}
        SchemaPropertyImpl(const std::string& name, const qpid::messaging::Variant::Map& map);
        static SchemaProperty* factory(const std::string& name, const qpid::messaging::Variant::Map& map);
        qpid::messaging::Variant::Map asMap() const;
        void setAccess(Access a) { access = a; }
        void setIndex(bool val) { index = val; }
        void setOptional(bool val) { optional = val; }
        void setDirection(Direction d) { dir = d; }
        void setUnit(const char* val) { unit = val; }
        void setDesc(const char* desc) { description = desc; }
        const std::string& getName() const { return name; }
        Typecode getType() const { return typecode; }
        Access getAccess() const { return access; }
        bool isIndex() const { return index; }
        bool isOptional() const { return optional; }
        Direction getDirection() const { return dir; }
        const std::string& getUnit() const { return unit; }
        const std::string& getDesc() const { return description; }
        void updateHash(SchemaHash& hash) const;
    };

    struct SchemaMethodImpl {
        std::string name;
        std::string description;
        std::vector<const SchemaProperty*> properties;

        SchemaMethodImpl(const char* n) : name(n) {}
        SchemaMethodImpl(const std::string& name, const qpid::messaging::Variant::Map& map);
        static SchemaMethod* factory(const std::string& name, const qpid::messaging::Variant::Map& map);
        qpid::messaging::Variant::Map asMap() const;
        void addProperty(const SchemaProperty* property);
        void setDesc(const char* desc) { description = desc; }
        const std::string& getName() const { return name; }
        const std::string& getDesc() const { return description; }
        int getPropertyCount() const { return properties.size(); }
        const SchemaProperty* getProperty(int idx) const;
        void updateHash(SchemaHash& hash) const;
    };

    struct SchemaClassKeyImpl {
        ClassKind kind;
        std::string package;
        std::string name;
        SchemaHash hash;
        mutable std::string repr;

        SchemaClassKeyImpl(ClassKind kind, const std::string& package, const std::string& name);
        SchemaClassKeyImpl(const qpid::messaging::Variant::Map& map);
        static SchemaClassKey* factory(ClassKind kind, const std::string& package, const std::string& name);
        static SchemaClassKey* factory(const qpid::messaging::Variant::Map& map);

        ClassKind getKind() const { return kind; }
        const std::string& getPackageName() const { return package; }
        const std::string& getClassName() const { return name; }
        const uint8_t* getHashData() const { return hash.get(); }
        SchemaHash& getHash() { return hash; }

        qpid::messaging::Variant::Map asMap() const;
        bool operator==(const SchemaClassKeyImpl& other) const;
        bool operator<(const SchemaClassKeyImpl& other) const;
        const std::string& str() const;
    };

    struct SchemaClassImpl {
        mutable bool hasHash;
        std::auto_ptr<SchemaClassKey> classKey;
        std::vector<const SchemaProperty*> properties;
        std::vector<const SchemaMethod*> methods;

        SchemaClassImpl(ClassKind kind, const char* package, const char* name) :
            hasHash(false), classKey(SchemaClassKeyImpl::factory(kind, package, name)) {}
        SchemaClassImpl(const qpid::messaging::Variant::Map& map);
        static SchemaClass* factory(const qpid::messaging::Variant::Map& map);

        qpid::messaging::Variant::Map asMap() const;
        void addProperty(const SchemaProperty* property);
        void addMethod(const SchemaMethod* method);

        const SchemaClassKey* getClassKey() const;
        int getPropertyCount() const { return properties.size(); }
        int getMethodCount() const { return methods.size(); }
        const SchemaProperty* getProperty(int idx) const;
        const SchemaMethod* getMethod(int idx) const;
    };
}
}

#endif

