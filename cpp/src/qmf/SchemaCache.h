#ifndef QMF_SCHEMA_CACHE_H
#define QMF_SCHEMA_CACHE_H
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

#include "qmf/SchemaIdImpl.h"
#include "qmf/Schema.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Condition.h"
#include "qpid/messaging/Duration.h"
#include <string>
#include <map>
#include <boost/shared_ptr.hpp>

namespace qmf {

    class SchemaCache {
    public:
        SchemaCache() {}
        ~SchemaCache() {}

        bool declareSchemaId(const SchemaId&);
        void declareSchema(const Schema&);
        bool haveSchema(const SchemaId&) const;
        const Schema& getSchema(const SchemaId&, qpid::messaging::Duration) const;

    private:
        mutable qpid::sys::Mutex lock;
        typedef std::map<SchemaId, Schema, SchemaIdCompare> SchemaMap;
        typedef std::map<SchemaId, boost::shared_ptr<qpid::sys::Condition>, SchemaIdCompare> CondMap;
        SchemaMap schemata;
        mutable CondMap conditions;
    };

}

#endif

