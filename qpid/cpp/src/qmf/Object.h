#ifndef _QmfObject_
#define _QmfObject_

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

#include <qmf/Schema.h>
#include <qmf/ObjectId.h>
#include <qmf/Value.h>

namespace qmf {

    struct ObjectImpl;
    class Object {
    public:
        Object(const SchemaObjectClass* type);
        Object(const Object& from);
        virtual ~Object();

        void destroy();
        const ObjectId* getObjectId() const;
        void setObjectId(ObjectId* oid);
        const SchemaObjectClass* getClass() const;
        Value* getValue(const char* key) const;
        void invokeMethod(const char* methodName, const Value* inArgs, void* context) const;
        bool isDeleted() const;
        void merge(const Object& from);

    private:
        friend class ObjectImpl;
        friend class AgentEngineImpl;
        Object(ObjectImpl* impl);
        ObjectImpl* impl;
    };
}

#endif

