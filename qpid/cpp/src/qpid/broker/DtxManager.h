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
#ifndef _DtxManager_
#define _DtxManager_

#include <boost/ptr_container/ptr_map.hpp>
#include "DtxBuffer.h"
#include "DtxWorkRecord.h"
#include "TransactionalStore.h"
#include "qpid/framing/amqp_types.h"

namespace qpid {
namespace broker {

class DtxManager{
    typedef boost::ptr_map<std::string, DtxWorkRecord> WorkMap;

    WorkMap work;
    TransactionalStore* const store;

    WorkMap::iterator getWork(const std::string& xid);

public:
    DtxManager(TransactionalStore* const store);
    ~DtxManager();
    void start(std::string xid, DtxBuffer::shared_ptr work);
    void prepare(const std::string& xid);
    void commit(const std::string& xid);
    void rollback(const std::string& xid);
};

}
}

#endif
