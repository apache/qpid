#ifndef _QmfSchema_
#define _QmfSchema_

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

#include <qmf/Typecode.h>
#include <qpid/sys/IntegerTypes.h>

namespace qmf {

    enum Access { ACCESS_READ_CREATE = 1, ACCESS_READ_WRITE = 2, ACCESS_READ_ONLY = 3 };
    enum Direction { DIR_IN = 1, DIR_OUT = 2, DIR_IN_OUT = 3 };
    enum ClassKind { CLASS_OBJECT = 1, CLASS_EVENT = 2 };

    struct SchemaArgumentImpl;
    struct SchemaMethodImpl;
    struct SchemaPropertyImpl;
    struct SchemaStatisticImpl;
    struct SchemaObjectClassImpl;
    struct SchemaEventClassImpl;
    struct SchemaClassKeyImpl;

    /**
     */
    class SchemaArgument {
    public:
        SchemaArgument(const char* name, Typecode typecode);
        SchemaArgument(const SchemaArgument& from);
        ~SchemaArgument();
        void setDirection(Direction dir);
        void setUnit(const char* val);
        void setDesc(const char* desc);
        const char* getName() const;
        Typecode getType() const;
        Direction getDirection() const;
        const char* getUnit() const;
        const char* getDesc() const;

    private:
        friend class SchemaArgumentImpl;
        friend class SchemaMethodImpl;
        friend class SchemaEventClassImpl;
        SchemaArgument(SchemaArgumentImpl* impl);
        SchemaArgumentImpl* impl;
    };

    /**
     */
    class SchemaMethod {
    public:
        SchemaMethod(const char* name);
        SchemaMethod(const SchemaMethod& from);
        ~SchemaMethod();
        void addArgument(const SchemaArgument* argument);
        void setDesc(const char* desc);
        const char* getName() const;
        const char* getDesc() const;
        int getArgumentCount() const;
        const SchemaArgument* getArgument(int idx) const;

    private:
        friend class SchemaMethodImpl;
        friend class SchemaObjectClassImpl;
        friend class AgentEngineImpl;
        SchemaMethod(SchemaMethodImpl* impl);
        SchemaMethodImpl* impl;
    };

    /**
     */
    class SchemaProperty {
    public:
        SchemaProperty(const char* name, Typecode typecode);
        SchemaProperty(const SchemaProperty& from);
        ~SchemaProperty();
        void setAccess(Access access);
        void setIndex(bool val);
        void setOptional(bool val);
        void setUnit(const char* val);
        void setDesc(const char* desc);
        const char* getName() const;
        Typecode getType() const;
        Access getAccess() const;
        bool isIndex() const;
        bool isOptional() const;
        const char* getUnit() const;
        const char* getDesc() const;

    private:
        friend class SchemaPropertyImpl;
        friend class SchemaObjectClassImpl;
        SchemaProperty(SchemaPropertyImpl* impl);
        SchemaPropertyImpl* impl;
    };

    /**
     */
    class SchemaStatistic {
    public:
        SchemaStatistic(const char* name, Typecode typecode);
        SchemaStatistic(const SchemaStatistic& from);
        ~SchemaStatistic();
        void setUnit(const char* val);
        void setDesc(const char* desc);
        const char* getName() const;
        Typecode getType() const;
        const char* getUnit() const;
        const char* getDesc() const;

    private:
        friend class SchemaStatisticImpl;
        friend class SchemaObjectClassImpl;
        SchemaStatistic(SchemaStatisticImpl* impl);
        SchemaStatisticImpl* impl;
    };

    /**
     */
    class SchemaClassKey {
    public:
        SchemaClassKey(const SchemaClassKey& from);
        ~SchemaClassKey();

        const char* getPackageName() const;
        const char* getClassName() const;
        const uint8_t* getHash() const;

        bool operator==(const SchemaClassKey& other) const;
        bool operator<(const SchemaClassKey& other) const;

    private:
        friend class SchemaClassKeyImpl;
        friend class BrokerProxyImpl;
        friend class ConsoleEngineImpl;
        SchemaClassKey(SchemaClassKeyImpl* impl);
        SchemaClassKeyImpl* impl;
    };

    /**
     */
    class SchemaObjectClass {
    public:
        SchemaObjectClass(const char* package, const char* name);
        SchemaObjectClass(const SchemaObjectClass& from);
        ~SchemaObjectClass();
        void addProperty(const SchemaProperty* property);
        void addStatistic(const SchemaStatistic* statistic);
        void addMethod(const SchemaMethod* method);

        const SchemaClassKey* getClassKey() const;
        int getPropertyCount() const;
        int getStatisticCount() const;
        int getMethodCount() const;
        const SchemaProperty* getProperty(int idx) const;
        const SchemaStatistic* getStatistic(int idx) const;
        const SchemaMethod* getMethod(int idx) const;

    private:
        friend class SchemaObjectClassImpl;
        friend class BrokerProxyImpl;
        friend class AgentEngineImpl;
        SchemaObjectClass(SchemaObjectClassImpl* impl);
        SchemaObjectClassImpl* impl;
    };

    /**
     */
    class SchemaEventClass {
    public:
        SchemaEventClass(const char* package, const char* name);
        SchemaEventClass(const SchemaEventClass& from);
        ~SchemaEventClass();
        void addArgument(const SchemaArgument* argument);
        void setDesc(const char* desc);

        const SchemaClassKey* getClassKey() const;
        int getArgumentCount() const;
        const SchemaArgument* getArgument(int idx) const;

    private:
        friend class SchemaEventClassImpl;
        friend class BrokerProxyImpl;
        friend class AgentEngineImpl;
        SchemaEventClass(SchemaEventClassImpl* impl);
        SchemaEventClassImpl* impl;
    };
}

#endif

