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
#ifndef _DtxWorkRecord_
#define _DtxWorkRecord_

#include <algorithm>
#include <functional>
#include <vector>
#include "DtxBuffer.h"
#include "TransactionalStore.h"
#include "qpid/framing/amqp_types.h"

namespace qpid {
namespace broker {

/**
 * Represents the work done under a particular distributed transaction
 * across potentially multiple channels. Identified by a xid. Allows
 * that work to be prepared, committed and rolled-back.
 */
class DtxWorkRecord
{
    typedef std::vector<DtxBuffer::shared_ptr> Work;

    const std::string xid;
    TransactionalStore* const store;
    bool completed;
    Work work;
    std::auto_ptr<TPCTransactionContext> txn;

    void checkCompletion();
    void abort();
    bool prepare(TransactionContext* txn);
public:
    DtxWorkRecord(const std::string& xid, TransactionalStore* const store);
    ~DtxWorkRecord();
    bool prepare();
    void commit();
    void rollback();
    void add(DtxBuffer::shared_ptr ops);
};

}
}

#endif
