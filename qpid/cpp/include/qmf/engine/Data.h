#ifndef _QmfEngineData_
#define _QmfEngineData_

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

#include <qpid/messaging/Variant.h>

namespace qmf {
namespace engine {

    class SchemaClass;
    struct DataImpl;
    class Data {
    public:
        Data();
        Data(SchemaClass* type, const qpid::messaging::Variant::Map& values=qpid::messaging::Variant::Map());
        Data(const Data& from);
        virtual ~Data();

        const qpid::messaging::Variant::Map& getValues() const;
        qpid::messaging::Variant::Map& getValues();

        const qpid::messaging::Variant::Map& getSubtypes() const;
        qpid::messaging::Variant::Map& getSubtypes();

        const SchemaClass* getSchema() const;

        const char* getKey() const;
        void setKey(const char* key);

        void modifyStart();
        void modifyDone();
        void destroy();

    private:
        friend struct DataImpl;
        friend class  AgentImpl;
        DataImpl* impl;
    };
}
}

#endif

