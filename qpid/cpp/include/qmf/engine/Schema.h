#ifndef _QmfEngineSchema_
#define _QmfEngineSchema_

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

#include <qpid/sys/IntegerTypes.h>
#include <qpid/messaging/Variant.h>

namespace qmf {
namespace engine {

    enum Access { ACCESS_READ_CREATE = 1, ACCESS_READ_WRITE = 2, ACCESS_READ_ONLY = 3 };
    enum Direction { DIR_IN = 1, DIR_OUT = 2, DIR_IN_OUT = 3 };
    enum ClassKind { CLASS_DATA = 1, CLASS_EVENT = 2 };

    struct SchemaMethodImpl;
    struct SchemaPropertyImpl;
    struct SchemaClassImpl;
    struct SchemaClassKeyImpl;

    /**
     */
    class SchemaProperty {
    public:
        SchemaProperty(const char* name, qpid::messaging::VariantType typecode);
        SchemaProperty(const SchemaProperty& from);
        ~SchemaProperty();
        void setAccess(Access access);
        void setIndex(bool val);
        void setOptional(bool val);
        void setDirection(Direction dir);
        void setUnit(const char* val);
        void setDesc(const char* desc);
        const char* getName() const;
        qpid::messaging::VariantType getType() const;
        Access getAccess() const;
        bool isIndex() const;
        bool isOptional() const;
        Direction getDirection() const;
        const char* getUnit() const;
        const char* getDesc() const;

    private:
        friend struct SchemaPropertyImpl;
        friend struct SchemaClassImpl;
        friend struct SchemaMethodImpl;
        SchemaProperty(SchemaPropertyImpl* impl);
        SchemaPropertyImpl* impl;
    };

    /**
     */
    class SchemaMethod {
    public:
        SchemaMethod(const char* name);
        SchemaMethod(const SchemaMethod& from);
        ~SchemaMethod();
        void addProperty(const SchemaProperty* property);
        void setDesc(const char* desc);
        const char* getName() const;
        const char* getDesc() const;
        int getPropertyCount() const;
        const SchemaProperty* getProperty(int idx) const;

    private:
        friend struct SchemaMethodImpl;
        friend struct SchemaClassImpl;
        friend class  AgentImpl;
        SchemaMethod(SchemaMethodImpl* impl);
        SchemaMethodImpl* impl;
    };

    /**
     */
    class SchemaClassKey {
    public:
        SchemaClassKey(const SchemaClassKey& from);
        ~SchemaClassKey();

        ClassKind getKind() const;
        const char* getPackageName() const;
        const char* getClassName() const;
        const uint8_t* getHashData() const;
        const char* asString() const;

        bool operator==(const SchemaClassKey& other) const;
        bool operator<(const SchemaClassKey& other) const;

    private:
        friend struct SchemaClassKeyImpl;
        friend struct SchemaClassImpl;
        friend class BrokerProxyImpl;
        friend class ConsoleImpl;
        SchemaClassKey(SchemaClassKeyImpl* impl);
        SchemaClassKeyImpl* impl;
    };

    /**
     */
    class SchemaClass {
    public:
        SchemaClass(ClassKind kind, const char* package, const char* name);
        SchemaClass(const SchemaClass& from);
        ~SchemaClass();
        void addProperty(const SchemaProperty* property);
        void addMethod(const SchemaMethod* method);

        ClassKind getKind() const;
        const SchemaClassKey* getClassKey() const;
        int getPropertyCount() const;
        int getMethodCount() const;
        const SchemaProperty* getProperty(int idx) const;
        const SchemaMethod* getMethod(int idx) const;

    private:
        friend struct SchemaClassImpl;
        friend class  BrokerProxyImpl;
        friend class  AgentImpl;
        SchemaClass(SchemaClassImpl* impl);
        SchemaClassImpl* impl;
    };
}
}

#endif

