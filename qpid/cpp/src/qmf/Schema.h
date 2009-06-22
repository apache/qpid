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

    /**
     */
    class SchemaArgument {
    public:
        SchemaArgument(const char* name, Typecode typecode);
        SchemaArgument(SchemaArgumentImpl* impl);
        ~SchemaArgument();
        void setDirection(Direction dir);
        void setUnit(const char* val);
        void setDesc(const char* desc);
        const char* getName() const;
        Typecode getType() const;
        Direction getDirection() const;
        const char* getUnit() const;
        const char* getDesc() const;

        SchemaArgumentImpl* impl;
    };

    /**
     */
    class SchemaMethod {
    public:
        SchemaMethod(const char* name);
        SchemaMethod(SchemaMethodImpl* impl);
        ~SchemaMethod();
        void addArgument(const SchemaArgument& argument);
        void setDesc(const char* desc);
        const char* getName() const;
        const char* getDesc() const;
        int getArgumentCount() const;
        const SchemaArgument* getArgument(int idx) const;

        SchemaMethodImpl* impl;
    };

    /**
     */
    class SchemaProperty {
    public:
        SchemaProperty(const char* name, Typecode typecode);
        SchemaProperty(SchemaPropertyImpl* impl);
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

        SchemaPropertyImpl* impl;
    };

    /**
     */
    class SchemaStatistic {
    public:
        SchemaStatistic(const char* name, Typecode typecode);
        SchemaStatistic(SchemaStatisticImpl* impl);
        ~SchemaStatistic();
        void setUnit(const char* val);
        void setDesc(const char* desc);
        const char* getName() const;
        Typecode getType() const;
        const char* getUnit() const;
        const char* getDesc() const;

        SchemaStatisticImpl* impl;
    };

    /**
     */
    class SchemaObjectClass {
    public:
        SchemaObjectClass(const char* package, const char* name);
        SchemaObjectClass(SchemaObjectClassImpl* impl);
        ~SchemaObjectClass();
        void addProperty(const SchemaProperty& property);
        void addStatistic(const SchemaStatistic& statistic);
        void addMethod(const SchemaMethod& method);

        const char* getPackage() const;
        const char* getName() const;
        const uint8_t* getHash() const;
        int getPropertyCount() const;
        int getStatisticCount() const;
        int getMethodCount() const;
        const SchemaProperty* getProperty(int idx) const;
        const SchemaStatistic* getStatistic(int idx) const;
        const SchemaMethod* getMethod(int idx) const;

        SchemaObjectClassImpl* impl;
    };

    /**
     */
    class SchemaEventClass {
    public:
        SchemaEventClass(const char* package, const char* name);
        SchemaEventClass(SchemaEventClassImpl* impl);
        ~SchemaEventClass();
        void addArgument(const SchemaArgument& argument);
        void setDesc(const char* desc);

        const char* getPackage() const;
        const char* getName() const;
        const uint8_t* getHash() const;
        int getArgumentCount() const;
        const SchemaArgument* getArgument(int idx) const;

        SchemaEventClassImpl* impl;
    };
}

#endif

