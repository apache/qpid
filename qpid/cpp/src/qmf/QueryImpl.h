#ifndef _QmfQueryImpl_
#define _QmfQueryImpl_

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

#include <qmf/Query.h>
#include <string>
#include <boost/shared_ptr.hpp>

namespace qmf {

    struct QueryImpl {
        Query* envelope;
        std::string packageName;
        std::string className;
        boost::shared_ptr<ObjectId> oid;

        QueryImpl(Query* e) : envelope(e) {}

        const char* getPackage() const { return packageName.empty() ? 0 : packageName.c_str(); }
        const char* getClass() const { return className.empty() ? 0 : className.c_str(); }
        const ObjectId* getObjectId() const { return oid.get(); }

        int whereCount() const { return 0;}
        Query::Oper whereOper() const { return Query::OPER_AND; }
        const char* whereKey() const { return 0; }
        const Value* whereValue() const { return 0; }
    };
}

#endif
