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

#include "qpid/broker/DtxBuffer.h"
#include "qpid/broker/DtxWorkRecord.h"
#include "qpid/broker/TransactionalStore.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/Xid.h"
#include "qpid/sys/Mutex.h"
#include "qpid/ptr_map.h"

namespace qpid {
namespace sys {
class Timer;
}

namespace broker {

class DtxManager{
    typedef boost::ptr_map<std::string, DtxWorkRecord> WorkMap;

    WorkMap work;
    TransactionalStore* store;
    qpid::sys::Mutex lock;
    qpid::sys::Timer* timer;
    uint32_t dtxDefaultTimeout;

    void remove(const std::string& xid);
    DtxWorkRecord* createWork(const std::string& xid);

public:
    DtxManager(sys::Timer&, uint32_t _dtxDefaultTimeout=0);
    ~DtxManager();
    void start(const std::string& xid, boost::intrusive_ptr<DtxBuffer> work);
    void join(const std::string& xid, boost::intrusive_ptr<DtxBuffer> work);
    void recover(const std::string& xid, std::auto_ptr<TPCTransactionContext> txn, boost::intrusive_ptr<DtxBuffer> work);
    bool prepare(const std::string& xid);
    bool commit(const std::string& xid, bool onePhase);
    void rollback(const std::string& xid);
    void setTimeout(const std::string& xid, uint32_t secs);
    uint32_t getTimeout(const std::string& xid);
    void timedout(const std::string& xid);
    void setStore(TransactionalStore* store);
    void setTimer(sys::Timer& t) { timer = &t; }

    DtxWorkRecord* getWork(const std::string& xid);
    bool exists(const std::string& xid);
    static std::string convert(const framing::Xid& xid);
    static framing::Xid convert(const std::string& xid);
};

}
}

#endif
