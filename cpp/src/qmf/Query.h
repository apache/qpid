#ifndef _QmfQuery_
#define _QmfQuery_

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

#include <qmf/ObjectId.h>
#include <qmf/Value.h>

namespace qmf {

    struct QueryImpl;
    class Query {
    public:
        Query();
        Query(QueryImpl* impl);
        ~Query();

        const char* getPackage() const;
        const char* getClass() const;
        const ObjectId* getObjectId() const;

        enum Oper {
            OPER_AND = 1,
            OPER_OR  = 2
        };

        int whereCount() const;
        Oper whereOper() const;
        const char* whereKey() const;
        const Value* whereValue() const;

        QueryImpl* impl;
    };
}

#endif

